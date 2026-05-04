package com.console;

import com.client.ClientConnectionManager;
import com.common.config.ClientProperties;
import com.common.protocol.file.IncomingTransferRequestPacket;
import com.crypto.CryptoSupport;
import com.service.ClientTransferService;
import com.service.PushNotificationService;
import com.service.TransferTaskRegistry;
import com.session.TransferTask;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
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
 *
 * */

@Component
public class ConsoleCommandRunner
{
    private final ClientConnectionManager clientConnectionManager;
    private final ClientTransferService clientTransferService;
    private final ClientProperties clientProperties;
    private final CryptoSupport cryptoSupport;
    private final TransferTaskRegistry transferTaskRegistry;
    private final ConfigurableApplicationContext applicationContext;
    private final PushNotificationService pushNotificationService;
    private Runnable notificationSubscription;

    public ConsoleCommandRunner(
            ClientConnectionManager clientConnectionManager,
            ClientTransferService clientTransferService,
            ClientProperties clientProperties,
            CryptoSupport cryptoSupport,
            TransferTaskRegistry transferTaskRegistry,
            ConfigurableApplicationContext applicationContext,
            PushNotificationService pushNotificationService
    )
    {
        this.clientConnectionManager = clientConnectionManager;
        this.clientTransferService = clientTransferService;
        this.clientProperties = clientProperties;
        this.cryptoSupport = cryptoSupport;
        this.transferTaskRegistry = transferTaskRegistry;
        this.applicationContext = applicationContext;
        this.pushNotificationService = pushNotificationService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startConsole()//startConsole监听ApplicationReadyEvent
    {
        notificationSubscription=pushNotificationService.subscribeLocal(this::handleNotification);
        Thread consoleThread = new Thread(this::runCommandLoop, "console-command-runner");//console-command-runner守护线程
        consoleThread.setDaemon(true);
        consoleThread.start();
    }

    @PreDestroy
    public void stopConsole()
    {
        if(notificationSubscription!=null)
        {
            notificationSubscription.run();
            notificationSubscription=null;
        }
    }

    private void runCommandLoop()//打印欢迎消息
    {
        printWelcome();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            while (applicationContext.isActive()) {
                System.out.print("fst> ");
                String line = reader.readLine();
                if (line == null) {
                    return;
                }
                handleCommand(reader, line.trim());
            }
        } catch (IOException ex) {
            System.out.println("Console stopped: " + ex.getMessage());
        }
    }

