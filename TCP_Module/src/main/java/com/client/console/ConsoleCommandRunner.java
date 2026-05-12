package com.client.console;

import com.client.ClientConnectionManager;
import com.common.config.ClientProperties;
import com.common.protocol.file.IncomingTransferRequestPacket;
import com.common.protocol.searchUser.OnlineUserSearchResultPacket;
import com.crypto.CryptoSupport;
import com.client.service.ClientTransferService;
import com.client.service.LocalContactBookService;
import com.common.service.PushNotificationService;
import com.common.util.PathInputNormalizer;
import com.client.service.TransferTaskRegistry;
import com.persistence.local.model.contactsRecord.BlacklistRecord;
import com.persistence.local.model.contactsRecord.ContactRecord;
import com.session.TransferStatus;
import com.session.TransferTask;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
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
 * 15. exit / quit：退出客户端程序
 *
 * */

@Component
public class ConsoleCommandRunner
{
    private static final int PROGRESS_BAR_WIDTH = 30;
    private static final long TASK_WATCH_INTERVAL_MILLIS = 1000L;

    private final ClientConnectionManager clientConnectionManager;//负责客户端连接服务器，认证，断开连接
    private final ClientTransferService clientTransferService;//负责收发文件，处理传输请求
    private final ClientProperties clientProperties;//负责读取客户端配置
    private final CryptoSupport cryptoSupport;//负责访问本地的加密服务，管理密钥，获取密钥状态，生成密钥，导入密钥
    private final TransferTaskRegistry transferTaskRegistry;//负责保持传输任务状态，用于对tasks, task的命令查询
    private final ConfigurableApplicationContext applicationContext;//负责控制Spring应用的生命周期
    private final PushNotificationService pushNotificationService;//监听本地通知
    private final LocalContactBookService localContactBookService;//负责本地联系人和黑名单
    private Runnable notificationSubscription;

