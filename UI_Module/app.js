const fileInput = document.querySelector("#file-input");
const dropZone = document.querySelector("#drop-zone");
const fileList = document.querySelector("#file-list");
const transferForm = document.querySelector("#transfer-form");
const resetButton = document.querySelector("#reset-button");
const receiveForm = document.querySelector("#receive-form");
const transferTitle = document.querySelector("#transfer-title");
const segments = document.querySelectorAll(".segment");

const progressFill = document.querySelector("#progress-fill");
const progressLabel = document.querySelector("#progress-label");
const progressPercent = document.querySelector("#progress-percent");
const connectionStatus = document.querySelector("#connection-status");
const speed = document.querySelector("#speed");
const eta = document.querySelector("#eta");
const acked = document.querySelector("#acked");
const sessionKey = document.querySelector("#session-key");
const pipelineSteps = document.querySelectorAll(".pipeline-step");

let selectedFiles = [];
let progressTimer = null;

function escapeHtml(value) {
  return value.replace(/[&<>"']/g, (char) => {
    const entities = {
      "&": "&amp;",
      "<": "&lt;",
      ">": "&gt;",
      '"': "&quot;",
      "'": "&#039;",
    };
    return entities[char];
  });
}

function formatBytes(bytes) {
  if (bytes === 0) return "0 B";
  const units = ["B", "KB", "MB", "GB", "TB"];
  const exponent = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
  const value = bytes / 1024 ** exponent;
  return `${value.toFixed(value >= 10 || exponent === 0 ? 0 : 1)} ${units[exponent]}`;
}

function renderFiles() {
  if (!selectedFiles.length) {
    fileList.innerHTML = '<p class="empty-state">还没有选择文件。</p>';
    return;
  }

  fileList.innerHTML = selectedFiles
    .map(
      (file) => `
        <article class="file-item">
          <div>
            <div class="file-name">${escapeHtml(file.name)}</div>
            <div class="file-size">${formatBytes(file.size)}</div>
          </div>
          <div class="file-size">${Math.max(1, Math.ceil(file.size / 1024 / 1024))} 块</div>
        </article>
      `,
    )
    .join("");
}

function updateFiles(files) {
  selectedFiles = Array.from(files);
  renderFiles();
}

function setPipelineStep(index) {
  pipelineSteps.forEach((step, stepIndex) => {
    step.classList.toggle("done", stepIndex < index);
    step.classList.toggle("active", stepIndex === index);
  });
}

function resetProgress() {
  clearInterval(progressTimer);
  progressTimer = null;
  progressFill.style.width = "0%";
  progressLabel.textContent = "等待任务";
  progressPercent.textContent = "0%";
  connectionStatus.textContent = "未连接";
  connectionStatus.classList.remove("online");
  speed.textContent = "0 MB/s";
  eta.textContent = "--";
  acked.textContent = "0";
  sessionKey.textContent = "待协商";
  setPipelineStep(0);
}

function startDemoTransfer() {
  clearInterval(progressTimer);

  const totalBytes = selectedFiles.reduce((sum, file) => sum + file.size, 0);
  const totalChunks = Math.max(1, Math.ceil(totalBytes / 1024 / 1024));
  let progress = 0;

  connectionStatus.textContent = "已连接";
  connectionStatus.classList.add("online");
  progressLabel.textContent = "正在协商密钥";
  sessionKey.textContent = "AES-256";
  setPipelineStep(1);

  progressTimer = setInterval(() => {
    progress = Math.min(100, progress + Math.ceil(Math.random() * 7));
    const chunkCount = Math.min(totalChunks, Math.ceil((progress / 100) * totalChunks));
    const activeStep = progress < 18 ? 1 : progress < 34 ? 2 : progress < 92 ? 3 : 4;

    progressFill.style.width = `${progress}%`;
    progressPercent.textContent = `${progress}%`;
    progressLabel.textContent = progress < 100 ? "正在加密并发送分块" : "传输完成";
    speed.textContent = `${(5 + Math.random() * 18).toFixed(1)} MB/s`;
    eta.textContent = progress < 100 ? `${Math.max(1, Math.ceil((100 - progress) / 12))} s` : "0 s";
    acked.textContent = String(chunkCount);
    setPipelineStep(Math.min(activeStep, 3));

    if (progress === 100) {
      clearInterval(progressTimer);
      progressTimer = null;
      setPipelineStep(3);
      pipelineSteps.forEach((step) => step.classList.add("done"));
      connectionStatus.textContent = "已完成";
    }
  }, 460);
}

fileInput.addEventListener("change", (event) => {
  updateFiles(event.target.files);
});

["dragenter", "dragover"].forEach((eventName) => {
  dropZone.addEventListener(eventName, (event) => {
    event.preventDefault();
    dropZone.classList.add("drag-over");
  });
});

["dragleave", "drop"].forEach((eventName) => {
  dropZone.addEventListener(eventName, (event) => {
    event.preventDefault();
    dropZone.classList.remove("drag-over");
  });
});

dropZone.addEventListener("drop", (event) => {
  updateFiles(event.dataTransfer.files);
});

transferForm.addEventListener("submit", (event) => {
  event.preventDefault();

  if (!selectedFiles.length) {
    progressLabel.textContent = "请先选择至少一个文件";
    return;
  }

  startDemoTransfer();
});

resetButton.addEventListener("click", () => {
  selectedFiles = [];
  fileInput.value = "";
  renderFiles();
  resetProgress();
});

segments.forEach((segment) => {
  segment.addEventListener("click", () => {
    segments.forEach((item) => {
      const isActive = item === segment;
      item.classList.toggle("active", isActive);
      item.setAttribute("aria-selected", String(isActive));
    });

    transferTitle.textContent = segment.dataset.mode === "receive" ? "接收文件" : "发送文件";
    progressLabel.textContent =
      segment.dataset.mode === "receive" ? "输入接收码后查询任务" : "等待任务";
  });
});

receiveForm.addEventListener("submit", (event) => {
  event.preventDefault();
  connectionStatus.textContent = "查询中";
  connectionStatus.classList.add("online");
  progressLabel.textContent = "等待后端返回任务状态";
});

renderFiles();
resetProgress();
