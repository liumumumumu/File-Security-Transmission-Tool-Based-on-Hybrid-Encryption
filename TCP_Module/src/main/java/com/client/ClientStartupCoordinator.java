package com.client;

import com.common.config.ClientProperties;
import com.common.config.LocalStorageProperties;
import com.common.config.NodeProperties;
import com.crypto.CryptoSupport;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Author: LQH
 * Date: 2026-05-15
 * Purpose: 客户端启动阶段的协调器，负责
 * 1. 判断启动时有没有本地私钥
 * 2. 判断是否要提示用户生成/ 导入密钥
 * 3. 在条件满足后恢复自动连接
 *
 **/

@Service
@Slf4j
public class ClientStartupCoordinator
{
    private static final String BLOCK_REASON_KEY_MISSING = "KEY_MISSING";//自动连接被停止的原因

    private final ClientProperties clientProperties;//读取客户端连接配置，服务器地址，端口，认证超时时间
    private final NodeProperties nodeProperties;//读取节点配置
    private final CryptoSupport cryptoSupport;//访问加密服务，负责检查密钥，生成密钥，导入密钥等
    private final ClientConnectionManager clientConnectionManager;//负责连接服务器和认证
    private final Path startupStatePath;//启动状态文件路径；用来判断启动的时候是否提示用户设置密钥
    private final Gson gson = new Gson();//把启动状态写成JSON，并从JSON读回来

    private boolean initialized;//标识启动状态是否已经刷新过
    private boolean autoConnectBlocked;//标识自动连接是否被暂停
    private String autoConnectBlockReason = "";//自动连接被停止的原因
    private boolean lastKeyMissing = true;//标识最近一次检查得到的密钥状态
    private Map<String, Object> lastKeyStatus = Map.of();//缓存cryptoSupport返回的完整密钥状态

    public ClientStartupCoordinator(
            ClientProperties clientProperties,
            NodeProperties nodeProperties,
            CryptoSupport cryptoSupport,
            ClientConnectionManager clientConnectionManager,
            LocalStorageProperties localStorageProperties
    )
    {
        this.clientProperties = clientProperties;
        this.nodeProperties = nodeProperties;
        this.cryptoSupport = cryptoSupport;
        this.clientConnectionManager = clientConnectionManager;
        this.startupStatePath = Path.of(localStorageProperties.getStartupStatePath()).toAbsolutePath();
    }

    //应用启动完成后调用的入口；1.刷新启动状态；2.判断现在能不能自动连接；3.如果满足条件，则执行自动连接；4.自动连接后再刷新一次状态；5.返回最终启动状态
    public synchronized Map<String, Object> handleApplicationReady()
    {
        Map<String, Object> status = refreshStartupStatus();//先刷新initialized, autoConnectBlocked, autoConnectBlockReason这些变量
        if (shouldAutoConnectNow()) {
            continueAutoConnect();
            status = refreshStartupStatus();
        }
        return status;
    }

    //查询启动状态(只重新检查密钥状态，自动连接阻塞状态，并返回一个Map)
    public synchronized Map<String, Object> startupStatus()
    {
        return refreshStartupStatus();
    }

    //用于启动阶段生成密钥，并尝试继续自动连接
    public synchronized Map<String, Object> generateStartupKeyAndContinue()
    {
        Map<String, String> result = cryptoSupport.generateKeyPair();//生成密钥
        markKeySetupPrompted(true);//表示已经处理过启动时密钥自动生成提示
        continueAutoConnectIfBlockedByMissingKey();//如果之前的自动连接因为密钥被阻塞，现在则尝试恢复
        return Map.of(
                "success", true,
                "keyResult", result,
                "startupStatus", refreshStartupStatus()
        );//返回结果
    }

    //处理用户跳过启动密钥设置
    public synchronized Map<String, Object> skipStartupKeySetup()
    {
        markKeySetupPrompted(true);//表示是否自动生成密钥提示已经问过用户了，不再重复提示
        return refreshStartupStatus();
    }

    //处理用户通过其他入口导入或生成密钥
    public synchronized void markKeyAvailableAndContinueAutoConnect()
    {
        markKeySetupPrompted(true);//标识密钥提醒已经处理过了
        continueAutoConnectIfBlockedByMissingKey();//如果之前的自动连接因为密钥被阻塞，现在则尝试恢复自动连接
        refreshStartupStatus();//刷新状态
    }

    //检查是否缺失密钥
    public boolean isKeyMissing(Map<String, ?> keyStatus)
    {
        return !isTruthy(keyStatus.get("hasPrivateKey"));
    }

    //控制台启动后通过这个函数，判断要不要询问用户生成密钥
    public synchronized boolean shouldPromptForStartupKeySetup()
    {
        Map<String, Object> status = refreshStartupStatus();
        return Boolean.TRUE.equals(status.get("shouldPromptKeySetup"));
    }