    public ConsoleCommandRunner(
            ClientConnectionManager clientConnectionManager,
            ClientTransferService clientTransferService,
            ClientProperties clientProperties,
            CryptoSupport cryptoSupport,
            TransferTaskRegistry transferTaskRegistry,
            ConfigurableApplicationContext applicationContext,
            PushNotificationService pushNotificationService,
            LocalContactBookService localContactBookService
    )
    {
        this.clientConnectionManager = clientConnectionManager;
        this.clientTransferService = clientTransferService;
        this.clientProperties = clientProperties;
        this.cryptoSupport = cryptoSupport;
        this.transferTaskRegistry = transferTaskRegistry;
        this.applicationContext = applicationContext;
        this.pushNotificationService = pushNotificationService;
        this.localContactBookService = localContactBookService;
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
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, commandInputCharset()))) {
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
                case "status" -> printStatus();                 //把当前客户端连接状态逐项打印出来
                case "connect" -> connect(args);                //用于连接服务器并完成认证，connect [host] [port]
                case "disconnect" -> disconnect();              //断开当前连接
                case "send" -> sendFile(args);                  //发送文件，send <filePath> <targetAccountId>     //filePath可以是绝对路径也可以是相对路径（相对程序运行的位置），accountId就是公钥指纹(64 位)
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
                case "public-key" -> printPublicKey();          //查看和导入密钥,打印当前客户端的本地公钥
                case "public-key-fingerprint", "accountid", "account-id" -> printPublicKeyFingerprint(args);  //计算指定公钥或本地公钥的指纹;因为公钥指纹就是本系统的accountId,故也兼容accountId指令
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
        System.out.println("  cancel <taskId|transferId>        Cancel an active transfer task");
        System.out.println("  retransmit <taskId|transferId>    Request retransmission from the receiver progress");
        System.out.println("  retransmit-accept <transferId>    Accept a receiver retransmission request");
        System.out.println("  retransmit-reject <transferId>    Reject a receiver retransmission request");
        System.out.println("  contacts                          List local contacts");
        System.out.println("  contact-add <accountId> [alias] Add or update a local contact");
        System.out.println("  contact-remove <contact-N|N>      Remove a local contact");
        System.out.println("  contact-show <contact-N|N>        Show one local contact");
        System.out.println("  blacklist                         List blacklist records");
        System.out.println("  blacklist-add <accountId> [reason] Add or update a blacklist record");
        System.out.println("  blacklist-add-contact <contact-N|N> [reason] Add a contact to blacklist");
        System.out.println("  blacklist-remove <accountId>      Remove a blacklist record");
        System.out.println("  search-user <accountId>           Search whether an account is online");
        System.out.println("  search-user-add <accountId> [alias] Search online user and add to contacts");
        System.out.println("  tasks                             List transfer tasks");
        System.out.println("  task <taskId|transferId> [--once] Watch one transfer task progress. Press Enter or q then Enter to stop watching.");
        System.out.println("  public-key                        Print local public key");
        System.out.println("  public-key-fingerprint [publicKey] Print fingerprint for the given public key, or local public key when omitted");
        System.out.println("  account-id [publicKey]             Alias of public-key-fingerprint");//alias别名
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
        if ("transfer-retransmit-request-received".equals(type)) {
            printRetransmitRequestNotification(payload);
        }
    }

    private void printIncomingTransferNotification(Object payload)
    {
        if (!(notificationPayload(payload) instanceof Map<?, ?> values)) {
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

    private void printRetransmitRequestNotification(Object payload)
    {
        if (!(notificationPayload(payload) instanceof Map<?, ?> values)) {
            printConsoleNotice("Retransmission request received. Use 'retransmit-accept <transferId>' or 'retransmit-reject <transferId>'.");
            return;
        }

        printConsoleNotice(String.format(
                "Retransmission request received: %s | file=%s | from block=%s | reason=%s%nUse 'retransmit-accept %s' to continue, or 'retransmit-reject %s' to refuse.",
                values.get("transferId"),
                values.get("fileName"),
                values.get("startBlockId"),
                values.get("reason"),
                values.get("transferId"),
                values.get("transferId")
        ));
    }

    private Object notificationPayload(Object payload)
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
        //最后一个参数是目标账户的公钥指纹，中间参数拼回文件路径，兼容未加引号但包含空格的路径
        String filePath = joinArguments(args, 1, args.size() - 1);
        String targetAccountId = localContactBookService.resolveAccountId(args.get(args.size() - 1));
        String taskId = clientTransferService.sendFile(PathInputNormalizer.toPath(filePath), targetAccountId);//处理发送文件的函数，对于文件路径进行规格化操作
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

    //处理取消传输任务
    private void cancelTransfer(List<String> args)
    {
        if (args.size() < 2) {
            System.out.println("Usage: cancel <taskId|transferId>");
            return;
        }
        clientTransferService.cancelTransfer(args.get(1));
        System.out.println("Transfer canceled: " + args.get(1));
    }

    private void requestRetransmission(List<String> args)
    {
        if (args.size() < 2) {
            System.out.println("Usage: retransmit <taskId|transferId>");
            return;
        }
        clientTransferService.requestRetransmission(args.get(1));
        System.out.println("Retransmission requested: " + args.get(1));
    }

    private void acceptRetransmission(List<String> args)
    {
        if (args.size() < 2) {
            System.out.println("Usage: retransmit-accept <transferId>");
            return;
        }
        clientTransferService.acceptRetransmission(args.get(1));
        System.out.println("Retransmission accepted: " + args.get(1));
    }

    private void rejectRetransmission(List<String> args)
    {
        if (args.size() < 2) {
            System.out.println("Usage: retransmit-reject <transferId>");
            return;
        }
        clientTransferService.rejectRetransmission(args.get(1));
        System.out.println("Retransmission rejected: " + args.get(1));
    }

    private void printContacts()
    {
        List<ContactRecord> contacts = localContactBookService.listContacts();
        if (contacts.isEmpty()) {
            System.out.println("No contacts.");
            return;
        }

        System.out.println("contact | alias | accountId | publicKey");
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
            System.out.println("Usage: contact-add <accountId> [alias]");
            return;
        }

        String alias = args.size() >= 3 ? joinArguments(args, 2) : null;
        String publicKey = searchPublicKeyForContact(args.get(1));
        ContactRecord contact = localContactBookService.addContact(args.get(1), publicKey, alias);
        System.out.printf(
                "Contact saved: contact-%d | %s | %s | %s%n",
                contact.getContactIndex(),
                displayNullable(contact.getAlias()),
                contact.getAccountId(),
                abbreviate(contact.getPublicKey(), 32)
        );
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
            System.out.println("Online user not found. Contact publicKey will be empty.");
        } catch (Exception ex) {
            System.out.println("Unable to search publicKey from server. Contact publicKey will be empty: " + ex.getMessage());
        }
        return null;
    }

    private void removeContact(List<String> args)
    {
        if (args.size() < 2) {
            System.out.println("Usage: contact-remove <contact-N|N>");
            return;
        }

        int contactIndex = parseContactIndexArgument(args.get(1));
        localContactBookService.removeContactByIndex(contactIndex);
        System.out.println("Contact removed: contact-" + contactIndex);
    }

    private void showContact(List<String> args)
    {
        if (args.size() < 2) {
            System.out.println("Usage: contact-show <contact-N|N>");
            return;
        }

        int contactIndex = parseContactIndexArgument(args.get(1));
        ContactRecord contact = localContactBookService.findContactByIndex(contactIndex).orElse(null);
        if (contact == null) {
            System.out.println("Contact not found: contact-" + contactIndex);
            return;
        }

        System.out.println("contact: contact-" + contact.getContactIndex());
        System.out.println("alias: " + displayNullable(contact.getAlias()));
        System.out.println("accountId: " + contact.getAccountId());
        System.out.println("publicKey: " + contact.getPublicKey());
        System.out.println("createdAt: " + contact.getCreatedAt());
        System.out.println("updatedAt: " + contact.getUpdatedAt());
    }

    private void printBlacklist()
    {
        List<BlacklistRecord> records = localContactBookService.listBlacklist();
        if (records.isEmpty()) {
            System.out.println("No blacklist records.");
            return;
        }

        System.out.println("accountId | reason | publicKey | createdAt");
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
            System.out.println("Usage: blacklist-add <accountId> [reason]");
            return;
        }

        String reason = args.size() >= 3 ? joinArguments(args, 2) : null;
        BlacklistRecord record = localContactBookService.addBlacklist(args.get(1), null, reason);
        System.out.println("Blacklist record saved: " + record.getAccountId());
    }

    private void addBlacklistContact(List<String> args)
    {
        if (args.size() < 2) {
            System.out.println("Usage: blacklist-add-contact <contact-N|N> [reason]");
            return;
        }

        int contactIndex = parseContactIndexArgument(args.get(1));
        String reason = args.size() >= 3 ? joinArguments(args, 2) : null;
        BlacklistRecord record = localContactBookService.addBlacklistByContactIndex(contactIndex, reason);
        System.out.println("Blacklist record saved from contact-" + contactIndex + ": " + record.getAccountId());
    }

    private void removeBlacklist(List<String> args)
    {
        if (args.size() < 2) {
            System.out.println("Usage: blacklist-remove <accountId>");
            return;
        }

        localContactBookService.removeBlacklist(args.get(1));
        System.out.println("Blacklist record removed: " + args.get(1));
    }

    private void searchUser(List<String> args)
    {
        if (args.size() < 2) {
            System.out.println("Usage: search-user <accountId>");
            return;
        }

        OnlineUserSearchResultPacket result = clientTransferService.searchOnlineUser(args.get(1));
        printOnlineUserSearchResult(result);
    }

    private void searchUserAndAddContact(List<String> args)
    {
        if (args.size() < 2) {
            System.out.println("Usage: search-user-add <accountId> [alias]");
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
        System.out.printf(
                "Contact saved: contact-%d | %s | %s%n",
                contact.getContactIndex(),
                displayNullable(contact.getAlias()),
                contact.getAccountId()
        );
    }

    private void printOnlineUserSearchResult(OnlineUserSearchResultPacket result)
    {
        System.out.println("found: " + result.isSearchResult());
        System.out.println("accountId: " + result.getAccountId());
        System.out.println("message: " + result.getMessage());
        if(!result.isSearchResult())
        {
            return;
        }
        System.out.println("publicKey: " + result.getPublicKey());
    }

    //查看传输进度的时候，可以动态的显示传输进度，也可以仅是一次传输任务进度的快照
    private void printTask(BufferedReader reader, List<String> args) throws IOException
    {
        if (args.size() < 2) {      //校验参数数量
            System.out.println("Usage: task <taskId|transferId> [--once]");
            return;
        }

        String id = args.get(1);    //根据用户输入的id查任务
        TransferTask task = transferTaskRegistry.findByTaskId(id)//根据taskId查
                .or(() -> transferTaskRegistry.findByTransferId(id))//根据transferId查
                .orElse(null);
        if (task == null) {
            System.out.println("Task not found: " + id);
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
        System.out.println("Watching task progress. Press 'Enter' or 'q then Enter' to stop watching.");
        while (applicationContext.isActive()) {     //外层循环
            synchronized (System.out) {
                System.out.print("\r" + clearToLineEnd(formatProgressLine(task)));// \r把光标，移回到当前行的开头， clearToLineEnd()清空当前行再打印新内容
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
                        System.out.println("Stopped watching. Transfer continues in background.");
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
        }
        printTaskDetails(task);
    }

    //格式化输出传输任务的进度
    private String formatProgressLine(TransferTask task)
    {
        double progressPercent = task.getProgress() * 100D;
        int filledWidth = (int) Math.round(Math.min(100D, Math.max(0D, progressPercent)) / 100D * PROGRESS_BAR_WIDTH);
        String bar = "#".repeat(filledWidth) + "-".repeat(PROGRESS_BAR_WIDTH - filledWidth);
        return String.format(
                "[%s] %.2f%% | %s | %.2f mb/s | bytes %d/%d | blocks %d/%d | %s",
                bar,
                progressPercent,
                task.getStatus(),
                task.getAverageSpeedMegabytesPerSecond(),
                task.getTransferredBytes(),
                task.getTotalBytes(),
                task.getTransferredBlocks(),
                task.getTotalBlocks(),
                task.getMessage()
        );
    }

    //清空当前行
    private String clearToLineEnd(String value)
    {
        return "\033[2K" + value;   // \033[2k是ANSI控制码，用来清空当前行
    }

    //判断传输任务是否完成
    private boolean isTerminal(TransferStatus status)
    {
        return status != null && status.isTerminal();
    }

    //打印任务详情
    private void printTaskDetails(TransferTask task)
    {
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
        System.out.printf("speed: %.2f mb/s%n", task.getAverageSpeedMegabytesPerSecond());
        System.out.println("createdAt: " + task.getCreatedAt());
        System.out.println("transferStartedAt: " + task.getTransferStartedAt());
        System.out.println("message: " + task.getMessage());
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
            System.out.println(cryptoSupport.publicKeyFingerprint(args.get(1)));
            return;
        }
        if (!ensureLocalPublicKeyPresent()) {   //当前是否密钥文件
            System.out.println("Command invalid: no local public key is available.");
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
        cryptoSupport.importPrivateKeyFile(PathInputNormalizer.toPath(args.get(1)));//对于导入私钥文件的路径进行规格化操作
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

    private boolean ensureLocalPublicKeyPresent()
    {
        try {
            return isTruthy(cryptoSupport.keyStatus().get("hasPublicKey"));//检查当前是否有密钥
        } catch (Exception ex) {
            System.out.println("Unable to check key status: " + ex.getMessage());
            System.out.println("Run 'key-info' after confirming the crypto service is running.");
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

    private String displayNullable(String value)
    {
        return value == null || value.isBlank() ? "-" : value;
    }

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

    private void addArgument(List<String> args, StringBuilder current)
    {
        if (!current.isEmpty()) {
            args.add(current.toString());
            current.setLength(0);
        }
    }
}
