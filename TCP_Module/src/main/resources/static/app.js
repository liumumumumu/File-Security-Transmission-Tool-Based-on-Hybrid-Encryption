const { createApp } = Vue;

const MB = 1024 * 1024;
const DEFAULT_API_BASE = window.location.protocol === "file:" ? "http://127.0.0.1:20201" : window.location.origin;

createApp({
  data() {
    return {
      activeView: "transfer",
      navigation: [
        { id: "transfer", label: "传输工作台", icon: "layout-dashboard" },
        { id: "receive", label: "接收任务", icon: "inbox" },
        { id: "tasks", label: "任务队列", icon: "list-checks" },
        { id: "security", label: "安全链路", icon: "shield-check" },
        { id: "system", label: "系统状态", icon: "server-cog" },
        { id: "settings", label: "接口配置", icon: "settings-2" },
      ],
      modes: [
        { label: "发送", value: "send" },
        { label: "接收", value: "receive" },
      ],
      mode: "send",
      files: [],
      isDragging: false,
      showDevPanel: false,
      receiveCode: "",
      localFilePath: "",
      apiBase: DEFAULT_API_BASE,
      settings: {
        host: "127.0.0.1",
        port: 9000,
        keyExchange: "RSA-2048",
        chunkSize: 1,
      },
      options: {
        enableGcm: true,
        resume: true,
        demoMode: true,
      },
      status: "idle",
      progress: 0,
      speedMbps: 0,
      etaSeconds: null,
      ackedChunks: 0,
      taskId: "",
      timer: null,
      pollTimer: null,
      backendLoading: false,
      keyLoading: false,
      systemStatus: null,
      keyStatus: null,
      backendError: "",
      keyError: "",
      logs: [],
      demoTasks: [
        {
          id: "task-demo-1",
          title: "archive-demo.zip",
          meta: "等待后端任务接口",
          percent: 0,
          tone: "neutral",
          icon: "clock",
        },
        {
          id: "task-demo-2",
          title: "keys-handshake.json",
          meta: "RSA / AES 会话信息",
          percent: 100,
          tone: "success",
          icon: "key-round",
        },
      ],
      pipeline: [
        {
          key: "connecting",
          title: "连接 Java 后端",
          desc: "Vue 调用 Spring Boot REST API，后端再连接 Netty TCP。",
        },
        {
          key: "key_exchange",
          title: "协商会话密钥",
          desc: "RSA/ECC 保护 AES-256 会话密钥。",
        },
        {
          key: "encrypting_chunks",
          title: "分块加密传输",
          desc: "每块独立 AES-GCM nonce、ciphertext、tag。",
        },
        {
          key: "waiting_ack",
          title: "ACK 与断点续传",
          desc: "接收端确认块序号，失败后按确认状态恢复。",
        },
      ],
    };
  },

  computed: {
    viewTitle() {
      const item = this.navigation.find((entry) => entry.id === this.activeView);
      return item ? item.label : "传输工作台";
    },

    fileSummaries() {
      return this.files.map((file, index) => ({
        id: `${file.name}-${file.size}-${file.lastModified}-${index}`,
        name: file.name,
        sizeText: this.formatBytes(file.size),
        chunks: this.fileChunks(file.size),
      }));
    },

    totalBytes() {
      return this.files.reduce((sum, file) => sum + file.size, 0);
    },

    totalSizeText() {
      return this.formatBytes(this.totalBytes);
    },

    totalChunks() {
      if (!this.files.length) return 0;
      return this.files.reduce((sum, file) => sum + this.fileChunks(file.size), 0);
    },

    visibleTasks() {
      const current = {
        id: "current-task",
        title: this.files[0]?.name || (this.mode === "receive" ? this.receiveCode || "接收任务" : "当前传输任务"),
        meta: `${this.connectionText} · ${this.totalSizeText} · ${this.ackedChunks}/${this.totalChunks || 0} ACK`,
        percent: this.progress,
        tone: this.status === "failed" ? "danger" : this.status === "completed" ? "success" : "active",
        icon: this.mode === "send" ? "upload" : "download",
      };
      return [current, ...this.demoTasks];
    },

    isRunning() {
      return ["connecting", "key_exchange", "encrypting_chunks", "waiting_ack", "resuming"].includes(
        this.status,
      );
    },

    connectionText() {
      const labels = {
        idle: "未连接",
        connecting: "连接中",
        key_exchange: "协商密钥",
        encrypting_chunks: "传输中",
        waiting_ack: "等待 ACK",
        paused: "已暂停",
        resuming: "恢复中",
        completed: "已完成",
        failed: "失败",
      };
      return labels[this.status] || "未知";
    },

    progressLabel() {
      const labels = {
        idle: this.mode === "receive" ? "输入接收码后查询任务" : "等待任务",
        connecting: "正在连接 Java 后端",
        key_exchange: "正在协商会话密钥",
        encrypting_chunks: "正在加密并发送分块",
        waiting_ack: "等待接收端确认块",
        paused: "任务已暂停",
        resuming: "正在根据断点恢复任务",
        completed: "传输完成",
        failed: "任务失败，请检查后端或网络",
      };
      return labels[this.status] || "等待任务";
    },

    speedText() {
      return this.speedMbps > 0 ? `${this.speedMbps.toFixed(1)} MB/s` : "0 MB/s";
    },

    etaText() {
      if (this.etaSeconds === null) return "--";
      return `${this.etaSeconds} s`;
    },

    sessionKeyText() {
      return ["key_exchange", "encrypting_chunks", "waiting_ack", "paused", "resuming", "completed"].includes(
        this.status,
      )
        ? "AES-256"
        : "待协商";
    },

    currentStageIndex() {
      const stageMap = {
        idle: 0,
        connecting: 0,
        key_exchange: 1,
        encrypting_chunks: 2,
        waiting_ack: 3,
        paused: 3,
        resuming: 3,
        completed: 3,
        failed: 0,
      };
      return stageMap[this.status] || 0;
    },

    statusClass() {
      return {
        online: ["key_exchange", "encrypting_chunks", "waiting_ack", "completed"].includes(this.status),
        paused: this.status === "paused",
        danger: this.status === "failed",
      };
    },

    backendStatusText() {
      if (this.backendError) return "后端异常";
      if (this.systemStatus?.status) return this.systemStatus.status;
      return "未同步";
    },

    keyStatusText() {
      if (this.keyError) return "密钥异常";
      if (!this.keyStatus) return "未同步";
      const hasPrivate = this.toBoolean(this.keyStatus.hasPrivateKey);
      const hasPublic = this.toBoolean(this.keyStatus.hasPublicKey);
      return hasPrivate && hasPublic ? "密钥完整" : "密钥缺失";
    },
  },

  mounted() {
    this.addLog("新版 App UI 已加载，当前为演示模式。");
    this.refreshIcons();
    this.refreshBackendState();
  },

  updated() {
    this.refreshIcons();
  },

  beforeUnmount() {
    this.stopTimers();
  },

  methods: {
    setActiveView(view) {
      this.activeView = view;
      if (view === "receive") {
        this.setMode("receive");
      }
      if (view === "settings") {
        this.showDevPanel = true;
      }
      if (view === "system") {
        this.refreshBackendState();
      }
    },

    setMode(mode) {
      this.mode = mode;
      this.resetTransfer(false);
      this.addLog(`切换到${mode === "send" ? "发送" : "接收"}模式。`);
    },

    toggleDevPanel() {
      this.showDevPanel = !this.showDevPanel;
      if (this.showDevPanel) {
        this.activeView = "settings";
      }
    },

    handleFileChange(event) {
      this.setFiles(event.target.files);
    },

    handleDrop(event) {
      this.isDragging = false;
      this.setFiles(event.dataTransfer.files);
    },

    setFiles(fileList) {
      this.files = Array.from(fileList || []);
      if (this.files.length) {
        const firstFile = this.files[0];
        if (!this.localFilePath) {
          this.localFilePath = firstFile.path || firstFile.webkitRelativePath || firstFile.name || "";
        }
        this.addLog(`已选择 ${this.files.length} 个文件，共 ${this.totalSizeText}。`);
      }
    },

    async startTransfer() {
      if (this.mode === "send" && this.options.demoMode && !this.files.length) {
        this.addLog("请先选择至少一个文件。");
        this.status = "idle";
        return;
      }

      if (this.mode === "send" && !this.options.demoMode && !this.files.length && !this.localFilePath) {
        this.addLog("请先选择文件，或直接填写可访问的本地路径。");
        this.status = "idle";
        return;
      }

      if (!this.options.demoMode && this.mode === "send" && !this.localFilePath) {
        this.addLog("关闭演示模式后，请填写 Java 后端可访问的本地文件路径。");
        this.status = "idle";
        return;
      }

      if (!this.options.demoMode && this.mode === "send" && !this.receiveCode) {
        this.addLog("关闭演示模式后，请填写接收方 accountId。");
        this.status = "idle";
        return;
      }

      if (this.mode === "receive" && !this.receiveCode) {
        this.addLog("请先输入接收码或 transferId。");
        this.status = "idle";
        return;
      }

      this.stopTimers();
      this.progress = 0;
      this.speedMbps = 0;
      this.etaSeconds = null;
      this.ackedChunks = 0;
      this.status = "connecting";
      this.addLog(this.options.demoMode ? "开始演示传输。" : "开始调用 Java 后端创建传输任务。");

      if (this.options.demoMode) {
        this.startDemoTransfer();
        return;
      }

      await this.createBackendTransfer();
    },

    startDemoTransfer() {
      let tick = 0;
      this.timer = window.setInterval(() => {
        tick += 1;

        if (tick <= 2) {
          this.status = "connecting";
          this.progress = Math.max(this.progress, 7);
        } else if (tick <= 4) {
          this.status = "key_exchange";
          this.progress = Math.max(this.progress, 20);
        } else if (this.progress < 92) {
          this.status = "encrypting_chunks";
          this.progress = Math.min(92, this.progress + Math.ceil(Math.random() * 9));
        } else if (this.progress < 100) {
          this.status = "waiting_ack";
          this.progress = Math.min(100, this.progress + 4);
        }

        this.speedMbps = this.status === "connecting" ? 0 : 7 + Math.random() * 18;
        this.etaSeconds = this.progress < 100 ? Math.max(1, Math.ceil((100 - this.progress) / 12)) : 0;
        this.ackedChunks = Math.min(this.totalChunks || 1, Math.ceil(((this.totalChunks || 1) * this.progress) / 100));

        if (this.progress >= 100) {
          this.status = "completed";
          this.stopTimers();
          this.addLog("演示传输完成。");
        }
      }, 480);
    },

    async createBackendTransfer() {
      try {
        if (this.mode === "receive") {
          this.taskId = this.receiveCode.trim();
          this.addLog(`开始查询后端任务：${this.taskId}。`);
          this.startPolling();
          return;
        }

        const result = await this.requestJson("/api/send", {
          method: "POST",
          body: JSON.stringify({
            filePath: this.localFilePath || this.files[0]?.path || this.files[0]?.webkitRelativePath || this.files[0]?.name || "",
            targetAccountId: this.receiveCode.trim(),
          }),
        });
        this.taskId = result.task_id || result.taskId || "";
        this.status = this.normalizeStatus(result.status || "connecting");
        this.addLog(`后端已创建任务：${this.taskId || "未返回 task_id"}。`);

        if (this.taskId) {
          this.startPolling();
        }
      } catch (error) {
        this.status = "failed";
        this.addLog(`后端调用失败：${error.message}`);
      }
    },

    async refreshBackendState() {
      await Promise.all([this.fetchSystemStatus(), this.fetchKeyStatus()]);
    },

    async fetchSystemStatus() {
      this.backendLoading = true;
      this.backendError = "";
      try {
        const result = await this.requestJson("/api/system/status");
        this.systemStatus = result;
        this.addLog("已同步 Java 系统状态。");
      } catch (error) {
        this.backendError = error.message;
        this.addLog(`系统状态同步失败：${error.message}`);
      } finally {
        this.backendLoading = false;
      }
    },

    async fetchKeyStatus() {
      this.keyLoading = true;
      this.keyError = "";
      try {
        this.keyStatus = await this.requestJson("/api/system/key");
        this.addLog("已同步密钥状态。");
      } catch (error) {
        this.keyError = error.message;
        this.addLog(`密钥状态同步失败：${error.message}`);
      } finally {
        this.keyLoading = false;
      }
    },

    async generateKeyPair() {
      this.keyLoading = true;
      this.keyError = "";
      try {
        await this.requestJson("/api/system/key/generate", { method: "POST" });
        this.addLog("后端已生成密钥对。");
        await this.fetchKeyStatus();
      } catch (error) {
        this.keyError = error.message;
        this.addLog(`生成密钥失败：${error.message}`);
      } finally {
        this.keyLoading = false;
      }
    },

    async deleteKeyPair() {
      this.keyLoading = true;
      this.keyError = "";
      try {
        await this.requestJson("/api/system/key/delete", { method: "POST" });
        this.addLog("后端已删除密钥对。");
        await this.fetchKeyStatus();
      } catch (error) {
        this.keyError = error.message;
        this.addLog(`删除密钥失败：${error.message}`);
      } finally {
        this.keyLoading = false;
      }
    },

    async requestJson(path, options = {}) {
      const response = await fetch(`${this.apiBase}${path}`, {
        headers: options.body ? { "Content-Type": "application/json" } : undefined,
        ...options,
      });
      const text = await response.text();
      let payload = {};
      if (text) {
        try {
          payload = JSON.parse(text);
        } catch (error) {
          throw new Error(`后端返回非 JSON：${text.slice(0, 80)}`);
        }
      }
      if (!response.ok) {
        throw new Error(payload.error || `HTTP ${response.status}`);
      }
      return payload;
    },

    startPolling() {
      this.pollTimer = window.setInterval(async () => {
        try {
          const response = await fetch(`${this.apiBase}/api/send/tasks/${this.taskId}`);
          if (!response.ok) {
            throw new Error(`状态接口返回 ${response.status}`);
          }

          const result = await response.json();
          this.applyBackendStatus(result);
        } catch (error) {
          this.status = "failed";
          this.stopTimers();
          this.addLog(`轮询失败：${error.message}`);
        }
      }, 1000);
    },

    applyBackendStatus(result) {
      this.status = this.normalizeStatus(result.current_stage || result.status || this.status);
      const rawProgress = Number(result.progress ?? this.progress);
      this.progress = rawProgress > 0 && rawProgress <= 1 ? Math.round(rawProgress * 100) : rawProgress;
      this.speedMbps = Number(result.speedMegabytesPerSecond ?? result.speed_mbps ?? result.speedMbps ?? this.speedMbps);
      this.etaSeconds = result.eta_seconds ?? result.etaSeconds ?? this.etaSeconds;
      this.ackedChunks = Number(result.acked_chunks ?? result.transferredBlocks ?? this.ackedChunks);

      if (this.status === "completed" || this.progress >= 100) {
        this.status = "completed";
        this.progress = 100;
        this.stopTimers();
        this.addLog("后端返回传输完成。");
      }
    },

    pauseTransfer() {
      if (!this.isRunning) return;
      this.stopTimers();
      this.status = "paused";
      this.addLog("任务已暂停，后续可按已确认块恢复。");
    },

    resumeTransfer() {
      if (this.status !== "paused") return;
      this.status = "resuming";
      this.addLog("正在恢复任务。");

      if (this.options.demoMode) {
        this.startDemoTransfer();
      } else if (this.taskId) {
        this.startPolling();
      }
    },

    resetTransfer(clearFiles = true) {
      this.stopTimers();
      this.status = "idle";
      this.progress = 0;
      this.speedMbps = 0;
      this.etaSeconds = null;
      this.ackedChunks = 0;
      this.taskId = "";

      if (clearFiles) {
        this.files = [];
        this.localFilePath = "";
        if (this.$refs.fileInput) {
          this.$refs.fileInput.value = "";
        }
      }
    },

    stopTimers() {
      if (this.timer) {
        window.clearInterval(this.timer);
        this.timer = null;
      }
      if (this.pollTimer) {
        window.clearInterval(this.pollTimer);
        this.pollTimer = null;
      }
    },

    fileChunks(size) {
      return Math.max(1, Math.ceil(size / (this.settings.chunkSize * MB)));
    },

    normalizeStatus(status) {
      const normalized = String(status || "").toLowerCase();
      const statusMap = {
        created: "connecting",
        pending: "connecting",
        waiting_for_target: "connecting",
        waiting_for_accept: "waiting_ack",
        waiting_for_receiver: "waiting_ack",
        transferring: "encrypting_chunks",
        encrypting: "encrypting_chunks",
        success: "completed",
        done: "completed",
        completed: "completed",
        complete: "completed",
        error: "failed",
        failed: "failed",
        canceled: "failed",
        cancelled: "failed",
        rejected: "failed",
      };
      return statusMap[normalized] || status;
    },

    formatBytes(bytes) {
      if (!bytes) return "0 B";
      const units = ["B", "KB", "MB", "GB", "TB"];
      const exponent = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
      const value = bytes / 1024 ** exponent;
      return `${value.toFixed(value >= 10 || exponent === 0 ? 0 : 1)} ${units[exponent]}`;
    },

    toBoolean(value) {
      return value === true || value === "true";
    },

    addLog(text) {
      const now = new Date();
      this.logs.unshift({
        id: `${Date.now()}-${Math.random()}`,
        time: now.toLocaleTimeString("zh-CN", { hour12: false }),
        text,
      });
      this.logs = this.logs.slice(0, 8);
    },

    refreshIcons() {
      if (window.lucide) {
        window.lucide.createIcons();
      }
    },
  },
}).mount("#app");