    //刷新启动状态
    private Map<String, Object> refreshStartupStatus()
    {
        StartupState state = readStartupState();//读取本地启动状态
        try {
            lastKeyStatus = new LinkedHashMap<>(cryptoSupport.keyStatus());//查询密钥状态
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to check key status", ex);
        }
        lastKeyMissing = isKeyMissing(lastKeyStatus);//判断是否缺失密钥

        //如果有密钥但是本地状态还记录’已提示过‘，就自动补上
        if (!lastKeyMissing && !state.keySetupPrompted) {
            markKeySetupPrompted(true);
            state.keySetupPrompted = true;
        }

        //处理自动连接阻塞
        if (lastKeyMissing && nodeProperties.isAutoConnect()) {
            autoConnectBlocked = true;
            autoConnectBlockReason = BLOCK_REASON_KEY_MISSING;
        } else if (!lastKeyMissing && BLOCK_REASON_KEY_MISSING.equals(autoConnectBlockReason)) {
            autoConnectBlocked = false;
            autoConnectBlockReason = "";
        }

        initialized = true;//表示启动状态已初始化
        return buildStartupStatus(state);
    }

    //判断是否应该继续连接
    private boolean shouldAutoConnectNow()
    {
        return initialized  //完成初始化
                && nodeProperties.isAutoConnect()//设置里面开启自动连接
                && !lastKeyMissing//不缺密钥
                && !clientConnectionManager.isAuthenticated();//当前还没有完成认证
    }

    //处理之前因为缺密钥暂停了自动连接，现在用户生成或者导入密钥，所以继续自动连接
    private void continueAutoConnectIfBlockedByMissingKey()
    {
        boolean wasBlockedByMissingKey = autoConnectBlocked && BLOCK_REASON_KEY_MISSING.equals(autoConnectBlockReason);
        refreshStartupStatus();
        if (wasBlockedByMissingKey && !lastKeyMissing) {
            continueAutoConnect();
        }
    }

    //真正发起自动连接的方法
    private void continueAutoConnect()
    {
        if (!nodeProperties.isAutoConnect()) {  //没有开启自动连接，就直接返回
            return;
        }
        try {
            autoConnectBlocked = false; //清除自动连接的阻塞状态
            autoConnectBlockReason = "";

            //调用连接管理器；1.连接服务器；2.完成认证
            clientConnectionManager.connectAndAuthenticate(clientProperties.getServerHost(), clientProperties.getServerPort())
                    .get(clientProperties.getAuthTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (Exception ex) {
            log.warn("Client auto-connect failed. Console remains available; use 'connect <host> <port>' to retry.", ex);
            System.out.println("Auto-connect failed: " + ex.getMessage());
            System.out.println("Console is still available. Try: connect <host> <port>");
        }
    }

    //将当前状态组装成一个Map然后返回给控制台
    private Map<String, Object> buildStartupStatus(StartupState state)
    {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("keyMissing", lastKeyMissing);
        payload.put("keySetupPrompted", state.keySetupPrompted);
        payload.put("shouldPromptKeySetup", lastKeyMissing && !state.keySetupPrompted);
        payload.put("autoConnectConfigured", nodeProperties.isAutoConnect());
        payload.put("autoConnectBlocked", autoConnectBlocked);
        payload.put("autoConnectBlockReason", autoConnectBlockReason);
        payload.put("recommendedAction", recommendedAction(state));
        payload.put("keyStatus", lastKeyStatus);
        payload.put("startupStatePath", startupStatePath.toString());
        return payload;
    }

    private String recommendedAction(StartupState state)
    {
        if (!lastKeyMissing) {
            return "NONE";
        }
        if (!state.keySetupPrompted) {
            return "GENERATE_OR_IMPORT_KEY";
        }
        return "MANUALLY_GENERATE_OR_IMPORT_KEY";
    }

    //从startupStatePath读取JSON
    private StartupState readStartupState()
    {
        if (Files.notExists(startupStatePath)) {
            return new StartupState();
        }
        try {
            String json = Files.readString(startupStatePath);
            StartupState state = gson.fromJson(json, StartupState.class);
            return state == null ? new StartupState() : state;
        } catch (IOException | JsonSyntaxException ex) {
            log.warn("Failed to read startup state from {}", startupStatePath, ex);
            return new StartupState();
        }
    }

    //修改本地启动状态里的keySetupPrompted
    private void markKeySetupPrompted(boolean value)
    {
        StartupState state = readStartupState();
        state.keySetupPrompted = value;
        writeStartupState(state);
    }

    //写入启动状态文件
    private void writeStartupState(StartupState state)
    {
        try {
            Path parent = startupStatePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(startupStatePath, gson.toJson(state));//写入JSON文件
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to write startup state: " + startupStatePath, ex);
        }
    }

    private boolean isTruthy(Object value)
    {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return "true".equalsIgnoreCase(String.valueOf(value));
    }

    //要写到JSON里的状态对象
    private static class StartupState
    {
        private boolean keySetupPrompted;
    }
}
