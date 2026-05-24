package com.client.direct;

import com.common.config.DirectProperties;
import com.common.config.LocalStorageProperties;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Author: LQH
 * Date: 2026-05-22
 * Purpose: 负责读取配置，修改配置，保存配置的功能
 * 管理IPv6直连模块的端口设置
 * 1.从本地设置文件读取当前的直连配置
 * 2.设置为随机端口模式
 * 3.设置为固定端口模式，并保存端口号
 * 4.当设置文件不存在，损坏，字段缺少或端口非法时，就使用默认的端口
 *
 * */

@Service
public class DirectSettingsService
{
    private final Path settingsPath;//配置文件路径
    private final int defaultFixedListenPort;//固定监听端口的默认值
    private final Gson gson = new Gson();//用于将DirectSettings对象和JSON文件互相转换

    public DirectSettingsService(LocalStorageProperties localStorageProperties, DirectProperties directProperties)
    {
        this.settingsPath = Path.of(localStorageProperties.getDirectSettingsPath()).toAbsolutePath();
        this.defaultFixedListenPort = directProperties.getDefaultFixedListenPort();
    }

    //获取当前直连设置
    public synchronized DirectSettings current()
    {
        if(Files.notExists(settingsPath))
        {
            //文件不存在就返回默认值
            return defaultSettings();
        }
        try //文件存在就读取
        {
            DirectSettings settings = gson.fromJson(Files.readString(settingsPath), DirectSettings.class);
            return settings == null ? defaultSettings() : normalize(settings);//解析结果是NULL，返回默认设置
        }
        catch(IOException | JsonSyntaxException ex)
        {
            return defaultSettings();//读取失败也返回默认设置
        }
    }

    //将监听端口模式改成随机端口
    public synchronized DirectSettings useRandomPort()
    {
        DirectSettings settings = current();
        settings.setListenPortMode(DirectListenPortMode.RANDOM);//将端口监听模式设置为随机
        write(settings);//保存改动
        return settings;
    }

    //将监听端口模式改成固定端口
    public synchronized DirectSettings useFixedPort(int port)
    {
        if(port < 1 || port > 65535)//检查端口范围，TCP/UDP 端口合法范围是 1 到 65535。
        {
            throw new IllegalArgumentException("Port must be between 1 and 65535");
        }
        DirectSettings settings = current();//读取设置
        settings.setListenPortMode(DirectListenPortMode.FIXED);//将端口监听模式设置为固定
        settings.setFixedListenPort(port);//填入自定义的固定端口号
        write(settings);//保存设置
        return settings;
    }

    //返回设置文件的路径
    public Path settingsPath()
    {
        return settingsPath;
    }

    //写入本地JSON文件的工具函数
    private void write(DirectSettings settings)
    {
        try
        {
            Path parent = settingsPath.getParent();
            if(parent != null)
            {
                //确保父目录存在
                Files.createDirectories(parent);
            }
            Files.writeString(settingsPath, gson.toJson(settings));//将DirectSettings转成JSON并写入文件
        }
        catch(IOException ex)
        {
            throw new IllegalStateException("Unable to write direct settings: "+settingsPath, ex);
        }
    }

    //创建默认设置对象
    private DirectSettings defaultSettings()
    {
        DirectSettings settings = new DirectSettings();
        settings.setFixedListenPort(validDefaultFixedListenPort());
        return settings;
    }

    //用于修正从JSON文件读出来的不合法设置
    private DirectSettings normalize(DirectSettings settings)
    {
        if(settings.getFixedListenPort() < 1 || settings.getFixedListenPort() > 65535)
        {
            settings.setFixedListenPort(validDefaultFixedListenPort());
        }
        return settings;
    }

    //检查配置文件的默认端口是否合法
    private int validDefaultFixedListenPort()
    {
        if(defaultFixedListenPort < 1 || defaultFixedListenPort > 65535)
        {
            throw new IllegalStateException("direct.default-fixed-listen-port must be between 1 and 65535");
        }
        return defaultFixedListenPort;
    }
}
