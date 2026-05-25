package com.client.language;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ConsoleMessages
{
    private static final Map<UiLanguage, Map<Key, String>> MESSAGES = createMessages();
    private static final Map<String, String> CHINESE_LABELS = createChineseLabels();

    private final LanguageSettingsService languageSettingsService;

    public ConsoleMessages(LanguageSettingsService languageSettingsService)
    {
        this.languageSettingsService = languageSettingsService;
    }

    public UiLanguage currentLanguage()
    {
        return languageSettingsService.current();
    }

    public String text(Key key)
    {
        return text(currentLanguage(), key);
    }

    public String format(Key key, Object... args)
    {
        return format(currentLanguage(), key, args);
    }

    public String usage(String syntax)
    {
        return format(Key.USAGE, syntax);
    }

    public String label(String rawLabel)
    {
        return label(currentLanguage(), rawLabel);
    }

    public String tableHeader(String... labels)
    {
        List<String> translated = new ArrayList<>(labels.length);
        for(String label : labels)
        {
            translated.add(label(label));
        }
        return String.join(" | ", translated);
    }

    public List<String> relayHelpLines()
    {
        return relayHelpLines(currentLanguage());
    }

    public List<String> directHelpLines()
    {
        return directHelpLines(currentLanguage());
    }

    public static String text(UiLanguage language, Key key)
    {
        UiLanguage selected = language == null ? UiLanguage.defaultLanguage() : language;
        String value = MESSAGES.get(selected).get(key);
        if(value == null)
        {
            return MESSAGES.get(UiLanguage.defaultLanguage()).get(key);
        }
        return value;
    }

    public static String format(UiLanguage language, Key key, Object... args)
    {
        return String.format(Locale.ROOT, text(language, key), args);
    }

    public static String label(UiLanguage language, String rawLabel)
    {
        if(language != UiLanguage.CHINESE)
        {
            return rawLabel;
        }
        return CHINESE_LABELS.getOrDefault(rawLabel, rawLabel);
    }

    public static List<String> missingTranslations()
    {
        List<String> missing = new ArrayList<>();
        for(UiLanguage language : UiLanguage.values())
        {
            Map<Key, String> messages = MESSAGES.get(language);
            for(Key key : Key.values())
            {
                if(messages == null || messages.get(key) == null || messages.get(key).isBlank())
                {
                    missing.add(language + ":" + key);
                }
            }
        }
        return missing;
    }

    private static List<String> relayHelpLines(UiLanguage language)
    {
        if(language == UiLanguage.CHINESE)
        {
            return List.of(
                    "命令:",
                    "  help                              显示此帮助说明",
                    "  language                          切换控制台语言",
                    "  status                            查看客户端连接状态",
                    "  connect [host] [port]             连接服务器并完成认证",
                    "  disconnect                        断开服务器连接",
                    "  send <filePath> <targetAccountId> 向目标账号发送文件",
                    "  incoming                          查看待接收的传输请求",
                    "  accept <transferId>               在本设备接受一个传输请求",
                    "  reject <transferId>               拒绝并取消一个传输请求",
                    "  cancel <taskId|transferId>        取消正在进行的传输任务",
                    "  retransmit <taskId|transferId>    按接收方进度请求断点重传",
                    "  retransmit-accept <transferId>    接受接收方的重传请求",
                    "  retransmit-reject <transferId>    拒绝接收方的重传请求",
                    "  contacts                          查看本地联系人",
                    "  contact-add <accountId> [alias]   新增或更新本地联系人",
                    "  contact-remove <contact-N|N>      删除本地联系人",
                    "  contact-show <contact-N|N>        查看一个本地联系人",
                    "  blacklist                         查看黑名单记录",
                    "  blacklist-add <accountId> [reason] 新增或更新黑名单记录",
                    "  blacklist-add-contact <contact-N|N> [reason] 将联系人加入黑名单",
                    "  blacklist-remove <accountId>      删除黑名单记录",
                    "  search-user <accountId>           搜索账号是否在线",
                    "  search-user-add <accountId> [alias] 搜索在线账号并加入联系人",
                    "  tasks                             查看传输任务列表",
                    "  task <taskId|transferId> [--once] 查看单个传输任务进度。按Enter或输入q后按Enter停止动态查看。",
                    "  open-received <taskId|transferId|fileName> 在Finder或Explorer中定位接收到的文件",
                    "                                    文件名包含空格时可用双引号或单引号包裹，例如: open-received \"report.zip\"",
                    "  public-key                        打印本地公钥",
                    "  public-key-fingerprint [publicKey] 打印指定公钥的指纹，未提供时打印本地公钥指纹",
                    "  account-id [publicKey]             public-key-fingerprint的别名",
                    "  key-info                          查看加密服务密钥状态",
                    "  generate-key                      在加密服务中生成密钥对",
                    "  delete-key                        从加密服务中删除密钥对",
                    "  export-private-key                导出私钥文本和二维码文件",
                    "  import-private-key <keyText|path> 从手动复制、文本文件或PNG二维码导入私钥",
                    "  import-private-key-file <path>    从文件或PNG二维码导入私钥",
                    "  import-private-key-paste          粘贴多行私钥，最后输入单独一行的点号结束",
                    "  back                              返回模式选择",
                    "  exit                              停止应用程序",
                    "路径包含空格时可以用双引号包裹。"
            );
        }
        return List.of(
                "Commands:",
                "  help                              Show this help",
                "  language                          Change console language",
                "  status                            Show client connection status",
                "  connect [host] [port]             Connect and authenticate with server",
                "  disconnect                        Disconnect from server",
                "  send <filePath> <targetAccountId> Send a file to target account",
                "  incoming                          List incoming transfer requests",
                "  accept <transferId>               Accept an incoming transfer on this device",
                "  reject <transferId>               Reject and cancel an incoming transfer",
                "  cancel <taskId|transferId>        Cancel an active transfer task",
                "  retransmit <taskId|transferId>    Request retransmission from the receiver progress",
                "  retransmit-accept <transferId>    Accept a receiver retransmission request",
                "  retransmit-reject <transferId>    Reject a receiver retransmission request",
                "  contacts                          List local contacts",
                "  contact-add <accountId> [alias]   Add or update a local contact",
                "  contact-remove <contact-N|N>      Remove a local contact",
                "  contact-show <contact-N|N>        Show one local contact",
                "  blacklist                         List blacklist records",
                "  blacklist-add <accountId> [reason] Add or update a blacklist record",
                "  blacklist-add-contact <contact-N|N> [reason] Add a contact to blacklist",
                "  blacklist-remove <accountId>      Remove a blacklist record",
                "  search-user <accountId>           Search whether an account is online",
                "  search-user-add <accountId> [alias] Search online user and add to contacts",
                "  tasks                             List transfer tasks",
                "  task <taskId|transferId> [--once] Watch one transfer task progress. Press Enter or q then Enter to stop watching.",
                "  open-received <taskId|transferId|fileName> Reveal a received file in Finder or Explorer",
                "                                    Wrap fileName with double or single quotes, for example: open-received \"report.zip\"",
                "  public-key                        Print local public key",
                "  public-key-fingerprint [publicKey] Print fingerprint for the given public key, or local public key when omitted",
                "  account-id [publicKey]             Alias of public-key-fingerprint",
                "  key-info                          Show crypto service key status",
                "  generate-key                      Generate key pair in the crypto service",
                "  delete-key                        Delete key pair from the crypto service",
                "  export-private-key                Export private key text and QR artifact files",
                "  import-private-key <keyText|path> Import private key from raw text, a text file, or a PNG QR",
                "  import-private-key-file <path>    Import private key from a file or PNG QR",
                "  import-private-key-paste          Paste a multi-line private key, then enter a single dot",
                "  back                              Return to mode selector",
                "  exit                              Stop application",
                "Paths with spaces can be wrapped in double quotes."
        );
    }

    private static List<String> directHelpLines(UiLanguage language)
    {
        if(language == UiLanguage.CHINESE)
        {
            return List.of(
                    "直连命令:",
                    "  help                              显示此帮助说明",
                    "  language                          切换控制台语言",
                    "  status                            查看直连设置和任务状态",
                    "  handshake                         启动发送方/接收方二维码握手向导",
                    "  port-mode                         查看直连监听端口模式",
                    "  port-mode random                  使用随机临时监听端口",
                    "  port-mode fixed <port>            使用固定监听端口",
                    "  qr-clean                          删除过期二维码文件",
                    "  incoming                          查看待接收的传输请求",
                    "  accept <transferId>               接受一个传输请求",
                    "  reject <transferId>               拒绝一个传输请求",
                    "  cancel <taskId|transferId>        取消传输任务",
                    "  retransmit <taskId|transferId>    请求断点重传",
                    "  retransmit-accept <transferId>    接受重传请求",
                    "  retransmit-reject <transferId>    拒绝重传请求",
                    "  tasks                             查看传输任务列表",
                    "  task <taskId|transferId> [--once] 查看单个传输任务",
                    "  key-info                          查看加密服务密钥状态",
                    "  generate-key                      在加密服务中生成密钥对",
                    "  delete-key                        从加密服务中删除密钥对",
                    "  export-private-key                导出私钥文本和二维码文件",
                    "  import-private-key <keyText|path> 从手动复制、文本文件或PNG二维码导入私钥",
                    "  import-private-key-file <path>    从文件或PNG二维码导入私钥",
                    "  import-private-key-paste          粘贴多行私钥，最后输入单独一行的点号结束",
                    "  public-key                        打印本地公钥",
                    "  account-id [publicKey]            打印accountId/公钥指纹",
                    "  back                              返回模式选择",
                    "  exit                              停止应用程序"
            );
        }
        return List.of(
                "Direct commands:",
                "  help                              Show this help",
                "  language                          Change console language",
                "  status                            Show direct settings and task status",
                "  handshake                         Start sender/receiver QR handshake wizard",
                "  port-mode                         Show direct listen port mode",
                "  port-mode random                  Use a random temporary listen port",
                "  port-mode fixed <port>            Use a fixed listen port",
                "  qr-clean                          Remove expired QR files",
                "  incoming                          List incoming transfer requests",
                "  accept <transferId>               Accept an incoming transfer",
                "  reject <transferId>               Reject an incoming transfer",
                "  cancel <taskId|transferId>        Cancel a transfer",
                "  retransmit <taskId|transferId>    Request retransmission",
                "  retransmit-accept <transferId>    Accept retransmission",
                "  retransmit-reject <transferId>    Reject retransmission",
                "  tasks                             List transfer tasks",
                "  task <taskId|transferId> [--once] Watch one task",
                "  key-info                          Show crypto service key status",
                "  generate-key                      Generate key pair in the crypto service",
                "  delete-key                        Delete key pair from the crypto service",
                "  export-private-key                Export private key text and QR artifact files",
                "  import-private-key <keyText|path> Import private key from raw text, a text file, or a PNG QR",
                "  import-private-key-file <path>    Import private key from a file or PNG QR",
                "  import-private-key-paste          Paste a multi-line private key, then enter a single dot",
                "  public-key                        Print local public key",
                "  account-id [publicKey]            Print accountId/fingerprint",
                "  back                              Return to mode selector",
                "  exit                              Stop application"
        );
    }

    private static Map<UiLanguage, Map<Key, String>> createMessages()
    {
        Map<UiLanguage, Map<Key, String>> messages = new EnumMap<>(UiLanguage.class);
        Map<Key, String> en = new EnumMap<>(Key.class);
        Map<Key, String> zh = new EnumMap<>(Key.class);

        put(en, zh, Key.UNKNOWN_MODE, "Unknown mode. Choose 1/relay, 2/direct, 0/exit, or language.", "未知模式。请选择 1/relay、2/direct、0/exit，或输入 language。");
        put(en, zh, Key.CONSOLE_STOPPED, "Console stopped: %s", "控制台已停止: %s");
        put(en, zh, Key.MODE_TITLE, "Choose transfer mode:", "请选择传输模式:");
        put(en, zh, Key.MODE_RELAY_OPTION, "  1. relay  - server relay mode", "  1. relay  - 服务器中继模式");
        put(en, zh, Key.MODE_DIRECT_OPTION, "  2. direct - IPv6 direct QR handshake mode", "  2. direct - IPv6直连二维码握手模式");
        put(en, zh, Key.MODE_EXIT_OPTION, "  0. exit", "  0. exit");
        put(en, zh, Key.RELAY_CONSOLE_READY, "Relay console. Type 'help' for commands, 'language' to change language, or 'back' to choose another mode.", "中继控制台。输入 'help' 查看命令，输入 'language' 切换语言，或输入 'back' 返回模式选择。");
        put(en, zh, Key.DIRECT_CONSOLE_READY, "IPv6 direct console. Type 'help' for commands, 'language' to change language, or 'back' to choose another mode.", "IPv6直连控制台。输入 'help' 查看命令，输入 'language' 切换语言，或输入 'back' 返回模式选择。");
        put(en, zh, Key.CONFIRM_RETURN_MODE_DISCONNECT_RELAY, "Return to mode selector and disconnect relay connection if active? [y/N] ", "返回模式选择并断开当前中继连接吗？[y/N] ");
        put(en, zh, Key.CONFIRM_RETURN_MODE_CLOSE_DIRECT, "Return to mode selector and close direct listener if active? [y/N] ", "返回模式选择并关闭当前直连监听吗？[y/N] ");
        put(en, zh, Key.STARTUP_NO_KEY_PAUSED, "No local key pair is available. Auto-connect has been paused for this startup.", "当前没有本地密钥对。本次启动已暂停自动连接。");
        put(en, zh, Key.STARTUP_GENERATE_PROMPT, "Generate a new key pair now and continue auto-connect? [y/N] ", "现在生成新的密钥对并继续自动连接吗？[y/N] ");
        put(en, zh, Key.STARTUP_KEY_GENERATED, "Key pair generated. Auto-connect will continue if it was configured.", "密钥对已生成。如果已配置自动连接，将继续自动连接。");
        put(en, zh, Key.STARTUP_SKIPPED, "Skipped key generation. Auto-connect remains paused until you generate or import a private key.", "已跳过密钥生成。自动连接会保持暂停，直到你生成或导入私钥。");
        put(en, zh, Key.STARTUP_UNABLE_HANDLE, "Unable to handle startup key setup: %s", "无法处理启动密钥设置: %s");
        put(en, zh, Key.RUN_KEY_INFO_AFTER_CRYPTO, "Run 'key-info' after confirming the crypto service is running.", "确认加密服务运行后，请执行 'key-info'。");
        put(en, zh, Key.UNKNOWN_COMMAND, "Unknown command: %s. Type 'help' for commands.", "未知命令: %s。输入 'help' 查看命令。");
        put(en, zh, Key.UNKNOWN_DIRECT_COMMAND, "Unknown direct command: %s. Type 'help' for commands.", "未知直连命令: %s。输入 'help' 查看命令。");
        put(en, zh, Key.COMMAND_FAILED, "Command failed: %s", "命令执行失败: %s");
        put(en, zh, Key.EXPIRED_QR_GROUPS_REMOVED, "Expired QR groups removed: %s", "已删除过期二维码组: %s");
        put(en, zh, Key.DIRECT_LISTEN_PORT_MODE, "Direct listen port mode: %s", "直连监听端口模式: %s");
        put(en, zh, Key.FIXED_LISTEN_PORT, "Fixed listen port: %s", "固定监听端口: %s");
        put(en, zh, Key.QR_OUTPUT_CLEANED, "QR output dir: %s expired QR groups cleaned", "二维码输出目录: 已清理 %s 个过期二维码组");
        put(en, zh, Key.TASK_COUNT, "Task count: %s", "任务数量: %s");
        put(en, zh, Key.DIRECT_PORT_MODE_SET, "Direct listen port mode set to %s", "直连监听端口模式已设置为 %s");
        put(en, zh, Key.DIRECT_PORT_MODE_SET_WITH_PORT, "Direct listen port mode set to %s %s", "直连监听端口模式已设置为 %s %s");
        put(en, zh, Key.USAGE, "Usage: %s", "用法: %s");
        put(en, zh, Key.ROLE_PROMPT, "Role [sender/receiver]> ", "角色 [sender/receiver]> ");
        put(en, zh, Key.INVALID_ROLE, "Invalid role. Choose sender or receiver.", "角色无效。请选择 sender 或 receiver。");
        put(en, zh, Key.SEND_QR_TO_RECEIVER, "Send this QR/FST1 file to the receiver.", "请将此二维码/FST1文件发送给接收方。");
        put(en, zh, Key.PASTE_RECEIVER_FST1, "Type receiver .fst1 or .png file path:", "输入接收方 .fst1 或 .png 文件路径:");
        put(en, zh, Key.HANDSHAKE_CANCEL_HINT, "Leave blank or type 'cancel' to stop this handshake step.", "留空或输入 'cancel' 可停止当前握手步骤。");
        put(en, zh, Key.DIRECT_SESSION_CONNECTED, "Direct session connected: %s", "直连会话已连接: %s");
        put(en, zh, Key.FILE_PATH_TO_SEND, "File path to send> ", "要发送的文件路径> ");
        put(en, zh, Key.SEND_TASK_CREATED, "Send task created: %s", "发送任务已创建: %s");
        put(en, zh, Key.PASTE_SENDER_FST1, "Type sender .fst1 or .png file path:", "输入发送方 .fst1 或 .png 文件路径:");
        put(en, zh, Key.SEND_QR_TO_SENDER_WAITING, "Send this QR/FST1 file to the sender. Waiting for incoming transfer request.", "请将此二维码/FST1文件发送给发送方。正在等待传输请求。");
        put(en, zh, Key.QR_PNG, "QR PNG: %s", "二维码PNG: %s");
        put(en, zh, Key.QR_FST1, "QR FST1 file: %s", "二维码FST1文件: %s");
        put(en, zh, Key.QR_ASCII, "QR ASCII file: %s", "二维码ASCII文件: %s");
        put(en, zh, Key.QR_TEXT, "QR text:", "二维码文本:");
        put(en, zh, Key.WELCOME_READY, "File Security Transmission console is ready.", "文件安全传输控制台已就绪。");
        put(en, zh, Key.WELCOME_HELP_HINT, "Type 'help' for commands.", "输入 'help' 查看命令。");
        put(en, zh, Key.SELECT_LANGUAGE, "Select language:", "请选择语言:");
        put(en, zh, Key.LANGUAGE_CHANGED_ENGLISH, "Language changed to English.", "Language changed to English.");
        put(en, zh, Key.LANGUAGE_CHANGED_CHINESE, "语言已切换为中文。", "语言已切换为中文。");
        put(en, zh, Key.INVALID_LANGUAGE, "Invalid language. Please choose 1/English or 2/Chinese.", "语言选择无效。请选择 1/English 或 2/Chinese。");
        put(en, zh, Key.INCOMING_NOTIFICATION_SIMPLE, "New incoming transfer request. Run 'incoming' to list requests.", "收到新的传输请求。执行 'incoming' 查看请求列表。");
        put(en, zh, Key.INCOMING_NOTIFICATION, "New incoming transfer request: %s | sender=%s | file=%s | bytes=%s | blocks=%s%nUse 'accept %s' to receive it or 'reject %s' to cancel it.", "收到新的传输请求: %s | 发送方=%s | 文件=%s | 字节=%s | 分块=%s%n使用 'accept %s' 接收，或使用 'reject %s' 取消。");
        put(en, zh, Key.RETRANSMISSION_NOTIFICATION_SIMPLE, "Retransmission request received. Use 'retransmit-accept <transferId>' or 'retransmit-reject <transferId>'.", "收到重传请求。使用 'retransmit-accept <transferId>' 或 'retransmit-reject <transferId>'。");
        put(en, zh, Key.RETRANSMISSION_NOTIFICATION, "Retransmission request received: %s | file=%s | from block=%s | reason=%s%nUse 'retransmit-accept %s' to continue, or 'retransmit-reject %s' to refuse.", "收到重传请求: %s | 文件=%s | 起始分块=%s | 原因=%s%n使用 'retransmit-accept %s' 继续，或使用 'retransmit-reject %s' 拒绝。");
        put(en, zh, Key.CONNECTED_AUTHENTICATED, "Connected and authenticated: %s:%s", "已连接并完成认证: %s:%s");
        put(en, zh, Key.DISCONNECTED, "Disconnected.", "已断开连接。");
        put(en, zh, Key.NO_TRANSFER_TASKS, "No transfer tasks.", "没有传输任务。");
        put(en, zh, Key.NO_INCOMING_REQUESTS, "No incoming transfer requests.", "没有待接收的传输请求。");
        put(en, zh, Key.ACCEPTED_INCOMING, "Accepted incoming transfer request: %s", "已接受传输请求: %s");
        put(en, zh, Key.REJECTED_INCOMING, "Rejected incoming transfer request: %s", "已拒绝传输请求: %s");
        put(en, zh, Key.TRANSFER_CANCELED, "Transfer canceled: %s", "传输已取消: %s");
        put(en, zh, Key.RETRANSMISSION_REQUESTED, "Retransmission requested: %s", "已请求重传: %s");
        put(en, zh, Key.RETRANSMISSION_ACCEPTED, "Retransmission accepted: %s", "已接受重传: %s");
        put(en, zh, Key.RETRANSMISSION_REJECTED, "Retransmission rejected: %s", "已拒绝重传: %s");
        put(en, zh, Key.NO_CONTACTS, "No contacts.", "没有联系人。");
        put(en, zh, Key.CONTACT_SAVED, "Contact saved: contact-%s | %s | %s | %s", "联系人已保存: contact-%s | %s | %s | %s");
        put(en, zh, Key.ONLINE_USER_NOT_FOUND_CONTACT_EMPTY, "Online user not found. Contact publicKey will be empty.", "未找到在线用户。联系人publicKey将为空。");
        put(en, zh, Key.SEARCH_PUBLIC_KEY_FAILED, "Unable to search publicKey from server. Contact publicKey will be empty: %s", "无法从服务器搜索publicKey。联系人publicKey将为空: %s");
        put(en, zh, Key.CONTACT_REMOVED, "Contact removed: contact-%s", "联系人已删除: contact-%s");
        put(en, zh, Key.CONTACT_NOT_FOUND, "Contact not found: contact-%s", "未找到联系人: contact-%s");
        put(en, zh, Key.NO_BLACKLIST, "No blacklist records.", "没有黑名单记录。");
        put(en, zh, Key.BLACKLIST_SAVED, "Blacklist record saved: %s", "黑名单记录已保存: %s");
        put(en, zh, Key.BLACKLIST_SAVED_FROM_CONTACT, "Blacklist record saved from contact-%s: %s", "已从 contact-%s 保存黑名单记录: %s");
        put(en, zh, Key.BLACKLIST_REMOVED, "Blacklist record removed: %s", "黑名单记录已删除: %s");
        put(en, zh, Key.CONTACT_SAVED_SHORT, "Contact saved: contact-%s | %s | %s", "联系人已保存: contact-%s | %s | %s");
        put(en, zh, Key.TASK_NOT_FOUND, "Task not found: %s", "未找到任务: %s");
        put(en, zh, Key.WATCHING_TASK, "Watching task progress. Press 'Enter' or 'q then Enter' to stop watching.", "正在查看任务进度。按 Enter 或输入 q 后按 Enter 停止查看。");
        put(en, zh, Key.STOPPED_WATCHING, "Stopped watching. Transfer continues in background.", "已停止查看。传输会继续在后台进行。");
        put(en, zh, Key.PROGRESS_LINE, "[%s] %.2f%% | %s | %.2f mb/s | %s %d/%d | %s %d/%d | %s", "[%s] %.2f%% | %s | %.2f mb/s | %s %d/%d | %s %d/%d | %s");
        put(en, zh, Key.RECEIVED_FILE_NOT_FOUND, "Received file not found by taskId, transferId, or fileName: %s", "未通过taskId、transferId或fileName找到接收文件: %s");
        put(en, zh, Key.RECEIVED_PATH_EMPTY, "Received file path is empty for task: %s", "任务的接收文件路径为空: %s");
        put(en, zh, Key.RECEIVED_PATH_MISSING, "Received file path no longer exists: %s", "接收文件路径已不存在: %s");
        put(en, zh, Key.OPENED_FILE_LOCATION, "Opened file location: %s", "已打开文件位置: %s");
        put(en, zh, Key.MULTIPLE_RECEIVED_MATCHED, "Multiple received files matched. Please use taskId or transferId:", "匹配到多个接收文件。请使用taskId或transferId:");
        put(en, zh, Key.NOT_RECEIVED_FILE_TASK, "Task is not a received file: %s", "该任务不是接收文件任务: %s");
        put(en, zh, Key.NO_LOCAL_PUBLIC_KEY, "Command invalid: no local public key is available.", "命令无效: 当前没有本地公钥。");
        put(en, zh, Key.PRIVATE_KEY_IMPORTED, "Private key imported. Public key fingerprint: %s", "私钥已导入。公钥指纹: %s");
        put(en, zh, Key.PRIVATE_KEY_EXPORT_READY, "Private key export files are ready. Keep them secure and remove them after use.", "私钥导出文件已生成。请妥善保管，并在使用后及时删除。");
        put(en, zh, Key.AUTO_CONNECT_CONTINUE, "Auto-connect will continue if it was paused by missing key.", "如果自动连接因缺少密钥而暂停，将继续自动连接。");
        put(en, zh, Key.PASTE_PRIVATE_KEY, "Paste private key text. Enter a single dot on its own line to finish.", "请粘贴私钥文本。单独输入一行点号结束。");
        put(en, zh, Key.STOPPING_APPLICATION, "Stopping application...", "正在停止应用程序...");
        put(en, zh, Key.UNABLE_CHECK_KEY_STATUS, "Unable to check key status: %s", "无法检查密钥状态: %s");
        put(en, zh, Key.NO_LOCAL_KEY_PAIR, "No local key pair is available.", "当前没有本地密钥对。");
        put(en, zh, Key.MISSING_KEY_ACTION, "Run 'generate-key' to create one, or use 'import-private-key-file <path>' / 'import-private-key-paste' to import an existing private key. PNG QR files are also supported.", "执行 'generate-key' 创建密钥对，或使用 'import-private-key-file <path>' / 'import-private-key-paste' 导入已有私钥。也支持PNG二维码文件。");
        put(en, zh, Key.AUTO_CONNECT_FAILED, "Auto-connect failed: %s", "自动连接失败: %s");
        put(en, zh, Key.CONSOLE_AVAILABLE_TRY_CONNECT, "Console is still available. Try: connect <host> <port>", "控制台仍可使用。可尝试: connect <host> <port>");
        put(en, zh, Key.STARTUP_CHECK_FAILED, "Startup check failed: %s", "启动检查失败: %s");
        put(en, zh, Key.CONSOLE_AVAILABLE_TRY_KEY_INFO, "Console is still available. Try: key-info", "控制台仍可使用。可尝试: key-info");

        messages.put(UiLanguage.ENGLISH, en);
        messages.put(UiLanguage.CHINESE, zh);
        return messages;
    }

    private static Map<String, String> createChineseLabels()
    {
        return Map.ofEntries(
                Map.entry("taskId", "任务ID"),
                Map.entry("transferId", "传输ID"),
                Map.entry("direction", "方向"),
                Map.entry("mode", "模式"),
                Map.entry("transportMode", "传输模式"),
                Map.entry("status", "状态"),
                Map.entry("progress", "进度"),
                Map.entry("fileName", "文件名"),
                Map.entry("message", "消息"),
                Map.entry("sender", "发送方"),
                Map.entry("file", "文件"),
                Map.entry("bytes", "字节"),
                Map.entry("blocks", "分块"),
                Map.entry("receivedAt", "接收时间"),
                Map.entry("contact", "联系人"),
                Map.entry("alias", "别名"),
                Map.entry("accountId", "账号ID"),
                Map.entry("publicKey", "公钥"),
                Map.entry("createdAt", "创建时间"),
                Map.entry("updatedAt", "更新时间"),
                Map.entry("reason", "原因"),
                Map.entry("found", "找到"),
                Map.entry("localPath", "本地路径"),
                Map.entry("peerDeviceId", "对端设备ID"),
                Map.entry("speed", "速度"),
                Map.entry("transferStartedAt", "传输开始时间"),
                Map.entry("deviceId", "设备ID"),
                Map.entry("connectedHost", "已连接主机"),
                Map.entry("connectedPort", "已连接端口"),
                Map.entry("hasPrivateKey", "是否有私钥"),
                Map.entry("hasPublicKey", "是否有公钥"),
                Map.entry("privateKey", "私钥"),
                Map.entry("publicKeyPem", "公钥PEM"),
                Map.entry("privateKeyPath", "私钥路径"),
                Map.entry("publicKeyPath", "公钥路径"),
                Map.entry("keyMissing", "密钥缺失"),
                Map.entry("keySetupPrompted", "已提示密钥设置"),
                Map.entry("shouldPromptKeySetup", "应提示密钥设置"),
                Map.entry("autoConnectConfigured", "已配置自动连接"),
                Map.entry("autoConnectBlocked", "自动连接已阻塞"),
                Map.entry("autoConnectBlockReason", "自动连接阻塞原因"),
                Map.entry("recommendedAction", "建议操作"),
                Map.entry("keyStatus", "密钥状态"),
                Map.entry("startupStatePath", "启动状态路径"),
                Map.entry("success", "成功"),
                Map.entry("listenPortMode", "监听端口模式"),
                Map.entry("fixedListenPort", "固定监听端口"),
                Map.entry("settingsPath", "设置路径")
        );
    }

    private static void put(Map<Key, String> en, Map<Key, String> zh, Key key, String english, String chinese)
    {
        en.put(key, english);
        zh.put(key, chinese);
    }

    public enum Key
    {
        UNKNOWN_MODE,
        CONSOLE_STOPPED,
        MODE_TITLE,
        MODE_RELAY_OPTION,
        MODE_DIRECT_OPTION,
        MODE_EXIT_OPTION,
        RELAY_CONSOLE_READY,
        DIRECT_CONSOLE_READY,
        CONFIRM_RETURN_MODE_DISCONNECT_RELAY,
        CONFIRM_RETURN_MODE_CLOSE_DIRECT,
        STARTUP_NO_KEY_PAUSED,
        STARTUP_GENERATE_PROMPT,
        STARTUP_KEY_GENERATED,
        STARTUP_SKIPPED,
        STARTUP_UNABLE_HANDLE,
        RUN_KEY_INFO_AFTER_CRYPTO,
        UNKNOWN_COMMAND,
        UNKNOWN_DIRECT_COMMAND,
        COMMAND_FAILED,
        EXPIRED_QR_GROUPS_REMOVED,
        DIRECT_LISTEN_PORT_MODE,
        FIXED_LISTEN_PORT,
        QR_OUTPUT_CLEANED,
        TASK_COUNT,
        DIRECT_PORT_MODE_SET,
        DIRECT_PORT_MODE_SET_WITH_PORT,
        USAGE,
        ROLE_PROMPT,
        INVALID_ROLE,
        SEND_QR_TO_RECEIVER,
        PASTE_RECEIVER_FST1,
        HANDSHAKE_CANCEL_HINT,
        DIRECT_SESSION_CONNECTED,
        FILE_PATH_TO_SEND,
        SEND_TASK_CREATED,
        PASTE_SENDER_FST1,
        SEND_QR_TO_SENDER_WAITING,
        QR_PNG,
        QR_FST1,
        QR_ASCII,
        QR_TEXT,
        WELCOME_READY,
        WELCOME_HELP_HINT,
        SELECT_LANGUAGE,
        LANGUAGE_CHANGED_ENGLISH,
        LANGUAGE_CHANGED_CHINESE,
        INVALID_LANGUAGE,
        INCOMING_NOTIFICATION_SIMPLE,
        INCOMING_NOTIFICATION,
        RETRANSMISSION_NOTIFICATION_SIMPLE,
        RETRANSMISSION_NOTIFICATION,
        CONNECTED_AUTHENTICATED,
        DISCONNECTED,
        NO_TRANSFER_TASKS,
        NO_INCOMING_REQUESTS,
        ACCEPTED_INCOMING,
        REJECTED_INCOMING,
        TRANSFER_CANCELED,
        RETRANSMISSION_REQUESTED,
        RETRANSMISSION_ACCEPTED,
        RETRANSMISSION_REJECTED,
        NO_CONTACTS,
        CONTACT_SAVED,
        ONLINE_USER_NOT_FOUND_CONTACT_EMPTY,
        SEARCH_PUBLIC_KEY_FAILED,
        CONTACT_REMOVED,
        CONTACT_NOT_FOUND,
        NO_BLACKLIST,
        BLACKLIST_SAVED,
        BLACKLIST_SAVED_FROM_CONTACT,
        BLACKLIST_REMOVED,
        CONTACT_SAVED_SHORT,
        TASK_NOT_FOUND,
        WATCHING_TASK,
        STOPPED_WATCHING,
        PROGRESS_LINE,
        RECEIVED_FILE_NOT_FOUND,
        RECEIVED_PATH_EMPTY,
        RECEIVED_PATH_MISSING,
        OPENED_FILE_LOCATION,
        MULTIPLE_RECEIVED_MATCHED,
        NOT_RECEIVED_FILE_TASK,
        NO_LOCAL_PUBLIC_KEY,
        PRIVATE_KEY_IMPORTED,
        PRIVATE_KEY_EXPORT_READY,
        AUTO_CONNECT_CONTINUE,
        PASTE_PRIVATE_KEY,
        STOPPING_APPLICATION,
        UNABLE_CHECK_KEY_STATUS,
        NO_LOCAL_KEY_PAIR,
        MISSING_KEY_ACTION,
        AUTO_CONNECT_FAILED,
        CONSOLE_AVAILABLE_TRY_CONNECT,
        STARTUP_CHECK_FAILED,
        CONSOLE_AVAILABLE_TRY_KEY_INFO
    }
}