    private void handleCommand(BufferedReader reader, String line)
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
                case "help" -> printHelp();                     //打印所有可用命令
                case "status" -> printStatus();                 //把当前客户端连接状态逐项打印出来
                case "connect" -> connect(args);                //用于连接服务器并完成认证，connect [host] [port]
                case "disconnect" -> disconnect();              //断开当前连接
                case "send" -> sendFile(args);                  //发送文件，send <filePath> <targetAccountId>     //filePath可以是绝对路径也可以是相对路径（相对程序运行的位置），accountId就是公钥指纹(64 位)
                case "incoming" -> printIncomingRequests();     //列出当前待处理的文件接收请求
                case "accept" -> acceptIncomingRequest(args);   //接收指定的incoming transfer， accept <transferId>
                case "reject" -> rejectIncomingRequest(args);   //拒绝指定的incoming transfer， reject <transferId>
                case "tasks" -> printTasks();                   //列出所有传输任务
                case "task" -> printTask(args);                 //查看单个任务详情， task <taskId|transferId>    //参数可以是任务Id, 也可以是传输Id
                case "public-key" -> printPublicKey();          //查看和导入密钥,打印当前客户端的本地公钥
                case "key-info" -> printKeyInfo();              //打印本地的密钥状态
                case "import-private-key" -> importPrivateKey(args);                //以文本的方式导入私钥，import-private-key <keyText>
                case "import-private-key-file" -> importPrivateKeyFile(args);       //从文件导入私钥，import-private-key-file <path>
                case "import-private-key-paste" -> importPrivateKeyPaste(reader);   //进入多行粘贴模式，用户可以粘贴多行私钥内容，最后一行只输入一个'.'标识结束， import-private-key-paste
                case "exit", "quit" -> exit();                  //推出程序， exit或者quit
                default -> System.out.println("Unknown command: " + command + ". Type 'help' for commands.");
            }
        } catch (Exception ex) {
            System.out.println("Command failed: " + ex.getMessage());
        }
    }

    private void printWelcome()
    {
        System.out.println();
        System.out.println("File Security Transmission console is ready.");
        System.out.println("Type 'help' for commands.");
    }

    private void printHelp()
    {
        System.out.println("Commands:");
        System.out.println("  help                              Show this help");
        System.out.println("  status                            Show client connection status");
        System.out.println("  connect [host] [port]             Connect and authenticate with server");
        System.out.println("  disconnect                        Disconnect from server");
        System.out.println("  send <filePath> <targetAccountId> Send a file to target account");
        System.out.println("  incoming                          List incoming transfer requests");
        System.out.println("  accept <transferId>               Accept an incoming transfer on this device");
        System.out.println("  reject <transferId>               Reject and cancel an incoming transfer");
        System.out.println("  tasks                             List transfer tasks");
        System.out.println("  task <taskId|transferId>          Show one transfer task");
        System.out.println("  public-key                        Print local public key");
        System.out.println("  key-info                          Show local key paths and public key fingerprint");
        System.out.println("  import-private-key <keyText>      Import private key text from manual copy or QR scan");
        System.out.println("  import-private-key-file <path>    Import private key from a file");
        System.out.println("  import-private-key-paste          Paste a multi-line private key, then enter a single dot");
        System.out.println("  exit                              Stop application");
        System.out.println("Paths with spaces can be wrapped in double quotes.");
    }

    private void handleNotification(String type, Object payload)
    {
        if ("incoming-transfer-request".equals(type)) {
            printIncomingTransferNotification(payload);
        }
    }

    private void printIncomingTransferNotification(Object payload)
    {
        if (!(payload instanceof Map<?, ?> values)) {
            printConsoleNotice("New incoming transfer request. Run 'incoming' to list requests.");
            return;
        }

        printConsoleNotice(String.format(
                "New incoming transfer request: %s | sender=%s | file=%s | bytes=%s | blocks=%s%nUse 'accept %s' to receive it or 'reject %s' to cancel it.",
                values.get("transferId"),
                values.get("senderDeviceId"),
                values.get("fileName"),
                values.get("fileSize"),
                values.get("totalBlocks"),
                values.get("transferId"),
                values.get("transferId")
        ));
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

    private void connect(List<String> args) throws Exception
    {
        String host = args.size() >= 2 ? args.get(1) : clientProperties.getServerHost();
        int port = args.size() >= 3 ? Integer.parseInt(args.get(2)) : clientProperties.getServerPort();
        clientConnectionManager.connectAndAuthenticate(host, port)
                .get(clientProperties.getAuthTimeoutSeconds(), TimeUnit.SECONDS);
        System.out.println("Connected and authenticated: " + host + ":" + port);
    }

    private void disconnect()
    {
        clientConnectionManager.disconnect();
        System.out.println("Disconnected.");
    }

    private void sendFile(List<String> args)
    {
        if (args.size() < 3) {
            System.out.println("Usage: send <filePath> <targetAccountId>");
            return;
        }
        String taskId = clientTransferService.sendFile(Path.of(args.get(1)), args.get(2));
        System.out.println("Send task created: " + taskId);
    }

    private void printTasks()
    {
        List<TransferTask> tasks = transferTaskRegistry.allTasks();
        if (tasks.isEmpty()) {
            System.out.println("No transfer tasks.");
            return;
        }

        for (TransferTask task : tasks) {
            System.out.printf(
                    "%s | %s | %s | %.2f%% | %s | %s%n",
                    task.getTaskId(),
                    task.getDirection(),
                    task.getStatus(),
                    task.getProgress() * 100D,
                    task.getFileName(),
                    task.getMessage()
            );
        }
    }

    private void printIncomingRequests()
    {
        Map<String, IncomingTransferRequestPacket> requests = clientTransferService.pendingIncomingTransferRequests();
        if (requests.isEmpty()) {
            System.out.println("No incoming transfer requests.");
            return;
        }
        for (IncomingTransferRequestPacket request : requests.values()) {
            System.out.printf(
                    "%s | sender=%s | file=%s | bytes=%d | blocks=%d%n",
                    request.getTransferId(),
                    request.getSenderDeviceId(),
                    request.getFileName(),
                    request.getFileSize(),
                    request.getTotalBlocks()
            );
        }
    }

    private void acceptIncomingRequest(List<String> args)
    {
        if (args.size() < 2) {
            System.out.println("Usage: accept <transferId>");
            return;
        }
        clientTransferService.acceptIncomingTransfer(args.get(1));
        System.out.println("Accepted incoming transfer request: " + args.get(1));
    }

    private void rejectIncomingRequest(List<String> args)
    {
        if (args.size() < 2) {
            System.out.println("Usage: reject <transferId>");
            return;
        }
        clientTransferService.rejectIncomingTransfer(args.get(1));
        System.out.println("Rejected incoming transfer request: " + args.get(1));
    }

    private void printTask(List<String> args)
    {
        if (args.size() < 2) {
            System.out.println("Usage: task <taskId|transferId>");
            return;
        }

        String id = args.get(1);
        TransferTask task = transferTaskRegistry.findByTaskId(id)
                .or(() -> transferTaskRegistry.findByTransferId(id))
                .orElse(null);
        if (task == null) {
            System.out.println("Task not found: " + id);
            return;
        }

        System.out.println("taskId: " + task.getTaskId());
        System.out.println("transferId: " + task.getTransferId());
        System.out.println("direction: " + task.getDirection());
        System.out.println("status: " + task.getStatus());
        System.out.println("fileName: " + task.getFileName());
        System.out.println("localPath: " + task.getLocalPath());
        System.out.println("peerDeviceId: " + task.getPeerDeviceId());
        System.out.println("bytes: " + task.getTransferredBytes() + "/" + task.getTotalBytes());
        System.out.println("blocks: " + task.getTransferredBlocks() + "/" + task.getTotalBlocks());
        System.out.printf("progress: %.2f%%%n", task.getProgress() * 100D);
        System.out.println("createdAt: " + task.getCreatedAt());
        System.out.println("message: " + task.getMessage());
    }

    private void printPublicKey()
    {
        System.out.println(clientConnectionManager.getLocalPublicKey());
    }

    private void printKeyInfo() throws Exception
    {
        printMap(cryptoSupport.keyStatus());
    }

    private void importPrivateKey(List<String> args) throws Exception
    {
        if (args.size() < 2) {
            System.out.println("Usage: import-private-key <privateKeyBase64OrPem>");
            return;
        }
        cryptoSupport.importPrivateKeyText(args.get(1));
        System.out.println("Private key imported. Public key fingerprint: " + cryptoSupport.publicKeyFingerprint());
        System.out.println("Reconnect to register/update this public key on the server.");
    }

    private void importPrivateKeyFile(List<String> args) throws Exception
    {
        if (args.size() < 2) {
            System.out.println("Usage: import-private-key-file <path>");
            return;
        }
        cryptoSupport.importPrivateKeyFile(Path.of(args.get(1)));
        System.out.println("Private key imported. Public key fingerprint: " + cryptoSupport.publicKeyFingerprint());
        System.out.println("Reconnect to register/update this public key on the server.");
    }

    private void importPrivateKeyPaste(BufferedReader reader) throws Exception
    {
        System.out.println("Paste private key text. Enter a single dot on its own line to finish.");
        StringBuilder keyText = new StringBuilder();
        while (true) {
            String line = reader.readLine();
            if (line == null) {
                return;
            }
            if (".".equals(line.trim())) {
                break;
            }
            keyText.append(line).append('\n');
        }
        cryptoSupport.importPrivateKeyText(keyText.toString());
        System.out.println("Private key imported. Public key fingerprint: " + cryptoSupport.publicKeyFingerprint());
        System.out.println("Reconnect to register/update this public key on the server.");
    }

    private void exit()
    {
        System.out.println("Stopping application...");
        applicationContext.close();
    }

    private void printMap(Map<String, Object> map)
    {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            System.out.println(entry.getKey() + ": " + entry.getValue());
        }
    }

    private List<String> parseArguments(String line)
    {
        List<String> args = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        boolean escaping = false;

        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (escaping) {
                current.append(ch);
                escaping = false;
                continue;
            }
            if (ch == '\\') {
                escaping = true;
                continue;
            }
            if (ch == '"') {
                quoted = !quoted;
                continue;
            }
            if (Character.isWhitespace(ch) && !quoted) {
                addArgument(args, current);
                continue;
            }
            current.append(ch);
        }

        if (escaping) {
            current.append('\\');
        }
        addArgument(args, current);
        return args;
    }

    private void addArgument(List<String> args, StringBuilder current)
    {
        if (!current.isEmpty()) {
            args.add(current.toString());
            current.setLength(0);
        }
    }
}
