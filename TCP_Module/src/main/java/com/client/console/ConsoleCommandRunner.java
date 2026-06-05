package com.client.console;

import com.client.ApplicationShutdownService;
import com.client.ClientConnectionManager;
import com.client.ClientStartupCoordinator;
import com.client.direct.DirectHandshakeService;
import com.client.direct.DirectPeerConnectionManager;
import com.client.direct.DirectSettings;
import com.client.direct.DirectSettingsService;
import com.client.direct.DirectSessionInfo;
import com.client.direct.ReceiverListeningSession;
import com.client.direct.qr.QrArtifact;
import com.client.language.ConsoleMessages;
import com.client.language.LanguageSettingsService;
import com.client.language.UiLanguage;
import com.client.message.ClientMessageService;
import com.client.message.ConversationSummary;
import com.client.message.TextMessageRecord;
import com.common.config.ClientProperties;
import com.common.protocol.file.IncomingTransferRequestPacket;
import com.common.protocol.searchUser.OnlineUserSearchResultPacket;
import com.crypto.CryptoSupport;
import com.client.service.ClientTransferService;
import com.client.service.LocalContactBookService;
import com.common.service.PushNotificationService;
import com.common.util.PathInputNormalizer;
import com.client.service.TransferTaskRegistry;
import com.client.service.PrivateKeyArtifactService;
import com.client.service.OfflineCryptoService;
import com.client.service.PublicKeyPayloadService;
import com.persistence.local.model.contactsRecord.BlacklistRecord;
import com.persistence.local.model.contactsRecord.ContactRecord;
import com.session.TransferDirection;
import com.session.TransferStatus;
import com.session.TransferTask;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Author: LQH
 * Date: 2026-05-04
 * Purpose: 在客户端程序启动后开启一个命令行交互控制台
 * 实现的指令：
 * 1. help：打印控制台帮助信息
 * 2. status：查看当前客户端连接状态
 * 3. connect / disconnect：连接服务器并认证，或断开连接
 * 4. send：向目标 accountId 发送文件
 * 5. incoming / accept / reject：查看、接受或拒绝待处理的接收请求
 * 6. cancel：取消正在进行或待接受的传输任务
 * 7. retransmit / retransmit-accept / retransmit-reject：请求断点重传，以及发送方接受或拒绝重传
 * 8. contacts / contact-add / contact-remove / contact-show：管理本地联系人
 * 9. blacklist / blacklist-add / blacklist-add-contact / blacklist-remove：管理本地黑名单
 * 10. search-user / search-user-add：搜索在线账号，并可加入联系人
 * 11. tasks / task：查看传输任务列表或动态查看单个任务进度
 * 12. public-key / public-key-fingerprint / account-id：查看本地公钥或计算公钥指纹(accountId)
 * 13. key-info / generate-key / delete-key：查看、生成或删除本地密钥
 * 14. import-private-key / import-private-key-file / import-private-key-paste：导入私钥
 * 15. language：切换客户端控制台语言
 * 16. exit / quit：退出客户端程序
 *
 * */

@Component
public class ConsoleCommandRunner
{
    private static final int PROGRESS_BAR_WIDTH = 30;
    private static final int WINDOWS_DEFAULT_TERMINAL_COLUMNS = 80;
    private static final int DEFAULT_TERMINAL_COLUMNS = 120;
    private static final long TASK_WATCH_INTERVAL_MILLIS = 1000L;

    private final ClientConnectionManager clientConnectionManager;//负责客户端连接服务器，认证，断开连接
    private final ClientStartupCoordinator clientStartupCoordinator;//协调启动期密钥检查、首次询问和自动连接恢复
    private final ClientTransferService clientTransferService;//负责收发文件，处理传输请求
    private final ClientProperties clientProperties;//负责读取客户端配置
    private final CryptoSupport cryptoSupport;//负责访问本地的加密服务，管理密钥，获取密钥状态，生成密钥，导入密钥
    private final TransferTaskRegistry transferTaskRegistry;//负责保持传输任务状态，用于对tasks, task的命令查询
    private final ApplicationShutdownService applicationShutdownService;//负责统一关闭整个客户端应用
    private final PushNotificationService pushNotificationService;//监听本地通知
    private final LocalContactBookService localContactBookService;//负责本地联系人和黑名单
    private final PrivateKeyArtifactService privateKeyArtifactService;
    private final DirectHandshakeService directHandshakeService;
    private final DirectSettingsService directSettingsService;
    private final DirectPeerConnectionManager directPeerConnectionManager;
    private final LanguageSettingsService languageSettingsService;
    private final ClientMessageService clientMessageService;
    private final OfflineCryptoService offlineCryptoService;
    private final PublicKeyPayloadService publicKeyPayloadService;
    private final ConsoleMessages messages;
    private Runnable notificationSubscription;
    private int lastProgressLineLength;

    @Autowired
    public ConsoleCommandRunner(
            ClientConnectionManager clientConnectionManager,
            ClientStartupCoordinator clientStartupCoordinator,
            ClientTransferService clientTransferService,
            ClientProperties clientProperties,
            CryptoSupport cryptoSupport,
            TransferTaskRegistry transferTaskRegistry,
            ApplicationShutdownService applicationShutdownService,
            PushNotificationService pushNotificationService,
            LocalContactBookService localContactBookService,
            PrivateKeyArtifactService privateKeyArtifactService,
            DirectHandshakeService directHandshakeService,
            DirectSettingsService directSettingsService,
            DirectPeerConnectionManager directPeerConnectionManager,
            LanguageSettingsService languageSettingsService,
            ClientMessageService clientMessageService,
            OfflineCryptoService offlineCryptoService,
            PublicKeyPayloadService publicKeyPayloadService,
            ConsoleMessages messages
    )
    {
        this.clientConnectionManager = clientConnectionManager;
        this.clientStartupCoordinator = clientStartupCoordinator;
        this.clientTransferService = clientTransferService;
        this.clientProperties = clientProperties;
        this.cryptoSupport = cryptoSupport;
        this.transferTaskRegistry = transferTaskRegistry;
        this.applicationShutdownService = applicationShutdownService;
        this.pushNotificationService = pushNotificationService;
        this.localContactBookService = localContactBookService;
        this.privateKeyArtifactService = privateKeyArtifactService;
        this.directHandshakeService = directHandshakeService;
        this.directSettingsService = directSettingsService;
        this.directPeerConnectionManager = directPeerConnectionManager;
        this.languageSettingsService = languageSettingsService;
        this.clientMessageService = clientMessageService;
        this.offlineCryptoService = offlineCryptoService;
        this.publicKeyPayloadService = publicKeyPayloadService;
        this.messages = messages;
    }

