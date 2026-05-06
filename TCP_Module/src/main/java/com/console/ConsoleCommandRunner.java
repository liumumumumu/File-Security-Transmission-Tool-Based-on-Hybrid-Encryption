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
    private final ClientConnectionManager clientConnectionManager;//负责客户端连接服务器，认证，断开连接
    private final ClientTransferService clientTransferService;//负责收发文件，处理传输请求
    private final ClientProperties clientProperties;//负责读取客户端配置
    private final CryptoSupport cryptoSupport;//负责访问本地的加密服务，管理密钥，获取密钥状态，生成密钥，导入密钥
    private final TransferTaskRegistry transferTaskRegistry;//负责保持传输任务状态，用于对tasks, task的命令查询
    private final ConfigurableApplicationContext applicationContext;//负责控制Spring应用的生命周期
    private final PushNotificationService pushNotificationService;//监听本地通知
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
        remindIfKeyMissing();//检查当前是否有密钥
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            while (applicationContext.isActive()) {//进入大循环
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
                case "key-info" -> printKeyInfo();              //打印Python加密服务管理的密钥状态
                case "generate-key" -> generateKey();           //请求Python加密服务生成密钥
                case "delete-key" -> deleteKey();               //请求Python加密服务删除密钥
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

    private void printWelcome()//打印欢迎消息
    {
        System.out.println();
        System.out.println("File Security Transmission console is ready.");
        System.out.println("Type 'help' for commands.");
    }

    private void printHelp()//打印该控制台程序支持的所有命令
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
        System.out.println("  key-info                          Show crypto service key status");
        System.out.println("  generate-key                      Generate key pair in the crypto service");
        System.out.println("  delete-key                        Delete key pair from the crypto service");
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

    private void connect(List<String> args) throws Exception    //处理connect命令
    {
        if (!ensureKeyPresent()) {  //先检查是否有密钥
            return;
        }
        String host = args.size() >= 2 ? args.get(1) : clientProperties.getServerHost();//host参数
        int port = args.size() >= 3 ? Integer.parseInt(args.get(2)) : clientProperties.getServerPort();//port参数
        clientConnectionManager.connectAndAuthenticate(host, port)
                .get(clientProperties.getAuthTimeoutSeconds(), TimeUnit.SECONDS);//通过该方法连接服务器并完成身份认证
        System.out.println("Connected and authenticated: " + host + ":" + port);
    }

    private void disconnect()
    {
        clientConnectionManager.disconnect();
        System.out.println("Disconnected.");
    }

    private void sendFile(List<String> args)    //处理发送文件的命令
    {
        if (!ensureKeyPresent()) {  //检查当前是否有密钥
            return;
        }
        if (args.size() < 3) {  //校验参数
            System.out.println("Usage: send <filePath> <targetAccountId>");
            return;
        }
        //第一个参数是文件路径(支持绝对路径和相对路径(相对可执行程序的路径))，第二个参数是目标账户的公钥指纹
        String taskId = clientTransferService.sendFile(Path.of(args.get(1)), args.get(2));//处理发送文件的函数
        System.out.println("Send task created: " + taskId);
    }

    private void printTasks()//任务查询命令，打印所有任务
    {
        List<TransferTask> tasks = transferTaskRegistry.allTasks();
        if (tasks.isEmpty()) {
            System.out.println("No transfer tasks.");
            return;
        }
        System.out.println("taskId | direction | status | progress | fileName | message");
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

    private void printIncomingRequests()//打印所有待处理的接收请求
    {
        Map<String, IncomingTransferRequestPacket> requests = clientTransferService.pendingIncomingTransferRequests();
        if (requests.isEmpty()) {
            System.out.println("No incoming transfer requests.");
            return;
        }
        System.out.println("transferId | sender | file | bytes | blocks");
        for (IncomingTransferRequestPacket request : requests.values()) {
            System.out.printf(
                    "%s | %s | %s | %d | %d%n",
                    request.getTransferId(),
                    request.getSenderDeviceId(),
                    request.getFileName(),
                    request.getFileSize(),
                    request.getTotalBlocks()
            );
        }
    }

    private void acceptIncomingRequest(List<String> args)//接受某个传输请求
    {
        if (args.size() < 2) {
            System.out.println("Usage: accept <transferId>");
            return;
        }
        clientTransferService.acceptIncomingTransfer(args.get(1));//参数是任务Id
        System.out.println("Accepted incoming transfer request: " + args.get(1));
    }

    private void rejectIncomingRequest(List<String> args)//拒绝某个传输请求
    {
        if (args.size() < 2) {
            System.out.println("Usage: reject <transferId>");
            return;
        }
        clientTransferService.rejectIncomingTransfer(args.get(1));//参数是任务Id
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

    private void printPublicKey()//打印当前的公钥
    {
        if (!ensureKeyPresent()) {//检查当前是否有密钥
            return;
        }
        System.out.println(clientConnectionManager.getLocalPublicKey());
    }

    private void printKeyInfo() throws Exception    //打印crypto-service返回的密钥状态
    {
        printMap(cryptoSupport.keyStatus());
    }

    private void generateKey() throws Exception //生成新的一对密钥
    {
        printMap(cryptoSupport.generateKeyPair());
    }

    private void deleteKey() throws Exception   //删除当前的密钥
    {
        printMap(cryptoSupport.deleteKeyPair());
    }

    //--------------------------导入密钥的三种方式------------------------------//

    private void importPrivateKey(List<String> args) throws Exception      //手动输入的方式
    {
        if (args.size() < 2) {
            System.out.println("Usage: import-private-key <privateKeyBase64OrPem>");
            return;
        }
        cryptoSupport.importPrivateKeyText(args.get(1));
        System.out.println("Private key imported. Public key fingerprint: " + cryptoSupport.publicKeyFingerprint());
        System.out.println("Reconnect to register/update this public key on the server.");
    }

    private void importPrivateKeyFile(List<String> args) throws Exception   //导入密钥文件的方式
    {
        if (args.size() < 2) {
            System.out.println("Usage: import-private-key-file <path>");
            return;
        }
        cryptoSupport.importPrivateKeyFile(Path.of(args.get(1)));
        System.out.println("Private key imported. Public key fingerprint: " + cryptoSupport.publicKeyFingerprint());
        System.out.println("Reconnect to register/update this public key on the server.");
    }

    private void importPrivateKeyPaste(BufferedReader reader) throws Exception  //通过负责粘贴的方式输入密钥
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

        //--------------------------导入密钥的三种方式------------------------------//

    private void exit() //退出程序的指令；退出整个TCP模块！！！；退出整个Spring应用
    {
        System.out.println("Stopping application...");
        applicationContext.close();
    }

    private void printMap(Map<String, ?> map)
    {
        for (Map.Entry<String, ?> entry : map.entrySet()) {
            System.out.println(entry.getKey() + ": " + entry.getValue());
        }
    }

    private void remindIfKeyMissing()//如果当前没有有效的密钥就提醒用户
    {
        try {
            if (isKeyMissing(cryptoSupport.keyStatus())) {
                printMissingKeyReminder();
            }
        } catch (Exception ex) {
            System.out.println("Unable to check key status: " + ex.getMessage());
            System.out.println("Run 'key-info' after confirming the crypto service is running.");
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
            System.out.println("Unable to check key status: " + ex.getMessage());
            System.out.println("Run 'key-info' after confirming the crypto service is running.");
            return false;
        }
    }

    private boolean isKeyMissing(Map<String, ?> keyStatus)  //判断是否缺少密钥
    {
        return !isTruthy(keyStatus.get("hasPrivateKey")) || !isTruthy(keyStatus.get("hasPublicKey"));
    }

    private boolean isTruthy(Object value)  //只要公钥和私钥中的任意一个不存在就认为当前密钥不可用(有待修改)
    {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return "true".equalsIgnoreCase(String.valueOf(value));
    }

    private void printMissingKeyReminder()
    {
        System.out.println("No local key pair is available.");
        System.out.println("Run 'generate-key' to create one, or use 'import-private-key-file <path>' / 'import-private-key-paste' to import an existing private key.");
    }

    private List<String> parseArguments(String line)//参数解析函数
    {
        List<String> args = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        boolean escaping = false;

        //将用户输入的命令解析成为String数组
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