    public ConsoleCommandRunner(
            ClientConnectionManager clientConnectionManager,
            ClientStartupCoordinator clientStartupCoordinator,
            ClientTransferService clientTransferService,
            ClientProperties clientProperties,
            CryptoSupport cryptoSupport,
            TransferTaskRegistry transferTaskRegistry,
            ApplicationShutdownService applicationShutdownService,
            PushNotificationService pushNotificationService,
            LocalContactBookService localContactBookService,
            PrivateKeyArtifactService privateKeyArtifactService,
            DirectHandshakeService directHandshakeService,
            DirectSettingsService directSettingsService,
            DirectPeerConnectionManager directPeerConnectionManager,
            LanguageSettingsService languageSettingsService,
            ClientMessageService clientMessageService,
            ConsoleMessages messages
    )
    {
        this(
                clientConnectionManager,
                clientStartupCoordinator,
                clientTransferService,
                clientProperties,
                cryptoSupport,
                transferTaskRegistry,
                applicationShutdownService,
                pushNotificationService,
                localContactBookService,
                privateKeyArtifactService,
                directHandshakeService,
                directSettingsService,
                directPeerConnectionManager,
                languageSettingsService,
                clientMessageService,
                null,
                null,
                messages
        );
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startConsole()//startConsole监听ApplicationReadyEvent
    {
        notificationSubscription=pushNotificationService.subscribeLocal(this::handleNotification);//订阅本地通知
        //创建一个后台线程运行控制台
        Thread consoleThread = new Thread(this::runCommandLoop, "console-command-runner");//console-command-runner守护线程
        consoleThread.setDaemon(true);
        consoleThread.start();
    }

    @PreDestroy
    public void stopConsole()
    {
        if(notificationSubscription!=null)
        {
            notificationSubscription.run();//取消通知订阅服务
            notificationSubscription=null;
        }
    }

    private void runCommandLoop()
    {
        printWelcome();//打印欢迎消息
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, commandInputCharset()))) {
            handleStartupKeyPrompt(reader);
            while (isApplicationActive()) {//进入大循环
                printModeMenu();
                String selected = reader.readLine();
                if (selected == null) {
                    handleConsoleInputClosed();
                    return;
                }
                switch (selected.trim().toLowerCase(Locale.ROOT)) {
                    case "1", "relay" -> runRelayConsole(reader);
                    case "2", "direct" -> runDirectConsole(reader);
                    case "3", "offline", "decrypt", "decryption" -> runOfflineConsole(reader);
                    case "language" -> changeLanguage(reader);
                    case "0", "exit", "quit" -> {
                        exit();
                        return;
                    }
                    default -> System.out.println(messages.text(ConsoleMessages.Key.UNKNOWN_MODE));
                }
            }
        } catch (IOException ex) {
            System.out.println(messages.format(ConsoleMessages.Key.CONSOLE_STOPPED, ex.getMessage()));
        }
    }

    //模式选择，中继服务器/ IPv6直连
    private void printModeMenu()
    {
        System.out.println();
        System.out.println(messages.text(ConsoleMessages.Key.MODE_TITLE));
        System.out.println(messages.text(ConsoleMessages.Key.MODE_RELAY_OPTION));
        System.out.println(messages.text(ConsoleMessages.Key.MODE_DIRECT_OPTION));
        System.out.println(messages.text(ConsoleMessages.Key.MODE_OFFLINE_OPTION));
        System.out.println(messages.text(ConsoleMessages.Key.MODE_EXIT_OPTION));
        System.out.print("mode> ");
    }

    //使用中继服务器传输模式
    private void runRelayConsole(BufferedReader reader) throws IOException
    {
        System.out.println(messages.text(ConsoleMessages.Key.RELAY_CONSOLE_READY));
        while (isApplicationActive()) {
            System.out.print("fst-relay> ");
            String line = reader.readLine();
            if (line == null) {
                handleConsoleInputClosed();
                return;
            }
            String trimmed = line.trim();
            if ("back".equalsIgnoreCase(trimmed) || "mode".equalsIgnoreCase(trimmed)) {
                if(confirm(reader, messages.text(ConsoleMessages.Key.CONFIRM_RETURN_MODE_DISCONNECT_RELAY))) {
                    disconnect();
                    return;
                }
                continue;
            }
            handleCommand(reader, trimmed);
        }
    }

    //使用IPv6直连模式
    private void runDirectConsole(BufferedReader reader) throws IOException
    {
        System.out.println(messages.text(ConsoleMessages.Key.DIRECT_CONSOLE_READY));
        while (isApplicationActive()) {
            System.out.print("fst-direct> ");
            String line = reader.readLine();
            if (line == null) {
                handleConsoleInputClosed();
                return;
            }
            String trimmed = line.trim();
            if ("back".equalsIgnoreCase(trimmed) || "mode".equalsIgnoreCase(trimmed)) {
                if(confirm(reader, messages.text(ConsoleMessages.Key.CONFIRM_RETURN_MODE_CLOSE_DIRECT))) {
                    directPeerConnectionManager.stopListener();
                    return;
                }
                continue;
            }
            handleDirectCommand(reader, trimmed);
        }
    }

    private void runOfflineConsole(BufferedReader reader) throws IOException
    {
        System.out.println(messages.text(ConsoleMessages.Key.OFFLINE_CONSOLE_READY));
        while (isApplicationActive()) {
            System.out.print("fst-offline> ");
            String line = reader.readLine();
            if (line == null) {
                handleConsoleInputClosed();
                return;
            }
            String trimmed = line.trim();
            if ("back".equalsIgnoreCase(trimmed) || "mode".equalsIgnoreCase(trimmed)) {
                return;
            }
            handleOfflineCommand(reader, trimmed);
        }
    }

    private void handleStartupKeyPrompt(BufferedReader reader) throws IOException
    {
        try {
            if (!clientStartupCoordinator.shouldPromptForStartupKeySetup()) {
                remindIfKeyMissing();
                return;
            }

            System.out.println(messages.text(ConsoleMessages.Key.STARTUP_NO_KEY_PAUSED));
            System.out.print(messages.text(ConsoleMessages.Key.STARTUP_GENERATE_PROMPT));
            String answer = reader.readLine();
            if (answer != null && ("y".equalsIgnoreCase(answer.trim()) || "yes".equalsIgnoreCase(answer.trim()))) {
                Map<String, Object> result = clientStartupCoordinator.generateStartupKeyAndContinue();
                Object keyResult = result.get("keyResult");
                if (keyResult instanceof Map<?, ?> keyMap) {
                    printAnyMap(keyMap);
                }
                System.out.println(messages.text(ConsoleMessages.Key.STARTUP_KEY_GENERATED));
                return;
            }

            clientStartupCoordinator.skipStartupKeySetup();
            System.out.println(messages.text(ConsoleMessages.Key.STARTUP_SKIPPED));
            printMissingKeyReminder();
        } catch (Exception ex) {
            System.out.println(messages.format(ConsoleMessages.Key.STARTUP_UNABLE_HANDLE, ex.getMessage()));
            System.out.println(messages.text(ConsoleMessages.Key.RUN_KEY_INFO_AFTER_CRYPTO));
        }
    }

    private Charset commandInputCharset()//按照统一的字符集解码
    {
        java.io.Console console = System.console();
        return console == null ? Charset.defaultCharset() : console.charset();
    }

    private void handleCommand(BufferedReader reader, String line)//命令分发
    {
        if (line.isBlank()) {//空命令的情况
            return;
        }

        List<String> args = parseArguments(line);//解析命令参数
        if (args.isEmpty()) {
            return;
        }

        String command = args.get(0).toLowerCase(Locale.ROOT);//第一个参数是命令名
        try {
            switch (command) {
                case "help" -> printHelp();                     //打印所有可用命令
                case "language" -> changeLanguage(reader);      //切换控制台语言
                case "status" -> printStatus();                 //把当前客户端连接状态逐项打印出来
                case "connect" -> connect(args);                //用于连接服务器并完成认证，connect [host] [port]
                case "disconnect" -> disconnect();              //断开当前连接
                case "send" -> sendFile(args);                  //发送文件，send <filePath> <targetAccountId>     //filePath可以是绝对路径也可以是相对路径（相对程序运行的位置），accountId就是公钥指纹(64 位)
                case "message-send" -> sendRelayMessage(reader, args);
                case "messages" -> printMessageSummaries();
                case "message" -> printRelayConversation(args);
                case "incoming" -> printIncomingRequests();     //列出当前待处理的文件接收请求
                case "accept" -> acceptIncomingRequest(args);   //接收指定的incoming transfer任务， accept <transferId>
                case "reject" -> rejectIncomingRequest(args);   //拒绝指定的incoming transfer任务， reject <transferId>
                case "cancel" -> cancelTransfer(args);          //取消正在进行的传输任务， cancel <taskId|transferId>
                case "retransmit" -> requestRetransmission(args);//接收方请求发送方从断点续传， retransmit <taskId|transferId>
                case "retransmit-accept" -> acceptRetransmission(args); //发送方同意接收方的重传请求
                case "retransmit-reject" -> rejectRetransmission(args); //发送方拒绝接收方的重传请求
                case "contacts" -> printContacts();             //列出联系人
                case "contact-add" -> addContact(args);         //添加联系人，contact-add <accountId> [alias]
                case "contact-remove" -> removeContact(args);   //删除联系人，contact-remove <contact-数字|数字>
                case "contact-show" -> showContact(args);       //查看联系人详情，contact-show <contact-数字|数字>
                case "blacklist" -> printBlacklist();           //列出黑名单
                case "blacklist-add" -> addBlacklist(args);     //添加黑名单，blacklist-add <accountId> [reason]
                case "blacklist-add-contact" -> addBlacklistContact(args); //添加联系人到黑名单
                case "blacklist-remove" -> removeBlacklist(args);//删除黑名单，blacklist-remove <accountId>
                case "search-user" -> searchUser(args);         //搜索在线用户，search-user <accountId>
                case "search-user-add" -> searchUserAndAddContact(args); //搜索在线用户并加入联系人
                case "tasks" -> printTasks();                   //列出所有传输任务
                case "task" -> printTask(reader, args);         //动态查看单个任务详情， task <taskId|transferId> [--once]    //参数可以是任务Id, 也可以是传输Id, once表示只看看一眼，不动态的显示传输进度。退出动态查看传输进度的方式，1.直接按Enter,2.输入Q再按Enter就是退出动态查看传输进度了。
                case "open-received", "open-receive", "open-file" -> openReceivedFile(args); //在系统文件管理器中打开接收到的文件，open-received <taskId|transferId|fileName>
                case "public-key" -> printPublicKey();          //查看和导入密钥,打印当前客户端的本地公钥
                case "public-key-fingerprint", "accountid", "account-id" -> printPublicKeyFingerprint(args);  //计算指定公钥或本地公钥的指纹;因为公钥指纹就是本系统的accountId,故也兼容accountId指令
                case "key-info" -> printKeyInfo();              //打印Python加密服务管理的密钥状态
                case "generate-key" -> generateKey();           //请求Python加密服务生成密钥
                case "delete-key" -> deleteKey();               //请求Python加密服务删除密钥
                case "export-public-key" -> exportPublicKey();  //导出公钥二维码和文本文件
                case "export-private-key" -> exportPrivateKey();//导出私钥文本和二维码文件
                case "import-private-key" -> importPrivateKey(args);                //以文本的方式导入私钥，import-private-key <keyText>
                case "import-private-key-file" -> importPrivateKeyFile(args);       //从文件导入私钥，import-private-key-file <path>
                case "import-private-key-paste" -> importPrivateKeyPaste(reader);   //进入多行粘贴模式，用户可以粘贴多行私钥内容，最后一行只输入一个'.'标识结束， import-private-key-paste
                case "exit", "quit" -> exit();                  //推出程序， exit或者quit
                default -> System.out.println(messages.format(ConsoleMessages.Key.UNKNOWN_COMMAND, command));
            }
        } catch (Exception ex) {
            System.out.println(messages.format(ConsoleMessages.Key.COMMAND_FAILED, ex.getMessage()));
        }
    }

    private void handleDirectCommand(BufferedReader reader, String line)
    {
        if (line.isBlank()) {
            return;
        }
        List<String> args = parseArguments(line);
        if (args.isEmpty()) {
            return;
        }
        String command = args.get(0).toLowerCase(Locale.ROOT);
        try {
            switch (command) {
                case "help" -> printDirectHelp();
                case "language" -> changeLanguage(reader);
                case "status" -> printDirectStatus();
                case "handshake" -> directHandshake(reader);
                case "message-send" -> sendDirectMessage(reader, args);
                case "messages" -> printMessageSummaries();
                case "message" -> printDirectConversation(args);
                case "port-mode" -> directPortMode(args);
                case "qr-clean" -> System.out.println(messages.format(ConsoleMessages.Key.EXPIRED_QR_GROUPS_REMOVED, directHandshakeService.cleanupExpiredQr()));
                case "incoming" -> printIncomingRequests();
                case "accept" -> acceptIncomingRequest(args);
                case "reject" -> rejectIncomingRequest(args);
                case "cancel" -> cancelTransfer(args);
                case "retransmit" -> requestRetransmission(args);
                case "retransmit-accept" -> acceptRetransmission(args);
                case "retransmit-reject" -> rejectRetransmission(args);
                case "tasks" -> printTasks();
                case "task" -> printTask(reader, args);
                case "key-info" -> printKeyInfo();
                case "generate-key" -> generateKey();
                case "delete-key" -> deleteKey();
                case "export-public-key" -> exportPublicKey();
                case "export-private-key" -> exportPrivateKey();
                case "public-key" -> printPublicKey();
                case "public-key-fingerprint", "accountid", "account-id" -> printPublicKeyFingerprint(args);
                case "import-private-key" -> importPrivateKey(args);
                case "import-private-key-file" -> importPrivateKeyFile(args);
                case "import-private-key-paste" -> importPrivateKeyPaste(reader);
                case "exit", "quit" -> exit();
                default -> System.out.println(messages.format(ConsoleMessages.Key.UNKNOWN_DIRECT_COMMAND, command));
            }
        } catch (Exception ex) {
            System.out.println(messages.format(ConsoleMessages.Key.COMMAND_FAILED, ex.getMessage()));
        }
    }

    private void handleOfflineCommand(BufferedReader reader, String line)
    {
        if (line.isBlank()) {
            return;
        }
        List<String> args = parseArguments(line);
        if (args.isEmpty()) {
            return;
        }
        String command = args.get(0).toLowerCase(Locale.ROOT);
        try {
            switch (command) {
                case "help" -> printOfflineHelp();
                case "language" -> changeLanguage(reader);
                case "status" -> printStatus();
                case "fst-file-encrypt" -> fst2EncryptFile(args);
                case "fst-file-decrypt" -> fst2DecryptFile(args);
                case "fst-text-encrypt" -> fstTextEncrypt(reader, args);
                case "fst-text-decrypt" -> fstTextDecrypt(reader, args);
                case "contacts" -> printContacts();
                case "contact-add" -> addContact(args);
                case "contact-add-public-key" -> addContactPublicKey(args);
                case "contact-update-public-key" -> updateContactPublicKey(args);
                case "contact-remove" -> removeContact(args);
                case "contact-show" -> showContact(args);
                case "public-key" -> printPublicKey();
                case "public-key-fingerprint", "accountid", "account-id" -> printPublicKeyFingerprint(args);
                case "export-public-key" -> exportPublicKey();
                case "export-private-key" -> exportPrivateKey();
                case "key-info" -> printKeyInfo();
                case "generate-key" -> generateKey();
                case "delete-key" -> deleteKey();
                case "import-private-key" -> importPrivateKey(args);
                case "import-private-key-file" -> importPrivateKeyFile(args);
                case "import-private-key-paste" -> importPrivateKeyPaste(reader);
                case "exit", "quit" -> exit();
                default -> System.out.println(messages.format(ConsoleMessages.Key.UNKNOWN_COMMAND, command));
            }
        } catch (Exception ex) {
            System.out.println(messages.format(ConsoleMessages.Key.COMMAND_FAILED, ex.getMessage()));
        }
    }

    private void printDirectHelp()
    {
        messages.directHelpLines().forEach(System.out::println);
    }

    private void printOfflineHelp()
    {
        messages.offlineHelpLines().forEach(System.out::println);
    }

    private void printDirectStatus()
    {
        DirectSettings settings = directSettingsService.current();
        System.out.println(messages.format(ConsoleMessages.Key.DIRECT_LISTEN_PORT_MODE, settings.getListenPortMode()));
        if(settings.getListenPortMode() == com.client.direct.DirectListenPortMode.FIXED) {
            System.out.println(messages.format(ConsoleMessages.Key.FIXED_LISTEN_PORT, settings.getFixedListenPort()));
        }
        System.out.println(messages.format(ConsoleMessages.Key.QR_OUTPUT_CLEANED, directHandshakeService.cleanupExpiredQr()));
        System.out.println(messages.format(ConsoleMessages.Key.TASK_COUNT, transferTaskRegistry.allTasks().size()));
    }

    private void directPortMode(List<String> args)
    {
        if(args.size() == 1)
        {
            DirectSettings settings = directSettingsService.current();
            System.out.println(messages.label("listenPortMode") + "=" + settings.getListenPortMode());
            System.out.println(messages.label("fixedListenPort") + "=" + settings.getFixedListenPort());
            System.out.println(messages.label("settingsPath") + "=" + directSettingsService.settingsPath());
            return;
        }
        switch(args.get(1).toLowerCase(Locale.ROOT))
        {
            case "random" -> {
                DirectSettings settings = directSettingsService.useRandomPort();
                System.out.println(messages.format(ConsoleMessages.Key.DIRECT_PORT_MODE_SET, settings.getListenPortMode()));
            }
            case "fixed" -> {
                if(args.size() < 3)
                {
                    System.out.println(messages.usage("port-mode fixed <port>"));
                    return;
                }
                DirectSettings settings = directSettingsService.useFixedPort(Integer.parseInt(args.get(2)));
                System.out.println(messages.format(ConsoleMessages.Key.DIRECT_PORT_MODE_SET_WITH_PORT, settings.getListenPortMode(), settings.getFixedListenPort()));
            }
            default -> System.out.println(messages.usage("port-mode | port-mode random | port-mode fixed <port>"));
        }
    }

    private void directHandshake(BufferedReader reader) throws Exception
    {
        if (!ensureKeyPresent()) {
            return;
        }
        System.out.print(messages.text(ConsoleMessages.Key.ROLE_PROMPT));
        String role = reader.readLine();
        if(role == null) {
            return;
        }
        switch(role.trim().toLowerCase(Locale.ROOT))
        {
            case "sender", "s" -> directSenderHandshake(reader);
            case "receiver", "r" -> directReceiverHandshake(reader);
            default -> System.out.println(messages.text(ConsoleMessages.Key.INVALID_ROLE));
        }
    }

    private void directSenderHandshake(BufferedReader reader) throws Exception
    {
        DirectHandshakeService.SenderHandshakeOffer offer = directHandshakeService.createSenderOffer();
        printQrArtifact(offer.artifact(), offer.text());
        System.out.println(messages.text(ConsoleMessages.Key.SEND_QR_TO_RECEIVER));
        DirectSessionInfo session = waitForReceiverHandshake(reader, offer);
        if (session == null) {
            return;
        }
        directHandshakeService.deleteQrInvite(offer.offer().getInviteId());
        System.out.println(messages.format(ConsoleMessages.Key.DIRECT_SESSION_CONNECTED, session.getPeerDeviceId()));
        String taskId = promptDirectFileSend(reader, session);
        if (taskId == null) {
            return;
        }
        System.out.println(messages.format(ConsoleMessages.Key.SEND_TASK_CREATED, taskId));
    }

    private void directReceiverHandshake(BufferedReader reader) throws Exception
    {
        var receiverSession = waitForSenderHandshake(reader);
        if (receiverSession == null) {
            return;
        }
        printQrArtifact(receiverSession.getArtifact(), Files.readString(receiverSession.getArtifact().getFst1Path()).trim());
        System.out.println(messages.text(ConsoleMessages.Key.SEND_QR_TO_SENDER_WAITING));
    }

    private DirectSessionInfo waitForReceiverHandshake(BufferedReader reader, DirectHandshakeService.SenderHandshakeOffer offer) throws IOException
    {
        while (isApplicationActive()) {
            System.out.println(messages.text(ConsoleMessages.Key.PASTE_RECEIVER_FST1));
            System.out.println(messages.text(ConsoleMessages.Key.HANDSHAKE_CANCEL_HINT));
            System.out.print("receiver-fst1> ");
            String receiverInput = reader.readLine();
            if(receiverInput == null) {
                handleConsoleInputClosed();
                return null;
            }
            if(isHandshakeAbortInput(receiverInput)) {
                return null;
            }
            try {
                String receiverText = directHandshakeService.readFst1Text(receiverInput);
                return directHandshakeService.connectSender(offer.offer(), receiverText).get();
            } catch (Exception ex) {
                System.out.println(messages.format(ConsoleMessages.Key.COMMAND_FAILED, ex.getMessage()));
            }
        }
        return null;
    }

    private ReceiverListeningSession waitForSenderHandshake(BufferedReader reader) throws IOException
    {
        while (isApplicationActive()) {
            System.out.println(messages.text(ConsoleMessages.Key.PASTE_SENDER_FST1));
            System.out.println(messages.text(ConsoleMessages.Key.HANDSHAKE_CANCEL_HINT));
            System.out.print("sender-fst1> ");
            String senderInput = reader.readLine();
            if(senderInput == null) {
                handleConsoleInputClosed();
                return null;
            }
            if(isHandshakeAbortInput(senderInput)) {
                return null;
            }
            try {
                String senderText = directHandshakeService.readFst1Text(senderInput);
                return directHandshakeService.createReceiverResponse(senderText);
            } catch (Exception ex) {
                System.out.println(messages.format(ConsoleMessages.Key.COMMAND_FAILED, ex.getMessage()));
            }
        }
        return null;
    }

    private String promptDirectFileSend(BufferedReader reader, DirectSessionInfo session) throws IOException
    {
        while (isApplicationActive()) {
            System.out.println(messages.text(ConsoleMessages.Key.HANDSHAKE_CANCEL_HINT));
            System.out.print(messages.text(ConsoleMessages.Key.FILE_PATH_TO_SEND));
            String filePath = reader.readLine();
            if(filePath == null) {
                handleConsoleInputClosed();
                return null;
            }
            if(isHandshakeAbortInput(filePath)) {
                return null;
            }
            try {
                return clientTransferService.sendFileDirect(
                        PathInputNormalizer.toPath(filePath),
                        session.getReceiverResponse(),
                        session.getTransport());
            } catch (Exception ex) {
                System.out.println(messages.format(ConsoleMessages.Key.COMMAND_FAILED, ex.getMessage()));
            }
        }
        return null;
    }

    private boolean isHandshakeAbortInput(String value)
    {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isBlank()
                || "cancel".equalsIgnoreCase(trimmed)
                || "back".equalsIgnoreCase(trimmed);
    }

    private void printQrArtifact(QrArtifact artifact, String text) throws IOException
    {
        System.out.println(messages.format(ConsoleMessages.Key.QR_PNG, artifact.getPngPath()));
        System.out.println(messages.format(ConsoleMessages.Key.QR_FST1, artifact.getFst1Path()));
        System.out.println(messages.format(ConsoleMessages.Key.QR_ASCII, artifact.getAsciiPath()));
        if(text != null && text.startsWith("FST1:")) {
            return;
        }
        System.out.println(messages.text(ConsoleMessages.Key.QR_TEXT));
        System.out.println(text);
    }

    private boolean confirm(BufferedReader reader, String prompt) throws IOException
    {
        System.out.print(prompt);
        String answer = reader.readLine();
        return answer != null && ("y".equalsIgnoreCase(answer.trim()) || "yes".equalsIgnoreCase(answer.trim()));
    }

    private void printWelcome()//打印欢迎消息
    {
        System.out.println();
        System.out.println(messages.text(ConsoleMessages.Key.WELCOME_READY));
        System.out.println(messages.text(ConsoleMessages.Key.WELCOME_HELP_HINT));
    }

    private void printHelp()//打印该控制台程序支持的所有命令
    {
        messages.relayHelpLines().forEach(System.out::println);
    }

    //处理切换语言的函数
    private void changeLanguage(BufferedReader reader) throws IOException
    {
        System.out.println(messages.text(ConsoleMessages.Key.SELECT_LANGUAGE));//目前先只支持这两个语言
        System.out.println("  1. English");
        System.out.println("  2. Chinese");
        System.out.print("language> ");

        String selected = reader.readLine();
        if (selected == null) {
            handleConsoleInputClosed();
            return;
        }

        //切换语言
        UiLanguage language = UiLanguage.fromUserSelection(selected);
        if(language == null)
        {
            System.out.println(messages.text(ConsoleMessages.Key.INVALID_LANGUAGE));
            return;
        }
        languageSettingsService.save(language);
        if(language == UiLanguage.CHINESE)
        {
            System.out.println(messages.text(ConsoleMessages.Key.LANGUAGE_CHANGED_CHINESE));
            return;
        }
        System.out.println(messages.text(ConsoleMessages.Key.LANGUAGE_CHANGED_ENGLISH));
    }

    //在控制它推送待处理的传输请求
    private void handleNotification(String type, Object payload)
    {
        if ("incoming-transfer-request".equals(type)) {
            printIncomingTransferNotification(payload);
        }
        if ("transfer-retransmit-request-received".equals(type)) {
            printRetransmitRequestNotification(payload);
        }
        if ("incoming-text-message".equals(type)) {
            printIncomingTextMessageNotification(payload);
        }
    }

    //打印待处理的传输请求的通知
    private void printIncomingTransferNotification(Object payload)
    {
        if (!(notificationPayload(payload) instanceof Map<?, ?> values)) {
            printConsoleNotice(messages.text(ConsoleMessages.Key.INCOMING_NOTIFICATION_SIMPLE));
            return;
        }

        printConsoleNotice(messages.format(
                ConsoleMessages.Key.INCOMING_NOTIFICATION,
                values.get("transferId"),
                values.get("senderDeviceId"),
                values.get("fileName"),
                values.get("fileSize"),
                values.get("totalBlocks"),
                values.get("transferId"),
                values.get("transferId")
        ));
    }

    //打印重传请求的通知
    private void printRetransmitRequestNotification(Object payload)
    {
        if (!(notificationPayload(payload) instanceof Map<?, ?> values)) {
            printConsoleNotice(messages.text(ConsoleMessages.Key.RETRANSMISSION_NOTIFICATION_SIMPLE));
            return;
        }

        printConsoleNotice(messages.format(
                ConsoleMessages.Key.RETRANSMISSION_NOTIFICATION,
                values.get("transferId"),
                values.get("fileName"),
                values.get("startBlockId"),
                values.get("reason"),
                values.get("transferId"),
                values.get("transferId")
        ));
    }

    private void printIncomingTextMessageNotification(Object payload)
    {
        if (!(notificationPayload(payload) instanceof Map<?, ?> values)) {
            return;
        }
        printConsoleNotice(messages.format(
                ConsoleMessages.Key.INCOMING_TEXT_MESSAGE_NOTIFICATION,
                values.get("senderAccountId"),
                values.get("senderAccountId")
        ));
    }

    private Object notificationPayload(Object payload)//统一提取通知消息里的真实业务数据
    {
        if(payload instanceof Map<?, ?> body && body.containsKey("payload"))
        {
            return body.get("payload");
        }
        return payload;
    }

    private void printConsoleNotice(String message)
    {
        synchronized (System.out) {
            System.out.println();
            System.out.println(message);
            System.out.print("fst> ");
        }
    }

    private void printStatus()
    {
        printMap(clientConnectionManager.currentStatus());
    }

    private void connect(List<String> args) throws Exception    //处理connect命令
    {
        if (!ensureKeyPresent()) {  //先检查是否有密钥
            return;
        }
        String host = args.size() >= 2 ? args.get(1) : clientProperties.getServerHost();//host参数
        int port = args.size() >= 3 ? Integer.parseInt(args.get(2)) : clientProperties.getServerPort();//port参数
        clientConnectionManager.connectAndAuthenticate(host, port)
                .get(clientProperties.getAuthTimeoutSeconds(), TimeUnit.SECONDS);//通过该方法连接服务器并完成身份认证
        System.out.println(messages.format(ConsoleMessages.Key.CONNECTED_AUTHENTICATED, host, port));
    }

    private void disconnect()
    {
        clientConnectionManager.disconnect();
        System.out.println(messages.text(ConsoleMessages.Key.DISCONNECTED));
    }

    private void sendFile(List<String> args)    //处理发送文件的命令
    {
        if (!ensureKeyPresent()) {  //检查当前是否有密钥
            return;
        }
        if (args.size() < 3) {  //校验参数
            System.out.println(messages.usage("send <filePath> <targetAccountId>"));
            return;
        }
        //最后一个参数是目标账户的公钥指纹，中间参数拼回文件路径，兼容未加引号但包含空格的路径
        String filePath = joinArguments(args, 1, args.size() - 1);
        String targetAccountId = localContactBookService.resolveAccountId(args.get(args.size() - 1));
        String taskId = clientTransferService.sendFile(PathInputNormalizer.toPath(filePath), targetAccountId);//处理发送文件的函数，对于文件路径进行规格化操作
        System.out.println(messages.format(ConsoleMessages.Key.SEND_TASK_CREATED, taskId));
    }

    private void fst2EncryptFile(List<String> args)
    {
        if (!ensureKeyPresent()) {
            return;
        }
        if (args.size() < 3) {
            System.out.println(messages.usage("fst-file-encrypt <filePath> <publicKey|publicKeyFile|contact-N> [outputDir]"));
            return;
        }
        int receiverIndex = resolveOfflineReceiverArgumentIndex(args);
        String filePath = joinArguments(args, 1, receiverIndex);
        String receiver = args.get(receiverIndex);
        Path outputDir = receiverIndex + 1 < args.size() ? PathInputNormalizer.toPath(joinArguments(args, receiverIndex + 1)) : null;
        OfflineCryptoService.Fst2EncryptResult result = offlineCryptoService.encryptFile(PathInputNormalizer.toPath(filePath), receiver, outputDir);
        System.out.println("FST2 file created: " + result.outputPath());
        System.out.println("fileSize: " + result.fileSize());
        System.out.println("totalBlocks: " + result.totalBlocks());
    }

    private int resolveOfflineReceiverArgumentIndex(List<String> args)
    {
        if(args.size() >= 4 && looksLikeOfflineReceiverToken(args.get(args.size() - 2)))
        {
            return args.size() - 2;
        }
        return args.size() - 1;
    }

    private boolean looksLikeOfflineReceiverToken(String value)
    {
        if(value == null || value.isBlank())
        {
            return false;
        }
        String token = value.trim();
        if(token.startsWith("contact-") || token.matches("\\d+"))
        {
            return true;
        }
        String lower = token.toLowerCase(Locale.ROOT);
        if(lower.endsWith(".fstpub") || lower.endsWith(".png"))
        {
            return true;
        }
        return token.startsWith(PublicKeyPayloadService.PREFIX) || token.length() >= 300;
    }

    private void fst2DecryptFile(List<String> args)
    {
        if (!ensureKeyPresent()) {
            return;
        }
        if (args.size() < 2) {
            System.out.println(messages.usage("fst-file-decrypt <fst2Path> [outputDir]"));
            return;
        }
        Path outputDir = args.size() >= 3 ? PathInputNormalizer.toPath(joinArguments(args, 2)) : null;
        OfflineCryptoService.Fst2DecryptResult result = offlineCryptoService.decryptFile(PathInputNormalizer.toPath(args.get(1)), outputDir);
        System.out.println("FST2 file decrypted: " + result.outputPath());
        System.out.println("fileName: " + result.fileName());
        System.out.println("fileSize: " + result.fileSize());
        System.out.println("totalBlocks: " + result.totalBlocks());
    }

    private void fstTextEncrypt(BufferedReader reader, List<String> args) throws IOException
    {
        if (!ensureKeyPresent()) {
            return;
        }
        if(args.size() < 2)
        {
            System.out.println(messages.usage("fst-text-encrypt <publicKey|publicKeyFile|contact-N>"));
            return;
        }
        String text = readUntilQuit(reader, "text> ");
        if(text == null)
        {
            return;
        }
        OfflineCryptoService.FstTextEncryptResult result = offlineCryptoService.encryptText(text, args.get(1));
        System.out.println(result.payload());
    }

    private void fstTextDecrypt(BufferedReader reader, List<String> args) throws IOException
    {
        if (!ensureKeyPresent()) {
            return;
        }
        String payload;
        if(args.size() >= 2)
        {
            payload = joinArguments(args, 1);
        }
        else
        {
            payload = readUntilQuit(reader, "fst-text> ");
            if(payload == null)
            {
                return;
            }
        }
        OfflineCryptoService.FstTextDecryptResult result = offlineCryptoService.decryptText(payload);
        System.out.println(result.text());
    }

    private String readUntilQuit(BufferedReader reader, String prompt) throws IOException
    {
        List<String> lines = new ArrayList<>();
        while(isApplicationActive())
        {
            System.out.print(prompt);
            String line = reader.readLine();
            if(line == null)
            {
                handleConsoleInputClosed();
                return null;
            }
            if(":q".equals(line))
            {
                return String.join(System.lineSeparator(), lines);
            }
            lines.add(line);
        }
        return null;
    }

    private void sendRelayMessage(BufferedReader reader, List<String> args) throws IOException
    {
        if (!ensureKeyPresent()) {
            return;
        }
        if (args.size() < 2) {
            System.out.println(messages.usage("fst-file-decrypt <fst2Path> [outputDir]"));
            return;
        }
        Path outputDir = args.size() >= 3 ? PathInputNormalizer.toPath(joinArguments(args, 2)) : null;
        OfflineCryptoService.Fst2DecryptResult result = offlineCryptoService.decryptFile(PathInputNormalizer.toPath(args.get(1)), outputDir);
        System.out.println("FST2 file decrypted: " + result.outputPath());
        System.out.println("fileName: " + result.fileName());
        System.out.println("fileSize: " + result.fileSize());
        System.out.println("totalBlocks: " + result.totalBlocks());
    }

    private void fstTextEncrypt(BufferedReader reader, List<String> args) throws IOException
    {
        if (!ensureKeyPresent()) {
            return;
        }
        if(args.size() < 2)
        {
            System.out.println(messages.usage("fst-text-encrypt <publicKey|publicKeyFile|contact-N>"));
            return;
        }
        String text = readUntilQuit(reader, "text> ");
        if(text == null)
        {
            return;
        }
        OfflineCryptoService.FstTextEncryptResult result = offlineCryptoService.encryptText(text, args.get(1));
        System.out.println(result.payload());
    }

    private void fstTextDecrypt(BufferedReader reader, List<String> args) throws IOException
    {
        if (!ensureKeyPresent()) {
            return;
        }
        String payload;
        if(args.size() >= 2)
        {
            payload = joinArguments(args, 1);
        }
        else
        {
            payload = readUntilQuit(reader, "fst-text> ");
            if(payload == null)
            {
                return;
            }
        }
        OfflineCryptoService.FstTextDecryptResult result = offlineCryptoService.decryptText(payload);
        System.out.println(result.text());
    }

    private String readUntilQuit(BufferedReader reader, String prompt) throws IOException
    {
        List<String> lines = new ArrayList<>();
        while(isApplicationActive())
        {
            System.out.print(prompt);
            String line = reader.readLine();
            if(line == null)
            {
                handleConsoleInputClosed();
                return null;
            }
            if(":q".equals(line))
            {
                return String.join(System.lineSeparator(), lines);
            }
            lines.add(line);
        }
        return null;
    }

    private void sendRelayMessage(BufferedReader reader, List<String> args) throws IOException
    {
        if (!ensureKeyPresent()) {
            return;
        }
        if(args.size() < 2)
        {
            System.out.println(messages.usage("message-send <accountId|contact-N>"));
            return;
        }
        String body = promptMessageBody(reader);
        if(body == null)
        {
            return;
        }
        TextMessageRecord record = clientMessageService.sendRelay(args.get(1), body);
        System.out.println(messages.format(ConsoleMessages.Key.MESSAGE_SENT, record.getMessageId()));
    }

    private void sendDirectMessage(BufferedReader reader, List<String> args) throws IOException
    {
        if (!ensureKeyPresent()) {
            return;
        }
        if(args.size() > 1)
        {
            System.out.println(messages.usage("message-send"));
            return;
        }
        String body = promptMessageBody(reader);
        if(body == null)
        {
            return;
        }
        TextMessageRecord record = clientMessageService.sendDirect(body, clientMessageService.requireSingleActiveDirectSession());
        System.out.println(messages.format(ConsoleMessages.Key.MESSAGE_SENT, record.getMessageId()));
    }

    private String promptMessageBody(BufferedReader reader) throws IOException
    {
        List<String> lines = new ArrayList<>();
        while(isApplicationActive())
        {
            System.out.println(messages.text(ConsoleMessages.Key.MESSAGE_EDIT_HINT));
            if(!lines.isEmpty())
            {
                System.out.println(messages.text(ConsoleMessages.Key.MESSAGE_CURRENT_DRAFT));
                System.out.println(String.join(System.lineSeparator(), lines));
            }
            while(isApplicationActive())
            {
                System.out.print("message> ");
                String line = reader.readLine();
                if(line == null)
                {
                    handleConsoleInputClosed();
                    return null;
                }
                if(":q".equals(line))
                {
                    break;
                }
                lines.add(line);
            }
            String body = String.join(System.lineSeparator(), lines);
            if(body.trim().isEmpty())
            {
                System.out.println(messages.text(ConsoleMessages.Key.MESSAGE_EMPTY));
                continue;
            }
            if(body.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > ClientMessageService.MAX_MESSAGE_BYTES)
            {
                System.out.println(messages.text(ConsoleMessages.Key.MESSAGE_TOO_LARGE));
                continue;
            }
            System.out.println(messages.text(ConsoleMessages.Key.MESSAGE_PREVIEW));
            System.out.println(body);
            System.out.println(messages.text(ConsoleMessages.Key.MESSAGE_REVIEW_HINT));
            System.out.print("message-review> ");
            String action = reader.readLine();
            if(action == null)
            {
                handleConsoleInputClosed();
                return null;
            }
            if(action.isBlank())
            {
                return body;
            }
            if("i".equalsIgnoreCase(action.trim()))
            {
                continue;
            }
            if("q".equalsIgnoreCase(action.trim()))
            {
                System.out.println(messages.text(ConsoleMessages.Key.MESSAGE_CANCELED));
                return null;
            }
            System.out.println(messages.text(ConsoleMessages.Key.MESSAGE_REVIEW_INVALID));
        }
        return null;
    }

    private void printMessageSummaries()
    {
        List<ConversationSummary> summaries = clientMessageService.summaries();
        if(summaries.isEmpty())
        {
            System.out.println(messages.text(ConsoleMessages.Key.NO_MESSAGES));
            return;
        }
        System.out.println(messages.tableHeader("accountId", "alias", "unread", "lastMessageTime", "direction", "status", "mode"));
        for(ConversationSummary summary : summaries)
        {
            System.out.printf(
                    "%s | %s | %d | %s | %s | %s | %s%n",
                    summary.peerAccountId(),
                    displayNullable(summary.alias()),
                    summary.unreadCount(),
                    summary.lastMessageTime(),
                    summary.lastDirection(),
                    summary.lastStatus(),
                    summary.lastMode()
            );
        }
    }

    private void printRelayConversation(List<String> args)
    {
        if(args.size() < 2)
        {
            System.out.println(messages.usage("message <accountId|contact-N>"));
            return;
        }
        printConversation(clientMessageService.conversation(args.get(1), true));
    }

    private void printDirectConversation(List<String> args)
    {
        if(args.size() < 2)
        {
            printConversation(clientMessageService.directActiveConversation(true));
            return;
        }
        printConversation(clientMessageService.conversation(args.get(1), true));
    }

    private void printConversation(List<TextMessageRecord> records)
    {
        if(records.isEmpty())
        {
            System.out.println(messages.text(ConsoleMessages.Key.NO_MESSAGES));
            return;
        }
        for(TextMessageRecord record : records)
        {
            String direction = record.getDirection() == com.client.message.MessageDirection.OUTGOING ? "me -> peer" : "peer -> me";
            System.out.printf(
                    "%s | %s | %s%s%n",
                    direction,
                    record.getCreatedAt(),
                    record.getStatus(),
                    record.getReadAt() == null ? "" : " | readAt=" + record.getReadAt()
            );
            if(record.getErrorMessage() != null)
            {
                System.out.println("error=" + record.getErrorMessage());
            }
            System.out.println(record.getBody());
            System.out.println();
        }
    }

    private void printTasks()//任务查询命令，打印所有任务
    {
        List<TransferTask> tasks = transferTaskRegistry.allTasks();
        if (tasks.isEmpty()) {
            System.out.println(messages.text(ConsoleMessages.Key.NO_TRANSFER_TASKS));
            return;
        }
        System.out.println(messages.tableHeader("taskId", "direction", "mode", "status", "progress", "fileName", "message"));
        for (TransferTask task : tasks) {
            System.out.printf(
                    "%s | %s | %s | %s | %.2f%% | %s | %s%n",
                    task.getTaskId(),
                    task.getDirection(),
                    task.getTransportMode(),
                    task.getStatus(),
                    task.getProgress() * 100D,
                    task.getFileName(),
                    task.getMessage()
            );
        }
    }

    private void printIncomingRequests()//打印所有待处理的接收请求
    {
        List<ClientTransferService.PendingIncomingTransferRequest> requests = clientTransferService.pendingIncomingTransferRequestsDetailed();
        if (requests.isEmpty()) {
            System.out.println(messages.text(ConsoleMessages.Key.NO_INCOMING_REQUESTS));
            return;
        }
        System.out.println(messages.tableHeader("receivedAt", "transferId", "sender", "file", "bytes", "blocks"));
        for (ClientTransferService.PendingIncomingTransferRequest request : requests) {
            IncomingTransferRequestPacket packet = request.packet();
            System.out.printf(
                    "%s | %s | %s | %s | %d | %d%n",
                    request.receivedAt(),
                    packet.getTransferId(),
                    packet.getSenderDeviceId(),
                    packet.getFileName(),
                    packet.getFileSize(),
                    packet.getTotalBlocks()
            );
        }
    }

    private void acceptIncomingRequest(List<String> args)//接受某个传输请求
    {
        if (args.size() < 2) {
            System.out.println(messages.usage("accept <transferId>"));
            return;
        }
        clientTransferService.acceptIncomingTransfer(args.get(1));//参数是任务Id
        System.out.println(messages.format(ConsoleMessages.Key.ACCEPTED_INCOMING, args.get(1)));
    }

    private void rejectIncomingRequest(List<String> args)//拒绝某个传输请求
    {
        if (args.size() < 2) {
            System.out.println(messages.usage("reject <transferId>"));
            return;
        }
        clientTransferService.rejectIncomingTransfer(args.get(1));//参数是任务Id
        System.out.println(messages.format(ConsoleMessages.Key.REJECTED_INCOMING, args.get(1)));
    }

    //处理取消传输任务
    private void cancelTransfer(List<String> args)
    {
        if (args.size() < 2) {
            System.out.println(messages.usage("cancel <taskId|transferId>"));
            return;
        }
        clientTransferService.cancelTransfer(args.get(1));
        System.out.println(messages.format(ConsoleMessages.Key.TRANSFER_CANCELED, args.get(1)));
    }

    private void requestRetransmission(List<String> args)
    {
        if (args.size() < 2) {
            System.out.println(messages.usage("retransmit <taskId|transferId>"));
            return;
        }
        clientTransferService.requestRetransmission(args.get(1));
        System.out.println(messages.format(ConsoleMessages.Key.RETRANSMISSION_REQUESTED, args.get(1)));
    }

    private void acceptRetransmission(List<String> args)
    {
        if (args.size() < 2) {
            System.out.println(messages.usage("retransmit-accept <transferId>"));
            return;
        }
        clientTransferService.acceptRetransmission(args.get(1));
        System.out.println(messages.format(ConsoleMessages.Key.RETRANSMISSION_ACCEPTED, args.get(1)));
    }

    private void rejectRetransmission(List<String> args)
    {
        if (args.size() < 2) {
            System.out.println(messages.usage("retransmit-reject <transferId>"));
            return;
        }
        clientTransferService.rejectRetransmission(args.get(1));
        System.out.println(messages.format(ConsoleMessages.Key.RETRANSMISSION_REJECTED, args.get(1)));
    }

    private void printContacts()
    {
        List<ContactRecord> contacts = localContactBookService.listContacts();
        if (contacts.isEmpty()) {
            System.out.println(messages.text(ConsoleMessages.Key.NO_CONTACTS));
            return;
        }

        System.out.println(messages.tableHeader("contact", "alias", "accountId", "publicKey"));
        for (ContactRecord contact : contacts) {
            System.out.printf(
                    "contact-%d | %s | %s | %s%n",
                    contact.getContactIndex(),
                    displayNullable(contact.getAlias()),
                    contact.getAccountId(),
                    abbreviate(contact.getPublicKey(), 32)
            );
        }
    }

    //用户手动添加联系人时，系统尽量帮用户从服务器补全 publicKey。
    //如果补不到，也允许保存联系人，只是 publicKey 为空。
    private void addContact(List<String> args)//指令格式contact-add <accountId> [alias]
    {
        if (args.size() < 2) {
            System.out.println(messages.usage("contact-add <accountId> [alias]"));
            return;
        }

        String alias = args.size() >= 3 ? joinArguments(args, 2) : null;
        String publicKey = searchPublicKeyForContact(args.get(1));
        ContactRecord contact = localContactBookService.addContact(args.get(1), publicKey, alias);
        System.out.println(messages.format(
                ConsoleMessages.Key.CONTACT_SAVED,
                contact.getContactIndex(),
                displayNullable(contact.getAlias()),
                contact.getAccountId(),
                abbreviate(contact.getPublicKey(), 32)
        ));
    }

    private void addContactPublicKey(List<String> args) throws Exception
    {
        if (args.size() < 2) {
            System.out.println(messages.usage("contact-add-public-key <publicKey|publicKeyPath> [alias]"));
            return;
        }
        String publicKey = publicKeyPayloadService.resolvePublicKey(args.get(1));
        String accountId = publicKeyPayloadService.accountIdForPublicKey(publicKey);
        String alias = args.size() >= 3 ? joinArguments(args, 2) : null;
        ContactRecord contact = localContactBookService.addContact(accountId, publicKey, alias);
        System.out.println(messages.format(
                ConsoleMessages.Key.CONTACT_SAVED,
                contact.getContactIndex(),
                displayNullable(contact.getAlias()),
                contact.getAccountId(),
                abbreviate(contact.getPublicKey(), 32)
        ));
    }

    private void updateContactPublicKey(List<String> args) throws Exception
    {
        if (args.size() < 3) {
            System.out.println(messages.usage("contact-update-public-key <contact-N|N> <publicKey|publicKeyPath>"));
            return;
        }
        int contactIndex = parseContactIndexArgument(args.get(1));
        String publicKey = publicKeyPayloadService.resolvePublicKey(args.get(2));
        String accountId = publicKeyPayloadService.accountIdForPublicKey(publicKey);
        ContactRecord contact = localContactBookService.updateContactPublicKey(contactIndex, publicKey, accountId);
        System.out.println(messages.format(
                ConsoleMessages.Key.CONTACT_SAVED,
                contact.getContactIndex(),
                displayNullable(contact.getAlias()),
                contact.getAccountId(),
                abbreviate(contact.getPublicKey(), 32)
        ));
    }

    //根据 accountId 去服务器查 publicKey。
    private String searchPublicKeyForContact(String accountId)
    {
        try {
            OnlineUserSearchResultPacket result = clientTransferService.searchOnlineUser(accountId);//向服务器发起搜索请求，查该accountId当前是否在线，是否有可用的publicKey
            if(result.isSearchResult() && result.getPublicKey()!=null && !result.getPublicKey().isBlank())
            {
                return result.getPublicKey();
            }
            System.out.println(messages.text(ConsoleMessages.Key.ONLINE_USER_NOT_FOUND_CONTACT_EMPTY));
        } catch (Exception ex) {
            System.out.println(messages.format(ConsoleMessages.Key.SEARCH_PUBLIC_KEY_FAILED, ex.getMessage()));
        }
        return null;
    }

    private void removeContact(List<String> args)
    {
        if (args.size() < 2) {
            System.out.println(messages.usage("contact-remove <contact-N|N>"));
            return;
        }

        int contactIndex = parseContactIndexArgument(args.get(1));
        localContactBookService.removeContactByIndex(contactIndex);
        System.out.println(messages.format(ConsoleMessages.Key.CONTACT_REMOVED, contactIndex));
    }

    private void showContact(List<String> args)
    {
        if (args.size() < 2) {
            System.out.println(messages.usage("contact-show <contact-N|N>"));
            return;
        }

        int contactIndex = parseContactIndexArgument(args.get(1));
        ContactRecord contact = localContactBookService.findContactByIndex(contactIndex).orElse(null);
        if (contact == null) {
            System.out.println(messages.format(ConsoleMessages.Key.CONTACT_NOT_FOUND, contactIndex));
            return;
        }

        printLabelValue("contact", "contact-" + contact.getContactIndex());
        printLabelValue("alias", displayNullable(contact.getAlias()));
        printLabelValue("accountId", contact.getAccountId());
        printLabelValue("publicKey", contact.getPublicKey());
        printLabelValue("createdAt", contact.getCreatedAt());
        printLabelValue("updatedAt", contact.getUpdatedAt());
    }

    private void printBlacklist()
    {
        List<BlacklistRecord> records = localContactBookService.listBlacklist();
        if (records.isEmpty()) {
            System.out.println(messages.text(ConsoleMessages.Key.NO_BLACKLIST));
            return;
        }

        System.out.println(messages.tableHeader("accountId", "reason", "publicKey", "createdAt"));
        for (BlacklistRecord record : records) {
            System.out.printf(
                    "%s | %s | %s | %s%n",
                    record.getAccountId(),
                    displayNullable(record.getReason()),
                    abbreviate(record.getPublicKey(), 32),
                    record.getCreatedAt()
            );
        }
    }

    private void addBlacklist(List<String> args)
    {
        if (args.size() < 2) {
            System.out.println(messages.usage("blacklist-add <accountId> [reason]"));
            return;
        }

        String reason = args.size() >= 3 ? joinArguments(args, 2) : null;
        BlacklistRecord record = localContactBookService.addBlacklist(args.get(1), null, reason);
        System.out.println(messages.format(ConsoleMessages.Key.BLACKLIST_SAVED, record.getAccountId()));
    }

    private void addBlacklistContact(List<String> args)
    {
        if (args.size() < 2) {
            System.out.println(messages.usage("blacklist-add-contact <contact-N|N> [reason]"));
            return;
        }

        int contactIndex = parseContactIndexArgument(args.get(1));
        String reason = args.size() >= 3 ? joinArguments(args, 2) : null;
        BlacklistRecord record = localContactBookService.addBlacklistByContactIndex(contactIndex, reason);
        System.out.println(messages.format(ConsoleMessages.Key.BLACKLIST_SAVED_FROM_CONTACT, contactIndex, record.getAccountId()));
    }

    private void removeBlacklist(List<String> args)
    {
        if (args.size() < 2) {
            System.out.println(messages.usage("blacklist-remove <accountId>"));
            return;
        }

        localContactBookService.removeBlacklist(args.get(1));
        System.out.println(messages.format(ConsoleMessages.Key.BLACKLIST_REMOVED, args.get(1)));
    }

    private void searchUser(List<String> args)
    {
        if (args.size() < 2) {
            System.out.println(messages.usage("search-user <accountId>"));
            return;
        }

        OnlineUserSearchResultPacket result = clientTransferService.searchOnlineUser(args.get(1));
        printOnlineUserSearchResult(result);
    }

    private void searchUserAndAddContact(List<String> args)
    {
        if (args.size() < 2) {
            System.out.println(messages.usage("search-user-add <accountId> [alias]"));
            return;
        }

        String alias = args.size() >= 3 ? joinArguments(args, 2) : null;
        OnlineUserSearchResultPacket result = clientTransferService.searchOnlineUser(args.get(1));
        printOnlineUserSearchResult(result);
        if(!result.isSearchResult())
        {
            return;
        }

        ContactRecord contact = localContactBookService.addContact(result.getAccountId(), result.getPublicKey(), alias);
        System.out.println(messages.format(
                ConsoleMessages.Key.CONTACT_SAVED_SHORT,
                contact.getContactIndex(),
                displayNullable(contact.getAlias()),
                contact.getAccountId()
        ));
    }

    private void printOnlineUserSearchResult(OnlineUserSearchResultPacket result)
    {
        printLabelValue("found", result.isSearchResult());
        printLabelValue("accountId", result.getAccountId());
        printLabelValue("message", result.getMessage());
        if(!result.isSearchResult())
        {
            return;
        }
        printLabelValue("publicKey", result.getPublicKey());
    }

    //查看传输进度的时候，可以动态的显示传输进度，也可以仅是一次传输任务进度的快照
    private void printTask(BufferedReader reader, List<String> args) throws IOException
    {
        if (args.size() < 2) {      //校验参数数量
            System.out.println(messages.usage("task <taskId|transferId> [--once]"));
            return;
        }

        String id = args.get(1);    //根据用户输入的id查任务
        TransferTask task = transferTaskRegistry.findByTaskId(id)//根据taskId查
                .or(() -> transferTaskRegistry.findByTransferId(id))//根据transferId查
                .orElse(null);
        if (task == null) {
            System.out.println(messages.format(ConsoleMessages.Key.TASK_NOT_FOUND, id));
            return;
        }

        //判断是不是仅查看一次传输任务进度快照
        boolean printOnce = args.stream().anyMatch("--once"::equalsIgnoreCase);
        if (!printOnce && !isTerminal(task.getStatus())) {
            watchTaskProgress(reader, task);//动态查看传输任务的进度
            return;
        }

        printTaskDetails(task);
    }

    //动态查看传输任务的进度的函数
    private void watchTaskProgress(BufferedReader reader, TransferTask task) throws IOException
    {
        System.out.println(messages.text(ConsoleMessages.Key.WATCHING_TASK));
        lastProgressLineLength = 0;
        while (isApplicationActive()) {     //外层循环
            synchronized (System.out) {
                printProgressLine(formatProgressLine(task));// \r把光标移回当前行开头，然后覆盖旧内容
            }

            //任务进入终态就停止动态查看
            if (isTerminal(task.getStatus())) {
                break;
            }
            if (reader.ready()) {   //如果用户中途退出查看进度
                String line = reader.readLine();
                if (line == null || line.isBlank() || "q".equalsIgnoreCase(line.trim())) {
                    synchronized (System.out) {
                        System.out.println();
                        System.out.println(messages.text(ConsoleMessages.Key.STOPPED_WATCHING));
                    }
                    return;
                }
            }

            try {
                Thread.sleep(TASK_WATCH_INTERVAL_MILLIS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        synchronized (System.out) {
            System.out.println();
            lastProgressLineLength = 0;
        }
        printTaskDetails(task);
    }

    //在同一控制台行刷新进度。Windows 控制台行宽较窄时，过长文本会自动换行，后续 \r 只能回到换行后的物理行行首。
    //所以这里先按终端宽度截断，再用空格覆盖上一轮残留字符，避免动态进度不断追加到下一行。
    private void printProgressLine(String line)
    {
        String printableLine = fitProgressLineToTerminal(line);
        int printableWidth = displayWidth(printableLine);
        int clearLength = Math.max(0, lastProgressLineLength - printableWidth);
        System.out.print("\r" + printableLine + " ".repeat(clearLength) + "\r" + printableLine);
        System.out.flush();
        lastProgressLineLength = printableWidth;
    }

    private String fitProgressLineToTerminal(String line)
    {
        int maxColumns = Math.max(20, terminalColumns() - 1);
        if (displayWidth(line) <= maxColumns) {
            return line;
        }
        return truncateToDisplayWidth(line, maxColumns - 3) + "...";
    }

    private String truncateToDisplayWidth(String value, int maxWidth)
    {
        if (maxWidth <= 0) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        int width = 0;
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            int charWidth = displayWidth(codePoint);
            if (width + charWidth > maxWidth) {
                break;
            }
            result.appendCodePoint(codePoint);
            width += charWidth;
            offset += Character.charCount(codePoint);
        }
        return result.toString();
    }

    private int displayWidth(String value)
    {
        int width = 0;
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            width += displayWidth(codePoint);
            offset += Character.charCount(codePoint);
        }
        return width;
    }

    private int displayWidth(int codePoint)
    {
        if (Character.isISOControl(codePoint)) {
            return 0;
        }
        Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
        return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HANGUL ? 2 : 1;
    }

    private int terminalColumns()
    {
        String columns = System.getenv("COLUMNS");
        if (columns != null && !columns.isBlank()) {
            try {
                int parsedColumns = Integer.parseInt(columns.trim());
                if (parsedColumns > 0) {
                    return parsedColumns;
                }
            } catch (NumberFormatException ignored) {
                //使用平台默认宽度
            }
        }
        return isWindows() ? WINDOWS_DEFAULT_TERMINAL_COLUMNS : DEFAULT_TERMINAL_COLUMNS;
    }

    private boolean isWindows()
    {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    //格式化输出传输任务的进度
    private String formatProgressLine(TransferTask task)
    {
        double progressPercent = task.getProgress() * 100D;
        int filledWidth = (int) Math.round(Math.min(100D, Math.max(0D, progressPercent)) / 100D * PROGRESS_BAR_WIDTH);
        String bar = "#".repeat(filledWidth) + "-".repeat(PROGRESS_BAR_WIDTH - filledWidth);
        return String.format(
                messages.text(ConsoleMessages.Key.PROGRESS_LINE),
                bar,
                progressPercent,
                task.getStatus(),
                task.getAverageSpeedMegabytesPerSecond(),
                messages.label("bytes"),
                task.getTransferredBytes(),
                task.getTotalBytes(),
                messages.label("blocks"),
                task.getTransferredBlocks(),
                task.getTotalBlocks(),
                task.getMessage()
        );
    }

    //判断传输任务是否完成
    private boolean isTerminal(TransferStatus status)
    {
        return status != null && status.isTerminal();
    }

    //打印任务详情
    private void printTaskDetails(TransferTask task)
    {
        printLabelValue("taskId", task.getTaskId());
        printLabelValue("transferId", task.getTransferId());
        printLabelValue("direction", task.getDirection());
        printLabelValue("transportMode", task.getTransportMode());
        printLabelValue("status", task.getStatus());
        printLabelValue("fileName", task.getFileName());
        printLabelValue("localPath", task.getLocalPath());
        printLabelValue("peerDeviceId", task.getPeerDeviceId());
        printLabelValue("bytes", task.getTransferredBytes() + "/" + task.getTotalBytes());
        printLabelValue("blocks", task.getTransferredBlocks() + "/" + task.getTotalBlocks());
        System.out.printf("%s: %.2f%%%n", messages.label("progress"), task.getProgress() * 100D);
        System.out.printf("%s: %.2f mb/s%n", messages.label("speed"), task.getAverageSpeedMegabytesPerSecond());
        printLabelValue("createdAt", task.getCreatedAt());
        printLabelValue("transferStartedAt", task.getTransferStartedAt());
        printLabelValue("message", task.getMessage());
    }

    //处理打开文件位置的函数
    private void openReceivedFile(List<String> args) throws IOException
    {
        if (args.size() < 2) {
            System.out.println(messages.usage("open-received <taskId|transferId|\"fileName\">"));
            return;
        }

        String target = args.get(1);
        TransferTask task = resolveReceivedFileTask(target);
        if (task == null) {
            System.out.println(messages.format(ConsoleMessages.Key.RECEIVED_FILE_NOT_FOUND, target));
            return;
        }
        if (task.getLocalPath() == null || task.getLocalPath().isBlank()) {
            System.out.println(messages.format(ConsoleMessages.Key.RECEIVED_PATH_EMPTY, task.getTaskId()));
            return;
        }

        Path filePath = Path.of(task.getLocalPath()).toAbsolutePath().normalize();
        if (!Files.exists(filePath)) {
            System.out.println(messages.format(ConsoleMessages.Key.RECEIVED_PATH_MISSING, filePath));
            return;
        }

        revealInFileManager(filePath);
        System.out.println(messages.format(ConsoleMessages.Key.OPENED_FILE_LOCATION, filePath));
    }

    private TransferTask resolveReceivedFileTask(String target)
    {
        TransferTask task = transferTaskRegistry.findByTaskId(target)
                .or(() -> transferTaskRegistry.findByTransferId(target))
                .orElse(null);
        if (task != null) {
            if (task.getDirection() != TransferDirection.RECEIVE) {
                throw new IllegalArgumentException(messages.format(ConsoleMessages.Key.NOT_RECEIVED_FILE_TASK, target));
            }
            return task;
        }

        List<TransferTask> matches = transferTaskRegistry.allTasks().stream()
                .filter(candidate -> candidate.getDirection() == TransferDirection.RECEIVE)
                .filter(candidate -> fileNameMatches(candidate, target))
                .toList();
        if (matches.isEmpty()) {
            return null;
        }
        if (matches.size() > 1) {
            System.out.println(messages.text(ConsoleMessages.Key.MULTIPLE_RECEIVED_MATCHED));
            for (TransferTask match : matches) {
                System.out.printf(
                        "  %s=%s | %s=%s | %s=%s | %s=%s%n",
                        messages.label("taskId"),
                        match.getTaskId(),
                        messages.label("transferId"),
                        match.getTransferId(),
                        messages.label("fileName"),
                        match.getFileName(),
                        messages.label("localPath"),
                        match.getLocalPath()
                );
            }
            return null;
        }
        return matches.get(0);
    }

    private boolean fileNameMatches(TransferTask task, String target)
    {
        if (task.getLocalPath() == null || task.getLocalPath().isBlank()) {
            return target.equals(task.getFileName()) || target.equalsIgnoreCase(task.getFileName());
        }
        String localFileName = Path.of(task.getLocalPath()).getFileName().toString();
        return target.equals(task.getFileName())
                || target.equalsIgnoreCase(task.getFileName())
                || target.equals(localFileName)
                || target.equalsIgnoreCase(localFileName);
    }

    private void revealInFileManager(Path filePath) throws IOException
    {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        ProcessBuilder processBuilder;
        if (osName.contains("mac")) {
            processBuilder = new ProcessBuilder("open", "-R", filePath.toString());
        } else if (osName.contains("win")) {
            processBuilder = new ProcessBuilder("explorer.exe", "/select," + filePath.toString());
        } else {
            Path parent = filePath.getParent();
            processBuilder = new ProcessBuilder("xdg-open", parent == null ? filePath.toString() : parent.toString());
        }
        processBuilder.start();
    }

    private void printPublicKey()//打印当前的公钥
    {
        if (!ensureKeyPresent()) {//检查当前是否有密钥
            return;
        }
        System.out.println(clientConnectionManager.getLocalPublicKey());
    }

    private void printPublicKeyFingerprint(List<String> args) throws Exception  //处理计算公钥指纹的指令
    {
        if (args.size() >= 2) { //输入公钥的情况
            System.out.println(publicKeyPayloadService.accountIdForPublicKey(joinArguments(args, 1)));
            return;
        }
        if (!ensureLocalPublicKeyPresent()) {   //当前是否密钥文件
            System.out.println(messages.text(ConsoleMessages.Key.NO_LOCAL_PUBLIC_KEY));
            return;
        }
        System.out.println(cryptoSupport.publicKeyFingerprint());//输出当前公钥的指纹
    }

    private void printKeyInfo() throws Exception    //打印crypto-service返回的密钥状态
    {
        printMap(cryptoSupport.keyStatus());
    }

    private void generateKey() throws Exception //生成新的一对密钥
    {
        Map<String, Object> result = clientStartupCoordinator.generateStartupKeyAndContinue();
        Object keyResult = result.get("keyResult");
        if (keyResult instanceof Map<?, ?> keyMap) {
            printAnyMap(keyMap);
        }
    }

    private void deleteKey() throws Exception   //删除当前的密钥
    {
        printMap(cryptoSupport.deleteKeyPair());
    }

    private void exportPrivateKey() throws Exception
    {
        if (isKeyMissing(cryptoSupport.keyStatus())) {
            System.out.println(messages.text(ConsoleMessages.Key.NO_LOCAL_KEY_PAIR));
            return;
        }
        PrivateKeyArtifactService.ExportedPrivateKey exported = privateKeyArtifactService.exportPrivateKey();
        printQrArtifact(exported.artifact(), "qrText: " + exported.qrText());
        System.out.println(messages.text(ConsoleMessages.Key.PRIVATE_KEY_EXPORT_READY));
    }

    private void exportPublicKey() throws Exception
    {
        if (!ensureLocalPublicKeyPresent()) {
            System.out.println(messages.text(ConsoleMessages.Key.NO_LOCAL_PUBLIC_KEY));
            return;
        }
        PublicKeyPayloadService.ExportedPublicKey exported = publicKeyPayloadService.exportPublicKey();
        System.out.println("publicKey: " + exported.publicKey());
        System.out.println("qrText: " + exported.qrText());
        System.out.println(messages.format(ConsoleMessages.Key.QR_PNG, exported.artifact().getPngPath()));
        System.out.println("Public key text file: " + exported.artifact().getFst1Path());
        System.out.println(messages.format(ConsoleMessages.Key.QR_ASCII, exported.artifact().getAsciiPath()));
    }

    //--------------------------导入密钥的三种方式------------------------------//

    private void importPrivateKey(List<String> args) throws Exception      //手动输入的方式
    {
        if (args.size() < 2) {
            System.out.println(messages.usage("import-private-key <privateKeyBase64OrPem|path|pngPath>"));
            return;
        }
        privateKeyArtifactService.importPrivateKey(joinArguments(args, 1, args.size()));
        clientStartupCoordinator.markKeyAvailableAndContinueAutoConnect();
        System.out.println(messages.format(ConsoleMessages.Key.PRIVATE_KEY_IMPORTED, cryptoSupport.publicKeyFingerprint()));
        System.out.println(messages.text(ConsoleMessages.Key.AUTO_CONNECT_CONTINUE));
    }

    private void importPrivateKeyFile(List<String> args) throws Exception   //导入密钥文件的方式
    {
        if (args.size() < 2) {
            System.out.println(messages.usage("import-private-key-file <path|pngPath>"));
            return;
        }
        privateKeyArtifactService.importPrivateKey(PathInputNormalizer.toPath(joinArguments(args, 1, args.size())));//对于导入私钥文件的路径进行规格化操作
        clientStartupCoordinator.markKeyAvailableAndContinueAutoConnect();
        System.out.println(messages.format(ConsoleMessages.Key.PRIVATE_KEY_IMPORTED, cryptoSupport.publicKeyFingerprint()));
        System.out.println(messages.text(ConsoleMessages.Key.AUTO_CONNECT_CONTINUE));
    }

    private void importPrivateKeyPaste(BufferedReader reader) throws Exception  //通过负责粘贴的方式输入密钥
    {
        System.out.println(messages.text(ConsoleMessages.Key.PASTE_PRIVATE_KEY));
        StringBuilder keyText = new StringBuilder();
        while (true) {
            String line = reader.readLine();
            if (line == null) {
                handleConsoleInputClosed();
                return;
            }
            if (".".equals(line.trim())) {
                break;
            }
            keyText.append(line).append('\n');
        }
        privateKeyArtifactService.importPrivateKey(keyText.toString());
        clientStartupCoordinator.markKeyAvailableAndContinueAutoConnect();
        System.out.println(messages.format(ConsoleMessages.Key.PRIVATE_KEY_IMPORTED, cryptoSupport.publicKeyFingerprint()));
        System.out.println(messages.text(ConsoleMessages.Key.AUTO_CONNECT_CONTINUE));
    }

    //--------------------------导入密钥的三种方式------------------------------//

    private void exit() //退出程序的指令；退出整个TCP模块！！！；退出整个Spring应用
    {
        applicationShutdownService.requestShutdown();
    }

    void handleConsoleInputClosed()
    {
        applicationShutdownService.requestShutdown();
    }

    private boolean isApplicationActive()
    {
        return applicationShutdownService.isApplicationActive();
    }

    private void printMap(Map<String, ?> map)
    {
        for (Map.Entry<String, ?> entry : map.entrySet()) {
            printLabelValue(entry.getKey(), entry.getValue());
        }
    }

    private void printAnyMap(Map<?, ?> map)
    {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            printLabelValue(String.valueOf(entry.getKey()), entry.getValue());
        }
    }

    private void printLabelValue(String label, Object value)
    {
        System.out.println(messages.label(label) + ": " + value);
    }

    private void remindIfKeyMissing()//如果当前没有有效的密钥就提醒用户
    {
        try {
            if (isKeyMissing(cryptoSupport.keyStatus())) {
                printMissingKeyReminder();
            }
        } catch (Exception ex) {
            System.out.println(messages.format(ConsoleMessages.Key.UNABLE_CHECK_KEY_STATUS, ex.getMessage()));
            System.out.println(messages.text(ConsoleMessages.Key.RUN_KEY_INFO_AFTER_CRYPTO));
        }
    }

    private boolean ensureKeyPresent()//检查当前是否有有效的密钥，有返回true,无返回false
    {
        try {
            if (!isKeyMissing(cryptoSupport.keyStatus())) {
                return true;
            }
            printMissingKeyReminder();
            return false;
        } catch (Exception ex) {
            System.out.println(messages.format(ConsoleMessages.Key.UNABLE_CHECK_KEY_STATUS, ex.getMessage()));
            System.out.println(messages.text(ConsoleMessages.Key.RUN_KEY_INFO_AFTER_CRYPTO));
            return false;
        }
    }

    private boolean ensureLocalPublicKeyPresent()
    {
        try {
            return isTruthy(cryptoSupport.keyStatus().get("hasPublicKey"));//检查当前是否有密钥
        } catch (Exception ex) {
            System.out.println(messages.format(ConsoleMessages.Key.UNABLE_CHECK_KEY_STATUS, ex.getMessage()));
            System.out.println(messages.text(ConsoleMessages.Key.RUN_KEY_INFO_AFTER_CRYPTO));
            return false;
        }
    }

    private boolean isKeyMissing(Map<String, ?> keyStatus)  //判断是否缺少密钥
    {
        return !isTruthy(keyStatus.get("hasPrivateKey"));
    }

    private boolean isTruthy(Object value)  //只要公钥和私钥中的任意一个不存在就认为当前密钥不可用(有待修改)
    {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return "true".equalsIgnoreCase(String.valueOf(value));
    }

    //解析Contact-1这个参数中的数字索引
    private int parseContactIndexArgument(String value)
    {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("contact index is required");
        }
        String normalized = value.startsWith("contact-") ? value.substring("contact-".length()) : value;
        try {
            int contactIndex = Integer.parseInt(normalized);
            if (contactIndex <= 0) {
                throw new IllegalArgumentException("contact index must be positive: " + value);
            }
            return contactIndex;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid contact token: " + value);
        }
    }

    private String joinArguments(List<String> args, int startIndex)
    {
        return joinArguments(args, startIndex, args.size());
    }

    //将args里指定范围的参数，重新用空格拼成一个字符串
    private String joinArguments(List<String> args, int startIndex, int endExclusive)
    {
        StringBuilder builder = new StringBuilder();
        for (int i = startIndex; i < endExclusive; i++) {
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(args.get(i));
        }
        return builder.toString();
    }

    //统一显示可能为空的字符串
    private String displayNullable(String value)
    {
        return value == null || value.isBlank() ? "-" : value;
    }

    //将过长的字符串缩短后显示
    private String abbreviate(String value, int maxLength)
    {
        if (value == null || value.isBlank()) {
            return "-";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        if (maxLength <= 3) {
            return value.substring(0, maxLength);
        }
        return value.substring(0, maxLength - 3) + "...";
    }

    //打印密钥缺失提醒
    private void printMissingKeyReminder()
    {
        System.out.println(messages.text(ConsoleMessages.Key.NO_LOCAL_KEY_PAIR));
        System.out.println(messages.text(ConsoleMessages.Key.MISSING_KEY_ACTION));
    }

    private List<String> parseArguments(String line)//参数解析函数
    {
        List<String> args = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        char quoteChar = 0;

        //将用户输入的命令解析成为String数组
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);

            if (quoted) {
                if (ch == quoteChar) {
                    quoted = false;
                    quoteChar = 0;
                    continue;
                }
                if (ch == '\\' && i + 1 < line.length() && line.charAt(i + 1) == quoteChar) {
                    current.append(line.charAt(++i));
                    continue;
                }
                current.append(ch);
                continue;
            }

            if (ch == '"' || ch == '\'') {
                quoted = true;
                quoteChar = ch;
                continue;
            }
            if (ch == '\\' && i + 1 < line.length()) {
                char next = line.charAt(i + 1);
                if (Character.isWhitespace(next) || next == '"' || next == '\'') {
                    current.append(next);
                    i++;
                    continue;
                }
                current.append(ch);
                continue;
            }
            if (Character.isWhitespace(ch) && !quoted) {
                addArgument(args, current);
                continue;
            }
            current.append(ch);
        }

        addArgument(args, current);
        return args;
    }

    //把当前正在拼接的一个命令参数，加入到参数列表args里，然后清空StringBuilder,准备解析下一个参数
    private void addArgument(List<String> args, StringBuilder current)
    {
        if (!current.isEmpty()) {
            args.add(current.toString());
            current.setLength(0);
        }
    }

}
