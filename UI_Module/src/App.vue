<template>
  <div class="app-shell">
    <div v-if="sidebarOpen" class="sidebar-backdrop" @click="closeSidebar"></div>
    <aside class="sidebar" :class="{ open: sidebarOpen }" aria-label="App navigation" :aria-hidden="sidebarOpen ? 'false' : 'true'">
      <div class="sidebar-head">
        <div class="brand">
          <span class="brand-mark">SH</span>
          <div>
            <strong>SafeSend</strong>
            <small>{{ t("app.tagline") }}</small>
          </div>
        </div>
        <button class="icon-button subtle sidebar-close" type="button" @click="closeSidebar">
          <i data-lucide="arrow-left" aria-hidden="true"></i>
          <span class="sr-only">{{ t("app.closeNavigation") }}</span>
        </button>
      </div>

      <nav class="side-nav">
        <button
          v-for="item in navigation"
          :key="item.id"
          type="button"
          :class="{ active: activePage === item.id }"
          @click="setPage(item.id)"
        >
          <i :data-lucide="item.icon" aria-hidden="true"></i>
          <span>{{ item.label }}</span>
        </button>
      </nav>
    </aside>

    <div class="app-main">
      <header class="topbar">
        <div class="topbar-main">
          <button class="icon-button sidebar-toggle" type="button" @click="toggleSidebar">
            <SidebarOpenIcon />
            <span class="sr-only">{{ t("app.openNavigation") }}</span>
          </button>
          <div>
            <p class="eyebrow">{{ t("app.eyebrow") }}</p>
            <h1>{{ pageTitle }}</h1>
            <p class="page-intro">{{ pageIntro }}</p>
          </div>
        </div>
        <div class="topbar-actions">
          <button
            v-if="localAccountId"
            class="account-chip"
            type="button"
            @click="copyValue(localAccountId, t('settings.accountId'))"
          >
            <span>{{ t("settings.accountId") }}</span>
            <strong>{{ shortId(localAccountId, 18) }}</strong>
            <i data-lucide="copy" aria-hidden="true"></i>
          </button>
          <button
            v-if="retransmitRequests.length"
            class="icon-button alert"
            type="button"
            @click="toggleRetransmitPanel"
            :title="t('retransmit.openPanel')"
          >
            <i data-lucide="bell-ring" aria-hidden="true"></i>
            <span class="badge-dot">{{ retransmitRequests.length }}</span>
            <span class="sr-only">{{ t("retransmit.openPanel") }}</span>
          </button>
          <button class="icon-button" type="button" @click="toggleDebugPanel" :title="t('debug.open')">
            <i data-lucide="terminal" aria-hidden="true"></i>
            <span class="sr-only">{{ t("debug.open") }}</span>
          </button>
          <button class="icon-button" type="button" @click="toggleSettings">
            <SettingsGearIcon />
            <span class="sr-only">{{ t("settings.open") }}</span>
          </button>
        </div>
      </header>

      <main class="workspace">
        <p v-if="copyFeedback" class="result-note success copy-feedback">{{ copyFeedback }}</p>
        <section v-if="showRetransmitPanel && retransmitRequests.length" class="global-alert-panel" aria-live="polite">
          <div class="panel-heading compact">
            <div>
              <p class="eyebrow">{{ t("retransmit.eyebrow") }}</p>
              <h2>{{ t("retransmit.title") }}</h2>
            </div>
            <button class="icon-button subtle" type="button" @click="showRetransmitPanel = false">
              <i data-lucide="x" aria-hidden="true"></i>
              <span class="sr-only">{{ t("common.cancel") }}</span>
            </button>
          </div>
          <div v-if="retransmitError" class="inline-error">{{ retransmitError }}</div>
          <div class="task-stack">
            <article v-for="request in retransmitRequests" :key="`global-${request.transferId}`" class="task-row simple alert-row">
              <div class="task-main">
                <strong>{{ request.fileName || t("retransmit.unknownFile") }}</strong>
                <small>{{ t("retransmit.fromBlock") }} {{ request.startBlockId ?? 0 }} · {{ request.reason || t("receive.defaultRetransmitReason") }}</small>
                <div class="task-meta-line">
                  <span>{{ t("common.transferId") }}</span>
                  <button class="copy-chip compact" type="button" @click="copyValue(request.transferId, t('common.transferId'))">
                    <span>{{ shortId(request.transferId, 14) }}</span>
                    <i data-lucide="copy" aria-hidden="true"></i>
                    <span class="sr-only">{{ t("common.copy") }}</span>
                  </button>
                </div>
              </div>
              <div class="row-actions">
                <button class="mini-button success" type="button" :disabled="isBusy(`rt-${request.transferId}`)" @click="acceptRetransmit(request.transferId)">
                  {{ t("receive.allowRetransmit") }}
                </button>
                <button class="mini-button danger" type="button" :disabled="isBusy(`rt-${request.transferId}`)" @click="rejectRetransmit(request.transferId)">
                  {{ t("common.reject") }}
                </button>
              </div>
            </article>
          </div>
        </section>

        <section v-if="activePage === 'send'" class="page-grid" aria-label="Send page">
          <article class="panel composer-panel">
            <div class="panel-heading">
              <div>
                <p class="eyebrow">{{ t("send.sectionEyebrow") }}</p>
                <h2>{{ t("send.recipientTitle") }}</h2>
              </div>
              <button class="link-button" type="button" :disabled="isBusy('contacts')" @click="loadContacts">
                {{ t("common.refresh") }}
              </button>
            </div>

            <div class="recipient-picker">
              <label class="field">
                <span>{{ t("send.recipientLabel") }}</span>
                <div class="input-with-action">
                  <input
                    v-model.trim="sendRecipientInput"
                    type="text"
                    :placeholder="t('send.recipientPlaceholder')"
                    autocomplete="off"
                    @input="handleSendRecipientInput"
                    @focus="sendContactDropdownOpen = true"
                  />
                  <button class="icon-button subtle" type="button" @click="sendContactDropdownOpen = !sendContactDropdownOpen">
                    <RecipientToggleIcon :open="sendContactDropdownOpen" />
                    <span class="sr-only">{{ t("send.openContacts") }}</span>
                  </button>
                </div>
              </label>

              <div v-if="sendContactDropdownOpen" class="contact-sheet">
                <div v-if="contactListError" class="inline-error compact">{{ contactListError }}</div>
                <div v-if="filteredSendContacts.length" class="contact-sheet-list">
                  <button
                    v-for="contact in filteredSendContacts"
                    :key="`send-${contact.contactIndex}`"
                    type="button"
                    class="contact-sheet-item"
                    @click="selectSendContact(contact)"
                  >
                    <strong>{{ contact.alias || t("contacts.unnamed") }}</strong>
                    <small>{{ shortId(contact.accountId, 18) }}</small>
                  </button>
                </div>
                <p v-else class="empty-state compact">{{ t("send.noContacts") }}</p>
              </div>

              <p v-if="selectedSendContact" class="helper-text">
                {{ t("send.selectedContact") }} {{ selectedSendContact.alias || shortId(selectedSendContact.accountId, 18) }}
              </p>
            </div>

            <div class="file-box">
              <div
                class="file-dropzone"
                :class="{ active: dragActive, filled: Boolean(chosenFileName) }"
                @dragenter.prevent="handleDragEnter"
                @dragover.prevent="handleDragOver"
                @dragleave.prevent="handleDragLeave"
                @drop.prevent="handleFileDrop"
              >
                <i data-lucide="file-up" aria-hidden="true"></i>
                <div class="file-drop-copy">
                  <strong>{{ chosenFileName || t("send.dropTitle") }}</strong>
                  <small>{{ chosenFileName ? sendForm.filePath : t("send.dropHint") }}</small>
                </div>
              </div>
              <p v-if="dragError" class="inline-error compact">{{ dragError }}</p>

              <div class="file-actions">
                <label class="field grow">
                  <span>{{ t("send.filePathLabel") }}</span>
                  <input
                    v-model.trim="sendForm.filePath"
                    type="text"
                    :placeholder="t('send.filePathPlaceholder')"
                    autocomplete="off"
                    @input="handleFilePathInput"
                  />
                </label>
                <button class="primary-button" type="button" :disabled="isBusy('pick-send-file')" @click="pickSendFile">
                  <i data-lucide="folder-open" aria-hidden="true"></i>
                  {{ t("send.pickFile") }}
                </button>
              </div>
            </div>

            <div class="composer-actions">
              <button class="primary-button" type="button" :disabled="isBusy('send')" @click="sendFile">
                <i data-lucide="send" aria-hidden="true"></i>
                {{ isBusy("send") ? t("send.sending") : t("send.sendNow") }}
              </button>
            </div>

            <p v-if="transferError" class="form-error">{{ transferError }}</p>
          </article>

          <article class="panel progress-panel">
            <div class="panel-heading">
              <div>
                <p class="eyebrow">{{ t("send.progressEyebrow") }}</p>
                <h2>{{ t("send.progressTitle") }}</h2>
              </div>
            </div>

            <div v-if="taskError" class="inline-error">{{ taskError }}</div>
            <div v-if="currentSendTask" class="progress-hero">
              <div class="progress-main">
                <strong>{{ currentSendTask.fileName || t("send.untitledTask") }}</strong>
                <small>{{ statusText(currentSendTask.status) }} · {{ taskSpeedText(currentSendTask) }}</small>
                <div class="task-meta-line">
                  <span>{{ t("common.transferId") }}</span>
                  <button class="copy-chip" type="button" @click="copyValue(taskIdentifier(currentSendTask), t('common.transferId'))">
                    <span>{{ shortId(taskIdentifier(currentSendTask), 14) }}</span>
                    <i data-lucide="copy" aria-hidden="true"></i>
                    <span class="sr-only">{{ t("common.copy") }}</span>
                  </button>
                </div>
                <div class="progress-track large">
                  <span :style="{ width: `${taskProgress(currentSendTask)}%` }"></span>
                </div>
              </div>
              <div class="progress-side">
                <span class="progress-number">{{ taskProgress(currentSendTask) }}%</span>
                <button
                  v-if="isSendTask(currentSendTask)"
                  class="mini-button danger"
                  type="button"
                  :disabled="isBusy(taskIdentifier(currentSendTask)) || isTerminalTask(currentSendTask)"
                  @click="cancelSendTask(currentSendTask)"
                >
                  {{ t("send.cancelTask") }}
                </button>
              </div>
            </div>
            <div v-else class="empty-state">{{ t("send.noProgress") }}</div>

            <div v-if="recentSendTasks.length" class="task-stack">
              <article v-for="task in recentSendTasks" :key="task.taskId || task.transferId" class="task-row simple">
                <div class="task-main">
                  <strong>{{ task.fileName || t("send.untitledTask") }}</strong>
                  <small>{{ statusText(task.status) }} · {{ taskProgress(task) }}%</small>
                  <div class="task-meta-line">
                    <span>{{ t("common.transferId") }}</span>
                    <button class="copy-chip compact" type="button" @click="copyValue(taskIdentifier(task), t('common.transferId'))">
                      <span>{{ shortId(taskIdentifier(task), 14) }}</span>
                      <i data-lucide="copy" aria-hidden="true"></i>
                      <span class="sr-only">{{ t("common.copy") }}</span>
                    </button>
                  </div>
                  <div class="progress-track">
                    <span :style="{ width: `${taskProgress(task)}%` }"></span>
                  </div>
                </div>
              </article>
            </div>
          </article>
        </section>

        <section v-else-if="activePage === 'receive'" class="page-grid" aria-label="Receive page">
          <article class="panel progress-panel">
            <div class="panel-heading">
              <div>
                <p class="eyebrow">{{ t("receive.progressEyebrow") }}</p>
                <h2>{{ t("receive.progressTitle") }}</h2>
              </div>
              <button class="link-button" type="button" :disabled="isBusy('tasks')" @click="refreshReceivePage()">
                {{ t("common.refresh") }}
              </button>
            </div>

            <div v-if="taskError" class="inline-error">{{ taskError }}</div>
            <div v-if="currentReceiveTask" class="progress-hero">
              <div class="progress-main">
                <strong>{{ currentReceiveTask.fileName || t("receive.untitledTask") }}</strong>
                <small>{{ statusText(currentReceiveTask.status) }} · {{ taskSpeedText(currentReceiveTask) }}</small>
                <div class="task-meta-line">
                  <span>{{ t("common.transferId") }}</span>
                  <button class="copy-chip" type="button" @click="copyValue(taskIdentifier(currentReceiveTask), t('common.transferId'))">
                    <span>{{ shortId(taskIdentifier(currentReceiveTask), 14) }}</span>
                    <i data-lucide="copy" aria-hidden="true"></i>
                    <span class="sr-only">{{ t("common.copy") }}</span>
                  </button>
                </div>
                <div class="progress-track large">
                  <span :style="{ width: `${taskProgress(currentReceiveTask)}%` }"></span>
                </div>
              </div>
              <div class="progress-side">
                <span class="progress-number">{{ taskProgress(currentReceiveTask) }}%</span>
                <button
                  class="mini-button"
                  type="button"
                  :disabled="isBusy(taskIdentifier(currentReceiveTask))"
                  @click="requestRetransmit(currentReceiveTask)"
                >
                  {{ t("receive.requestRetransmit") }}
                </button>
                <button
                  class="mini-button danger"
                  type="button"
                  :disabled="isBusy(`reject-${taskIdentifier(currentReceiveTask)}`)"
                  @click="rejectReceiveTask(currentReceiveTask)"
                >
                  {{ t("receive.cancelReceive") }}
                </button>
              </div>
            </div>
            <div v-else class="empty-state">{{ t("receive.noProgress") }}</div>
          </article>

          <article class="panel">
            <div class="panel-heading">
              <div>
                <p class="eyebrow">{{ t("receive.incomingEyebrow") }}</p>
                <h2>{{ t("receive.incomingTitle") }}</h2>
              </div>
              <button class="link-button" type="button" :disabled="isBusy('incoming')" @click="loadIncoming">
                {{ t("common.refresh") }}
              </button>
            </div>

            <div v-if="incomingError" class="inline-error">{{ incomingError }}</div>
            <div v-if="!incomingRequests.length" class="empty-state">{{ t("receive.noIncoming") }}</div>
            <div v-else class="task-stack">
              <article v-for="request in incomingRequests" :key="request.transferId" class="task-row simple">
                <div class="task-main">
                  <strong>{{ request.fileName || t("receive.untitledTask") }}</strong>
                  <small>{{ formatBytes(request.fileSize) }}</small>
                  <div class="task-meta-line">
                    <span>{{ t("common.transferId") }}</span>
                    <button class="copy-chip compact" type="button" @click="copyValue(request.transferId, t('common.transferId'))">
                      <span>{{ shortId(request.transferId, 14) }}</span>
                      <i data-lucide="copy" aria-hidden="true"></i>
                      <span class="sr-only">{{ t("common.copy") }}</span>
                    </button>
                  </div>
                </div>
                <div class="row-actions">
                  <button class="mini-button success" type="button" :disabled="isBusy(request.transferId)" @click="acceptIncoming(request.transferId)">
                    {{ t("receive.accept") }}
                  </button>
                  <button class="mini-button danger" type="button" :disabled="isBusy(request.transferId)" @click="rejectIncoming(request.transferId)">
                    {{ t("receive.reject") }}
                  </button>
                </div>
              </article>
            </div>
          </article>

          <article class="panel">
            <div class="panel-heading">
              <div>
                <p class="eyebrow">{{ t("receive.historyEyebrow") }}</p>
                <h2>{{ t("receive.historyTitle") }}</h2>
              </div>
              <button
                v-if="completedReceiveTasks.length > 3"
                class="link-button"
                type="button"
                @click="receiveHistoryExpanded = !receiveHistoryExpanded"
              >
                {{ receiveHistoryExpanded ? t("receive.collapseHistory") : t("receive.viewMoreHistory") }}
              </button>
            </div>

            <div v-if="!completedReceiveTasks.length" class="empty-state">{{ t("receive.noHistory") }}</div>
            <div v-else class="task-stack">
              <article v-for="task in visibleReceiveHistoryTasks" :key="taskIdentifier(task)" class="task-row simple">
                <div class="task-main">
                  <strong>{{ task.fileName || t("receive.untitledTask") }}</strong>
                  <small>{{ formatBytes(task.totalBytes) }} · {{ t("receive.historyBlocks") }} {{ task.totalBlocks || 0 }}</small>
                  <small>{{ t("receive.historyReceivedAt") }} {{ receiveHistoryTime(task) }}</small>
                  <div class="task-meta-line">
                    <span>{{ t("common.transferId") }}</span>
                    <button class="copy-chip compact" type="button" @click="copyValue(taskIdentifier(task), t('common.transferId'))">
                      <span>{{ shortId(taskIdentifier(task), 14) }}</span>
                      <i data-lucide="copy" aria-hidden="true"></i>
                      <span class="sr-only">{{ t("common.copy") }}</span>
                    </button>
                  </div>
                </div>
                <div class="row-actions">
                  <button
                    class="mini-button"
                    type="button"
                    :disabled="isBusy(`open-${taskIdentifier(task)}`)"
                    @click="openReceived(task)"
                  >
                    <i data-lucide="folder-open" aria-hidden="true"></i>
                    {{ t("receive.openFolder") }}
                  </button>
                </div>
              </article>
            </div>
          </article>

          <article class="panel">
            <div class="panel-heading">
              <div>
                <p class="eyebrow">{{ t("receive.advancedEyebrow") }}</p>
                <h2>{{ t("receive.advancedTitle") }}</h2>
              </div>
            </div>

            <div class="receive-action-panel">
              <label class="field">
                <span>{{ t("receive.transferId") }}</span>
                <input
                  v-model.trim="receiveForm.transferId"
                  type="text"
                  :placeholder="t('receive.transferIdPlaceholder')"
                  autocomplete="off"
                />
              </label>
              <div class="action-bar">
                <button class="mini-button success" type="button" :disabled="manualReceiveDisabled" @click="manualAcceptReceive">
                  {{ t("receive.accept") }}
                </button>
                <button class="mini-button danger" type="button" :disabled="manualReceiveDisabled" @click="manualRejectReceive">
                  {{ t("receive.reject") }}
                </button>
                <button class="mini-button" type="button" :disabled="manualReceiveDisabled" @click="manualRequestRetransmit">
                  {{ t("receive.requestRetransmit") }}
                </button>
                <button class="mini-button" type="button" :disabled="manualReceiveDisabled" @click="manualOpenReceived">
                  {{ t("receive.openFolder") }}
                </button>
              </div>
            </div>
          </article>

          <article class="panel">
            <div class="panel-heading">
              <div>
                <p class="eyebrow">{{ t("receive.retransmitEyebrow") }}</p>
                <h2>{{ t("receive.retransmitTitle") }}</h2>
              </div>
              <button class="link-button" type="button" :disabled="isBusy('retransmit')" @click="loadRetransmitRequests">
                {{ t("common.refresh") }}
              </button>
            </div>

            <div v-if="retransmitError" class="inline-error">{{ retransmitError }}</div>
            <div v-if="!retransmitRequests.length" class="empty-state">{{ t("receive.noRetransmitRequests") }}</div>
            <div v-else class="task-stack">
              <article v-for="request in retransmitRequests" :key="request.transferId" class="task-row simple">
                <div class="task-main">
                  <strong>{{ t("receive.retransmitRequestLabel") }}</strong>
                  <small>{{ request.reason || t("receive.defaultRetransmitReason") }}</small>
                  <div class="task-meta-line">
                    <span>{{ t("common.transferId") }}</span>
                    <button class="copy-chip compact" type="button" @click="copyValue(request.transferId, t('common.transferId'))">
                      <span>{{ shortId(request.transferId, 14) }}</span>
                      <i data-lucide="copy" aria-hidden="true"></i>
                      <span class="sr-only">{{ t("common.copy") }}</span>
                    </button>
                  </div>
                </div>
                <div class="row-actions">
                  <button class="mini-button success" type="button" :disabled="isBusy(`rt-${request.transferId}`)" @click="acceptRetransmit(request.transferId)">
                    {{ t("receive.allowRetransmit") }}
                  </button>
                  <button class="mini-button danger" type="button" :disabled="isBusy(`rt-${request.transferId}`)" @click="rejectRetransmit(request.transferId)">
                    {{ t("common.reject") }}
                  </button>
                </div>
              </article>
            </div>
          </article>
        </section>

        <section v-else class="page-grid" aria-label="Contacts page">
          <article class="panel contact-shell">
            <div class="panel-heading">
              <div>
                <p class="eyebrow">{{ contactsSubView === "list" ? t("contacts.eyebrow") : t("blacklist.eyebrow") }}</p>
                <h2>{{ contactsSubView === "list" ? t("contacts.title") : t("blacklist.title") }}</h2>
              </div>
              <div class="action-bar">
                <button
                  v-if="contactsSubView === 'list'"
                  class="link-button"
                  type="button"
                  :disabled="isBusy('blacklist')"
                  @click="switchContactsSubView('blacklist')"
                >
                  {{ t("contacts.viewBlacklist") }}
                </button>
                <button
                  v-else
                  class="link-button"
                  type="button"
                  :disabled="isBusy('contacts')"
                  @click="switchContactsSubView('list')"
                >
                  {{ t("contacts.backToContacts") }}
                </button>
                <button class="icon-button solid" type="button" @click="contactsSubView === 'list' ? openContactCreateModal() : openBlacklistCreateModal()">
                  <span>+</span>
                </button>
              </div>
            </div>

            <div v-if="contactsSubView === 'list'" class="list-shell">
              <div v-if="contactListError" class="inline-error">{{ contactListError }}</div>
              <div v-if="!contacts.length" class="empty-state">{{ t("contacts.empty") }}</div>
              <div v-else class="contact-stack">
                <article v-for="contact in contacts" :key="contact.contactIndex" class="contact-row">
                  <div class="contact-avatar">{{ initials(contact.alias || contact.accountId) }}</div>
                  <div class="contact-main">
                    <strong>{{ contact.alias || t("contacts.unnamed") }}</strong>
                    <small>{{ shortId(contact.accountId, 18) }}</small>
                  </div>
                  <div class="contact-actions">
                    <button class="menu-button" type="button" @click="toggleMenu('contact', contact.contactIndex)">...</button>
                    <div v-if="isMenuOpen('contact', contact.contactIndex)" class="menu-sheet">
                      <button type="button" @click="openContactEditModal(contact)">{{ t("contacts.menuEdit") }}</button>
                      <button type="button" @click="blacklistContact(contact)">{{ t("contacts.menuBlock") }}</button>
                      <button type="button" @click="removeContact(contact.contactIndex)">{{ t("contacts.menuDelete") }}</button>
                    </div>
                  </div>
                </article>
              </div>
            </div>

            <div v-else class="list-shell">
              <div v-if="blacklistListError" class="inline-error">{{ blacklistListError }}</div>
              <div v-if="!blacklist.length" class="empty-state">{{ t("blacklist.empty") }}</div>
              <div v-else class="contact-stack">
                <article v-for="record in blacklist" :key="record.accountId" class="contact-row">
                  <div class="contact-avatar blocked">{{ initials(record.accountId) }}</div>
                  <div class="contact-main">
                    <strong>{{ shortId(record.accountId, 20) }}</strong>
                    <small>{{ record.reason || t("blacklist.noReason") }}</small>
                  </div>
                  <div class="contact-actions">
                    <button class="menu-button" type="button" @click="toggleMenu('blacklist', record.accountId)">...</button>
                    <div v-if="isMenuOpen('blacklist', record.accountId)" class="menu-sheet">
                      <button type="button" @click="removeBlacklist(record.accountId)">{{ t("blacklist.menuRemove") }}</button>
                    </div>
                  </div>
                </article>
              </div>
            </div>
          </article>
        </section>
      </main>
    </div>

    <div v-if="showDebugPanel" class="drawer-backdrop" @click="closeDebugPanel"></div>
    <aside v-if="showDebugPanel" class="settings-drawer debug-drawer" aria-label="Debug drawer">
      <button class="drawer-close-tab" type="button" @click="closeDebugPanel">
        &gt;
        <span class="sr-only">{{ t("debug.close") }}</span>
      </button>
      <div class="drawer-head">
        <div>
          <p class="eyebrow">{{ t("debug.eyebrow") }}</p>
          <h2>{{ t("debug.title") }}</h2>
        </div>
      </div>

      <section class="drawer-section">
        <div class="drawer-section-head">
          <h3>{{ t("debug.actions") }}</h3>
        </div>
        <div class="drawer-actions">
          <button class="primary-button" type="button" :disabled="isBusy('debug-devtools')" @click="openDevTools">
            <i data-lucide="terminal" aria-hidden="true"></i>
            {{ t("debug.openDevTools") }}
          </button>
          <button class="mini-button" type="button" :disabled="isBusy('debug-logs')" @click="openLogsFolder">
            <i data-lucide="folder-open" aria-hidden="true"></i>
            {{ t("debug.openLogs") }}
          </button>
          <button class="mini-button" type="button" :disabled="isBusy('debug-status')" @click="openSystemStatus">
            <i data-lucide="activity" aria-hidden="true"></i>
            {{ t("debug.openStatus") }}
          </button>
          <button class="mini-button" type="button" @click="copyDebugInfo">
            <i data-lucide="copy" aria-hidden="true"></i>
            {{ t("debug.copyInfo") }}
          </button>
        </div>
        <p v-if="debugError" class="form-error">{{ debugError }}</p>
      </section>

      <section class="drawer-section">
        <div class="drawer-section-head">
          <h3>{{ t("debug.info") }}</h3>
          <button class="link-button" type="button" :disabled="isBusy('debug-info')" @click="loadDebugInfo">
            {{ t("common.refresh") }}
          </button>
        </div>
        <div v-if="debugInfo" class="debug-info-list">
          <div v-for="item in debugInfoItems" :key="item.label" class="debug-info-row">
            <span>{{ item.label }}</span>
            <button class="copy-value-button" type="button" @click="copyValue(item.value, item.label)">
              <strong>{{ item.value || "--" }}</strong>
              <i data-lucide="copy" aria-hidden="true"></i>
            </button>
          </div>
        </div>
        <p v-else class="empty-state">{{ t("debug.noInfo") }}</p>
      </section>
    </aside>

    <div v-if="showSettingsDrawer" class="drawer-backdrop" @click="closeSettings"></div>
    <aside v-if="showSettingsDrawer" class="settings-drawer" aria-label="Settings drawer">
      <button class="drawer-close-tab" type="button" @click="closeSettings">
        &gt;
        <span class="sr-only">{{ t("settings.close") }}</span>
      </button>
      <div class="drawer-head">
        <div>
          <p class="eyebrow">{{ t("settings.eyebrow") }}</p>
          <h2>{{ t("settings.title") }}</h2>
        </div>
      </div>

      <section class="drawer-section">
        <div class="drawer-section-head">
          <h3>{{ t("settings.statusTitle") }}</h3>
          <button class="link-button" type="button" :disabled="isBusy('dashboard')" @click="loadDashboard">
            {{ t("settings.refreshStatus") }}
          </button>
        </div>
        <div v-if="dashboardError" class="inline-error">{{ friendlyDashboardError }}</div>
          <div class="status-card-list">
            <article class="status-card mini">
              <span>{{ t("settings.localStatus") }}</span>
              <strong>{{ localStatusText }}</strong>
              <small>{{ localStatusHint }}</small>
          </article>
          <article class="status-card mini">
            <span>{{ t("settings.serverStatus") }}</span>
            <strong>{{ serverStatusText }}</strong>
            <small>{{ serverStatusHint }}</small>
          </article>
          <article class="status-card mini">
              <span>{{ t("settings.keyStatus") }}</span>
              <strong>{{ keyReadyText }}</strong>
              <small>{{ keyReadyHint }}</small>
            </article>
          </div>
        </section>

      <section class="drawer-section">
        <div class="drawer-section-head">
          <h3>{{ t("settings.languageTitle") }}</h3>
        </div>
        <div class="segmented-control wide">
          <button type="button" :class="{ active: language === 'zh-CN' }" @click="setLanguage('zh-CN')">中文</button>
          <button type="button" :class="{ active: language === 'en-US' }" @click="setLanguage('en-US')">English</button>
        </div>
      </section>

      <section class="drawer-section">
        <div class="drawer-section-head">
          <h3>{{ t("settings.keyTitle") }}</h3>
        </div>
        <div class="drawer-key-summary">
          <strong>{{ keyReadyText }}</strong>
          <small>{{ keyReadyHint }}</small>
        </div>

        <div class="drawer-actions">
          <button class="primary-button" type="button" :disabled="isBusy('key-generate')" @click="generateKey">
            {{ t("settings.generateKey") }}
          </button>
          <button class="mini-button danger" type="button" :disabled="isBusy('key-delete')" @click="deleteKey">
            {{ t("settings.deleteKey") }}
          </button>
        </div>

        <div class="segmented-control wide">
          <button type="button" :class="{ active: keyImportMode === 'paste' }" @click="setKeyImportMode('paste')">
            {{ t("settings.importTextMode") }}
          </button>
          <button type="button" :class="{ active: keyImportMode === 'file' }" @click="setKeyImportMode('file')">
            {{ t("settings.importFileMode") }}
          </button>
        </div>

        <form v-if="keyImportMode === 'paste'" class="stack-form" @submit.prevent="importPrivateKeyText">
          <label class="field">
            <span>{{ t("settings.privateKeyText") }}</span>
            <textarea v-model.trim="keyForm.privateKey" rows="6" :placeholder="t('settings.privateKeyTextPlaceholder')"></textarea>
          </label>
          <button class="primary-button" type="submit" :disabled="isBusy('key-import')">
            {{ t("settings.importTextAction") }}
          </button>
        </form>

        <form v-else class="stack-form" @submit.prevent="importPrivateKeyFile">
          <label class="field">
            <span>{{ t("settings.privateKeyPath") }}</span>
            <input
              v-model.trim="keyForm.privateKeyPath"
              type="text"
              :placeholder="t('settings.privateKeyPathPlaceholder')"
              autocomplete="off"
            />
          </label>
          <div class="drawer-actions">
            <button class="link-button" type="button" :disabled="isBusy('pick-key-file')" @click="pickPrivateKeyFile">
              {{ t("settings.pickKeyFile") }}
            </button>
            <button class="primary-button" type="submit" :disabled="isBusy('key-import-file')">
              {{ t("settings.importFileAction") }}
            </button>
          </div>
        </form>

        <p v-if="keyActionSuccess" class="result-note success">{{ keyActionSuccess }}</p>
        <p v-if="keyActionError" class="form-error">{{ keyActionError }}</p>
      </section>
    </aside>

    <div v-if="showContactModal" class="modal-backdrop" @click.self="closeContactModal">
      <section class="modal-card">
        <div class="panel-heading">
          <div>
            <p class="eyebrow">{{ contactModalMode === 'blacklist' ? t("blacklist.eyebrow") : t("contacts.eyebrow") }}</p>
            <h2>{{ contactModalTitle }}</h2>
          </div>
          <button class="icon-button subtle" type="button" @click="closeContactModal">
            <i data-lucide="x" aria-hidden="true"></i>
          </button>
        </div>

        <form class="stack-form" @submit.prevent="submitContactModal">
          <label class="field">
            <span>{{ t("contacts.accountId") }}</span>
            <input
              v-model.trim="contactDraft.accountId"
              type="text"
              :placeholder="t('contacts.accountPlaceholder')"
              :disabled="contactModalMode === 'edit'"
            />
          </label>

          <template v-if="contactModalMode !== 'blacklist'">
            <label class="field">
              <span>{{ t("contacts.alias") }}</span>
              <input v-model.trim="contactDraft.alias" type="text" :placeholder="t('contacts.aliasPlaceholder')" />
            </label>
            <label class="field">
              <span>{{ t("contacts.publicKey") }}</span>
              <textarea v-model.trim="contactDraft.publicKey" rows="4" :placeholder="t('contacts.publicKeyPlaceholder')"></textarea>
            </label>
          </template>

          <template v-else>
            <label class="field">
              <span>{{ t("blacklist.reason") }}</span>
              <input v-model.trim="contactDraft.reason" type="text" :placeholder="t('blacklist.reasonPlaceholder')" />
            </label>
            <label class="field">
              <span>{{ t("contacts.publicKey") }}</span>
              <textarea v-model.trim="contactDraft.publicKey" rows="4" :placeholder="t('blacklist.publicKeyPlaceholder')"></textarea>
            </label>
          </template>

          <div v-if="contactModalMode === 'create'" class="drawer-actions">
            <button class="link-button" type="button" :disabled="!contactDraft.accountId || isBusy('search-user')" @click="searchUserFromModal">
              {{ t("contacts.searchUser") }}
            </button>
            <button class="link-button" type="button" :disabled="!contactDraft.accountId || isBusy('search-add')" @click="searchUserAndAddFromModal">
              {{ t("contacts.searchAndAdd") }}
            </button>
          </div>

          <p v-if="searchResult" class="result-note" :class="{ success: searchResult.found }">{{ searchResultNote }}</p>
          <p v-if="contactModalError" class="form-error">{{ contactModalError }}</p>

          <div class="drawer-actions">
            <button class="link-button" type="button" @click="closeContactModal">{{ t("common.cancel") }}</button>
            <button class="primary-button" type="submit" :disabled="isBusy('save-contact')">
              {{ contactModalSubmitText }}
            </button>
          </div>
        </form>
      </section>
    </div>
  </div>
</template>

<script>
import { createIcons } from "lucide";
import RecipientToggleIcon from "./components/RecipientToggleIcon.vue";
import SidebarOpenIcon from "./components/SidebarOpenIcon.vue";
import SettingsGearIcon from "./components/SettingsGearIcon.vue";
import {
  extractFileNameFromPath,
  extractLocalFileSelection,
  hasDesktopDebugApi,
  hasLocalKeyPair,
  isActiveReceiveTask,
  isTerminalTaskStatus,
  normalizeRetransmitRequests,
  receiveHistoryTasks,
  shortId as formatShortId,
  taskIdentifier as getTaskIdentifier,
  taskSpeedText as formatTaskSpeedText,
  toFriendlyErrorMessage,
} from "./ui-state.js";

const DEFAULT_API_BASE = window.location.port === "5173" ? "" : window.location.origin;

const MESSAGES = {
  "zh-CN": {
    app: {
      eyebrow: "安全文件传输",
      tagline: "Hybrid Transfer",
      openNavigation: "打开导航",
      closeNavigation: "收起导航",
    },
    nav: {
      send: "发送",
      receive: "接收",
      contacts: "联系人",
    },
    common: {
      refresh: "刷新",
      cancel: "取消",
      reject: "拒绝",
      copy: "复制",
      copied: "已复制",
      transferId: "传输编号",
    },
    retransmit: {
      eyebrow: "补传提醒",
      title: "收到补传请求",
      openPanel: "查看补传请求",
      unknownFile: "未知文件",
      fromBlock: "起始分块",
    },
    debug: {
      eyebrow: "调试",
      title: "调试面板",
      open: "打开调试面板",
      close: "关闭调试面板",
      actions: "常用操作",
      openDevTools: "打开控制台",
      openLogs: "打开日志文件夹",
      openStatus: "打开状态页面",
      copyInfo: "复制诊断信息",
      info: "诊断信息",
      noInfo: "暂无诊断信息。",
      desktopOnly: "调试入口仅桌面版可用。",
      version: "版本",
      platform: "系统",
      mode: "运行模式",
      packaged: "安装包",
      development: "开发",
      healthUrl: "状态地址",
      logDir: "日志目录",
      userDataDir: "数据目录",
      downloadDir: "下载目录",
      runtimeRoot: "运行时目录",
    },
    send: {
      title: "发送",
      intro: "选择接收对象，拖入或选择文件，然后直接发送。",
      sectionEyebrow: "发送对象",
      recipientTitle: "接收方",
      recipientLabel: "联系人或 accountId",
      recipientPlaceholder: "输入备注名、contact-N 或 accountId",
      openContacts: "展开联系人",
      selectedContact: "当前联系人：",
      noContacts: "没有匹配的联系人。",
      dropTitle: "拖入文件到这里",
      dropHint: "拖入文件到这里，或点击选择本地文件",
      filePathLabel: "本地文件路径",
      filePathPlaceholder: "例如：C:\\Users\\15328\\Desktop\\demo.zip",
      pickFile: "选择文件",
      sendNow: "开始发送",
      sending: "发送中",
      progressEyebrow: "发送进度",
      progressTitle: "当前发送任务",
      noProgress: "暂无发送任务。",
      untitledTask: "未命名任务",
      cancelTask: "取消发送",
    },
    receive: {
      title: "接收",
      intro: "查看待接收文件、处理补传请求，并跟踪接收进度。",
      progressEyebrow: "接收进度",
      progressTitle: "当前接收任务",
      noProgress: "暂无接收任务。",
      incomingEyebrow: "待接收文件",
      incomingTitle: "待接收列表",
      noIncoming: "暂无待接收文件。",
      historyEyebrow: "接收历史",
      historyTitle: "最近接收",
      noHistory: "暂无接收历史。",
      viewMoreHistory: "查看更多",
      collapseHistory: "收起",
      historyReceivedAt: "接收时间",
      historyBlocks: "块数",
      advancedEyebrow: "高级操作",
      advancedTitle: "手动处理",
      transferId: "传输编号",
      transferIdPlaceholder: "用于补传或打开位置，可从任务编号复制",
      accept: "接受接收",
      reject: "拒绝接收",
      requestRetransmit: "请求补传",
      cancelReceive: "取消接收",
      openFolder: "打开位置",
      retransmitEyebrow: "补传请求",
      retransmitTitle: "待处理补传",
      noRetransmitRequests: "暂无补传请求。",
      retransmitRequestLabel: "对方请求补传",
      defaultRetransmitReason: "重新发送缺失部分",
      allowRetransmit: "允许补传",
      untitledTask: "未命名任务",
    },
    contacts: {
      title: "联系人",
      intro: "像聊天软件一样管理常用对象和身份信息。",
      eyebrow: "联系人",
      viewBlacklist: "黑名单",
      backToContacts: "返回联系人",
      addTitle: "添加联系人",
      editTitle: "修改联系人",
      empty: "暂无联系人。",
      unnamed: "未命名联系人",
      accountId: "账号",
      accountPlaceholder: "输入接收方账号",
      alias: "备注名",
      aliasPlaceholder: "例如：组员 A",
      publicKey: "公钥（可选）",
      publicKeyPlaceholder: "留空时后端会尝试从服务器补全",
      saveCreate: "保存联系人",
      saveEdit: "保存修改",
      searchUser: "搜索在线用户",
      searchAndAdd: "搜索并添加",
      menuEdit: "修改信息",
      menuDelete: "删除联系人",
      menuBlock: "移入黑名单",
    },
    blacklist: {
      title: "屏蔽名单",
      eyebrow: "黑名单",
      addTitle: "添加屏蔽账号",
      empty: "暂无屏蔽记录。",
      reason: "原因（可选）",
      reasonPlaceholder: "例如：测试拒绝对象",
      publicKeyPlaceholder: "可留空",
      saveCreate: "加入黑名单",
      menuRemove: "移出黑名单",
      noReason: "未填写原因",
    },
    settings: {
      eyebrow: "设置",
      title: "设置",
      open: "打开设置",
      close: "收起设置",
      statusTitle: "状态检测",
      refreshStatus: "刷新状态",
      localStatus: "本机服务",
      serverStatus: "服务器连接",
      keyStatus: "密钥状态",
      accountId: "本机账号",
      accountIdHint: "点击复制完整 accountID",
      accountIdMissing: "生成密钥后会显示 accountID",
      languageTitle: "语言切换",
      keyTitle: "密钥管理",
      generateKey: "重新生成密钥",
      deleteKey: "删除密钥",
      importTextMode: "粘贴私钥",
      importFileMode: "选择私钥文件",
      privateKeyText: "私钥文本",
      privateKeyTextPlaceholder: "粘贴 PEM 私钥内容",
      privateKeyPath: "私钥文件路径",
      privateKeyPathPlaceholder: "例如：C:\\Users\\15328\\Desktop\\private_key.pem",
      pickKeyFile: "选择私钥文件",
      importTextAction: "导入私钥",
      importFileAction: "导入私钥文件",
    },
    status: {
      localReady: "本机已就绪",
      localNeeds: "需要处理",
      localPending: "未同步",
      localWaiting: "正在等待本机服务",
      localError: "服务暂时不可用",
      serverConnected: "已连接",
      serverDisconnected: "未连接",
      serverConnectedHint: "可以向在线设备发送文件",
      serverDisconnectedHint: "可先管理联系人，发送前再连接",
      keyReady: "密钥正常",
      keyMissing: "缺少密钥",
      keyPending: "未同步",
      keyPendingHint: "正在读取密钥状态",
      keyMissingHint: "请先生成或导入本机密钥",
      keyReadyHint: "安全传输已准备好",
    },
    tasks: {
      send: "发送",
      receive: "接收",
      CREATED: "已创建",
      PENDING: "等待中",
      WAITING_FOR_ACCEPT: "等待接收",
      TRANSFERRING: "传输中",
      COMPLETED: "已完成",
      FAILED: "失败",
      CANCELED: "已取消",
      CANCELLED: "已取消",
      REJECTED: "已拒绝",
      unknown: "未知",
    },
    errors: {
      browserPath: "当前浏览器模式不支持直接读取本地路径，请手动填写，或在桌面版中使用选择文件。",
      recipientRequired: "请选择联系人或填写 accountId。",
      fileRequired: "请先选择文件或填写本地路径。",
      transferIdRequired: "请先填写传输编号。",
      keyTextRequired: "请先粘贴私钥内容。",
      keyPathRequired: "请先填写私钥文件路径。",
      contactAccountRequired: "请填写账号。",
      blacklistAccountRequired: "请填写要屏蔽的账号。",
    },
    info: {
      keyGenerated: "密钥已生成。",
      keyImported: "私钥已导入。",
      keyFileImported: "私钥文件已导入。",
      keyDeleted: "密钥已删除。",
      contactSaved: "联系人已保存。",
      blacklistSaved: "已加入黑名单。",
    },
  },
  "en-US": {
    app: {
      eyebrow: "Safe File Transfer",
      tagline: "Hybrid Transfer",
      openNavigation: "Open navigation",
      closeNavigation: "Collapse navigation",
    },
    nav: {
      send: "Send",
      receive: "Receive",
      contacts: "Contacts",
    },
    common: {
      refresh: "Refresh",
      cancel: "Cancel",
      reject: "Reject",
      copy: "Copy",
      copied: "Copied",
      transferId: "Transfer ID",
    },
    retransmit: {
      eyebrow: "Retransmit alert",
      title: "Retransmit request received",
      openPanel: "View retransmit requests",
      unknownFile: "Unknown file",
      fromBlock: "From block",
    },
    debug: {
      eyebrow: "Debug",
      title: "Debug panel",
      open: "Open debug panel",
      close: "Close debug panel",
      actions: "Actions",
      openDevTools: "Open console",
      openLogs: "Open logs folder",
      openStatus: "Open status page",
      copyInfo: "Copy diagnostics",
      info: "Diagnostics",
      noInfo: "No diagnostics yet.",
      desktopOnly: "Debug tools are only available in the desktop app.",
      version: "Version",
      platform: "Platform",
      mode: "Mode",
      packaged: "Packaged",
      development: "Development",
      healthUrl: "Status URL",
      logDir: "Log folder",
      userDataDir: "Data folder",
      downloadDir: "Download folder",
      runtimeRoot: "Runtime folder",
    },
    send: {
      title: "Send",
      intro: "Pick a recipient, choose a file, and start the transfer.",
      sectionEyebrow: "Recipient",
      recipientTitle: "Send to",
      recipientLabel: "Contact or accountId",
      recipientPlaceholder: "Search alias, contact-N, or accountId",
      openContacts: "Open contacts",
      selectedContact: "Selected contact:",
      noContacts: "No matching contacts.",
      dropTitle: "Drop a file here",
      dropHint: "Drop a file here or choose one from your computer",
      filePathLabel: "Local file path",
      filePathPlaceholder: "Example: C:\\Users\\15328\\Desktop\\demo.zip",
      pickFile: "Choose file",
      sendNow: "Start send",
      sending: "Sending",
      progressEyebrow: "Transfer progress",
      progressTitle: "Current send task",
      noProgress: "No send tasks yet.",
      untitledTask: "Untitled task",
      cancelTask: "Cancel send",
    },
    receive: {
      title: "Receive",
      intro: "Handle incoming files, retransmit requests, and receive progress.",
      progressEyebrow: "Receive progress",
      progressTitle: "Current receive task",
      noProgress: "No receive task right now.",
      incomingEyebrow: "Incoming files",
      incomingTitle: "Incoming queue",
      noIncoming: "No incoming files.",
      historyEyebrow: "Receive history",
      historyTitle: "Recent received files",
      noHistory: "No receive history yet.",
      viewMoreHistory: "View more",
      collapseHistory: "Collapse",
      historyReceivedAt: "Received",
      historyBlocks: "Blocks",
      advancedEyebrow: "Advanced actions",
      advancedTitle: "Manual handling",
      transferId: "Transfer ID",
      transferIdPlaceholder: "Copy it from a task card for retransmit or open location",
      accept: "Accept",
      reject: "Reject",
      requestRetransmit: "Request retransmit",
      cancelReceive: "Cancel receive",
      openFolder: "Open location",
      retransmitEyebrow: "Retransmit requests",
      retransmitTitle: "Pending retransmits",
      noRetransmitRequests: "No retransmit requests.",
      retransmitRequestLabel: "Peer requested retransmit",
      defaultRetransmitReason: "Resend missing parts",
      allowRetransmit: "Allow retransmit",
      untitledTask: "Untitled task",
    },
    contacts: {
      title: "Contacts",
      intro: "Manage common recipients like a chat app.",
      eyebrow: "Contacts",
      viewBlacklist: "Blacklist",
      backToContacts: "Back to contacts",
      addTitle: "Add contact",
      editTitle: "Edit contact",
      empty: "No contacts yet.",
      unnamed: "Unnamed contact",
      accountId: "Account",
      accountPlaceholder: "Enter accountId",
      alias: "Alias",
      aliasPlaceholder: "Example: Teammate A",
      publicKey: "Public key (optional)",
      publicKeyPlaceholder: "Leave blank to let the backend fetch it",
      saveCreate: "Save contact",
      saveEdit: "Save changes",
      searchUser: "Search online user",
      searchAndAdd: "Search and add",
      menuEdit: "Edit",
      menuDelete: "Delete",
      menuBlock: "Move to blacklist",
    },
    blacklist: {
      title: "Blacklist",
      eyebrow: "Blacklist",
      addTitle: "Block account",
      empty: "No blocked entries.",
      reason: "Reason (optional)",
      reasonPlaceholder: "Example: Ignore test sender",
      publicKeyPlaceholder: "Optional",
      saveCreate: "Add to blacklist",
      menuRemove: "Remove from blacklist",
      noReason: "No reason",
    },
    settings: {
      eyebrow: "Settings",
      title: "Settings",
      open: "Open settings",
      close: "Collapse settings",
      statusTitle: "Status check",
      refreshStatus: "Refresh status",
      localStatus: "Local service",
      serverStatus: "Server connection",
      keyStatus: "Key status",
      accountId: "Local account",
      accountIdHint: "Click to copy the full accountID",
      accountIdMissing: "Generate a key to show the accountID",
      languageTitle: "Language",
      keyTitle: "Key management",
      generateKey: "Regenerate key",
      deleteKey: "Delete key",
      importTextMode: "Paste key",
      importFileMode: "Key file",
      privateKeyText: "Private key text",
      privateKeyTextPlaceholder: "Paste PEM private key",
      privateKeyPath: "Private key file path",
      privateKeyPathPlaceholder: "Example: C:\\Users\\15328\\Desktop\\private_key.pem",
      pickKeyFile: "Choose key file",
      importTextAction: "Import private key",
      importFileAction: "Import key file",
    },
    status: {
      localReady: "Ready",
      localNeeds: "Needs attention",
      localPending: "Not synced",
      localWaiting: "Waiting for local service",
      localError: "Service unavailable",
      serverConnected: "Connected",
      serverDisconnected: "Disconnected",
      serverConnectedHint: "Ready to send to online devices",
      serverDisconnectedHint: "Manage contacts first, then connect before sending",
      keyReady: "Key ready",
      keyMissing: "Missing key",
      keyPending: "Not synced",
      keyPendingHint: "Checking key status",
      keyMissingHint: "Generate or import a private key first",
      keyReadyHint: "Secure transfer is ready",
    },
    tasks: {
      send: "Send",
      receive: "Receive",
      CREATED: "Created",
      PENDING: "Pending",
      WAITING_FOR_ACCEPT: "Waiting",
      TRANSFERRING: "Transferring",
      COMPLETED: "Completed",
      FAILED: "Failed",
      CANCELED: "Canceled",
      CANCELLED: "Canceled",
      REJECTED: "Rejected",
      unknown: "Unknown",
    },
    errors: {
      browserPath: "Browser mode cannot read a real local path. Type it manually or use the desktop app chooser.",
      recipientRequired: "Choose a contact or enter an accountId first.",
      fileRequired: "Choose a file or type a local path first.",
      transferIdRequired: "Enter a transfer ID first.",
      keyTextRequired: "Paste the private key text first.",
      keyPathRequired: "Enter the private key file path first.",
      contactAccountRequired: "AccountId is required.",
      blacklistAccountRequired: "Blocked accountId is required.",
    },
    info: {
      keyGenerated: "Key generated.",
      keyImported: "Private key imported.",
      keyFileImported: "Private key file imported.",
      keyDeleted: "Key deleted.",
      contactSaved: "Contact saved.",
      blacklistSaved: "Added to blacklist.",
    },
  },
};

function getMessage(language, path) {
  return path.split(".").reduce((value, key) => (value && typeof value === "object" ? value[key] : undefined), MESSAGES[language]);
}

export default {
  name: "App",

  components: {
    RecipientToggleIcon,
    SidebarOpenIcon,
    SettingsGearIcon,
  },

  data() {
    return {
      activePage: "send",
      sidebarOpen: false,
      contactsSubView: "list",
      showSettingsDrawer: false,
      showDebugPanel: false,
      showRetransmitPanel: false,
      showContactModal: false,
      contactModalMode: "create",
      contactModalError: "",
      activeMenuKey: "",
      apiBase: DEFAULT_API_BASE,
      refreshing: false,
      busyKeys: new Set(),
      language: "zh-CN",
      keyImportMode: "paste",
      systemStatus: null,
      keyStatus: null,
      connectionStatus: null,
      dashboardError: "",
      keyActionError: "",
      keyActionSuccess: "",
      transferError: "",
      incomingError: "",
      taskError: "",
      retransmitError: "",
      contactError: "",
      contactListError: "",
      blacklistError: "",
      blacklistListError: "",
      debugError: "",
      debugInfo: null,
      sendForm: {
        filePath: "",
        targetAccountId: "",
      },
      sendRecipientInput: "",
      sendContactDropdownOpen: false,
      receiveForm: {
        transferId: "",
      },
      receiveHistoryExpanded: false,
      keyForm: {
        privateKey: "",
        privateKeyPath: "",
      },
      dragActive: false,
      dragError: "",
      contactDraft: {
        contactIndex: null,
        accountId: "",
        alias: "",
        publicKey: "",
        reason: "",
      },
      tasks: [],
      incomingRequests: [],
      retransmitRequests: [],
      contacts: [],
      blacklist: [],
      searchResult: null,
      copyFeedback: "",
      copyFeedbackTimer: null,
      receivePollTimer: null,
      receivePollInFlight: false,
      retransmitPollTimer: null,
      retransmitPollInFlight: false,
    };
  },

  computed: {
    navigation() {
      return [
        { id: "send", label: this.t("nav.send"), icon: "send" },
        { id: "receive", label: this.t("nav.receive"), icon: "inbox" },
        { id: "contacts", label: this.t("nav.contacts"), icon: "users-round" },
      ];
    },

    pageTitle() {
      if (this.activePage === "receive") return this.t("receive.title");
      if (this.activePage === "contacts") {
        return this.contactsSubView === "blacklist" ? this.t("blacklist.title") : this.t("contacts.title");
      }
      return this.t("send.title");
    },

    pageIntro() {
      if (this.activePage === "receive") return this.t("receive.intro");
      if (this.activePage === "contacts") return this.t("contacts.intro");
      return this.t("send.intro");
    },

    hasKeyPair() {
      return hasLocalKeyPair(this.keyStatus);
    },

    isBackendUp() {
      return Boolean(this.systemStatus?.status);
    },

    isAuthenticated() {
      return String(this.connectionStatus?.status || "").toUpperCase() === "AUTHENTICATED";
    },

    localStatusText() {
      if (!this.systemStatus && !this.dashboardError) return this.t("status.localPending");
      return this.isBackendUp && this.hasKeyPair && !this.dashboardError ? this.t("status.localReady") : this.t("status.localNeeds");
    },

    localStatusHint() {
      if (this.dashboardError) return this.t("status.localError");
      if (!this.isBackendUp) return this.t("status.localWaiting");
      if (!this.hasKeyPair) return this.t("status.keyMissingHint");
      return this.t("status.keyReadyHint");
    },

    serverStatusText() {
      return this.isAuthenticated ? this.t("status.serverConnected") : this.t("status.serverDisconnected");
    },

    serverStatusHint() {
      return this.isAuthenticated ? this.t("status.serverConnectedHint") : this.t("status.serverDisconnectedHint");
    },

    keyReadyText() {
      if (!this.keyStatus) return this.t("status.keyPending");
      return this.hasKeyPair ? this.t("status.keyReady") : this.t("status.keyMissing");
    },

    keyReadyHint() {
      if (!this.keyStatus) return this.t("status.keyPendingHint");
      return this.hasKeyPair ? this.t("status.keyReadyHint") : this.t("status.keyMissingHint");
    },

    localAccountId() {
      return this.systemStatus?.accountId || this.keyStatus?.accountId || "";
    },

    friendlyDashboardError() {
      return this.toFriendlyError(this.dashboardError);
    },

    chosenFileName() {
      return extractFileNameFromPath(this.sendForm.filePath);
    },

    filteredSendContacts() {
      const keyword = this.sendRecipientInput.trim().toLowerCase();
      const source = this.contacts.slice(0, 30);
      if (!keyword) return source.slice(0, 8);
      return source
        .filter((contact) => {
          const alias = String(contact.alias || "").toLowerCase();
          const accountId = String(contact.accountId || "").toLowerCase();
          const token = `contact-${contact.contactIndex}`.toLowerCase();
          return alias.includes(keyword) || accountId.includes(keyword) || token.includes(keyword);
        })
        .slice(0, 8);
    },

    selectedSendContact() {
      const exactId = String(this.sendFormTargetAccountId()).toLowerCase();
      if (!exactId) return null;
      return this.contacts.find((contact) => String(contact.accountId || "").toLowerCase() === exactId) || null;
    },

    sendTasks() {
      return this.tasks.filter((task) => this.isSendTask(task));
    },

    receiveTasks() {
      return this.tasks.filter((task) => this.isReceiveTask(task));
    },

    activeReceiveTasks() {
      return this.receiveTasks.filter((task) => isActiveReceiveTask(task));
    },

    completedReceiveTasks() {
      return receiveHistoryTasks(this.tasks, { expanded: true });
    },

    visibleReceiveHistoryTasks() {
      return receiveHistoryTasks(this.tasks, { expanded: this.receiveHistoryExpanded });
    },

    currentSendTask() {
      return this.findPriorityTask(this.sendTasks);
    },

    currentReceiveTask() {
      return this.findPriorityTask(this.activeReceiveTasks);
    },

    recentSendTasks() {
      return this.sendTasks.filter((task) => task !== this.currentSendTask).slice(0, 3);
    },

    contactModalTitle() {
      if (this.contactModalMode === "edit") return this.t("contacts.editTitle");
      if (this.contactModalMode === "blacklist") return this.t("blacklist.addTitle");
      return this.t("contacts.addTitle");
    },

    contactModalSubmitText() {
      if (this.contactModalMode === "edit") return this.t("contacts.saveEdit");
      if (this.contactModalMode === "blacklist") return this.t("blacklist.saveCreate");
      return this.t("contacts.saveCreate");
    },

    searchResultNote() {
      if (!this.searchResult) return "";
      if (this.searchResult.found) return this.language === "en-US" ? "User found and ready to add." : "已找到该用户，可以直接添加。";
      return this.searchResult.message || "";
    },

    manualReceiveDisabled() {
      const id = this.receiveForm.transferId.trim();
      return !id || this.isBusy(id) || this.isBusy(`open-${id}`);
    },

    debugInfoItems() {
      if (!this.debugInfo) return [];
      return [
        { label: this.t("debug.version"), value: this.debugInfo.version },
        { label: this.t("debug.platform"), value: this.debugInfo.platform },
        { label: this.t("debug.mode"), value: this.debugInfo.isPackaged ? this.t("debug.packaged") : this.t("debug.development") },
        { label: this.t("debug.healthUrl"), value: this.debugInfo.healthUrl },
        { label: this.t("debug.logDir"), value: this.debugInfo.logDir },
        { label: this.t("debug.userDataDir"), value: this.debugInfo.userDataDir },
        { label: this.t("debug.downloadDir"), value: this.debugInfo.downloadDir },
        { label: this.t("debug.runtimeRoot"), value: this.debugInfo.runtimeRoot },
      ];
    },
  },

  mounted() {
    this.refreshActivePage();
    this.refreshIcons();
    this.syncReceivePolling();
    this.startRetransmitPolling();
    window.addEventListener("beforeunload", this.stopAllPolling);
  },

  beforeUnmount() {
    this.stopAllPolling();
    window.removeEventListener("beforeunload", this.stopAllPolling);
    if (this.copyFeedbackTimer) {
      clearTimeout(this.copyFeedbackTimer);
      this.copyFeedbackTimer = null;
    }
  },

  updated() {
    this.refreshIcons();
  },

  methods: {
    t(path) {
      return getMessage(this.language, path) || path;
    },

    setLanguage(language) {
      this.language = language;
    },

    initials(text) {
      const value = String(text || "").trim();
      if (!value) return "??";
      return value.slice(0, 2).toUpperCase();
    },

    toggleSettings() {
      this.showSettingsDrawer = !this.showSettingsDrawer;
      if (this.showSettingsDrawer) {
        this.loadDashboard();
      }
    },

    toggleSidebar() {
      this.sidebarOpen = !this.sidebarOpen;
    },

    closeSidebar() {
      this.sidebarOpen = false;
    },

    openSettings() {
      this.showSettingsDrawer = true;
      this.loadDashboard();
    },

    closeSettings() {
      this.showSettingsDrawer = false;
    },

    toggleRetransmitPanel() {
      this.showRetransmitPanel = !this.showRetransmitPanel;
    },

    toggleDebugPanel() {
      this.showDebugPanel = !this.showDebugPanel;
      this.debugError = "";
      if (this.showDebugPanel) {
        this.loadDebugInfo();
      }
    },

    closeDebugPanel() {
      this.showDebugPanel = false;
      this.debugError = "";
    },

    async loadDebugInfo() {
      this.debugError = "";
      const desktopApi = this.desktopApi();
      if (!hasDesktopDebugApi(desktopApi)) {
        this.debugError = this.t("debug.desktopOnly");
        this.debugInfo = null;
        return;
      }
      this.markBusy("debug-info", true);
      try {
        this.debugInfo = await desktopApi.getDebugInfo();
      } catch (error) {
        this.debugError = this.toFriendlyError(error.message);
      } finally {
        this.markBusy("debug-info", false);
      }
    },

    async openDevTools() {
      await this.performDebugAction("debug-devtools", async (desktopApi) => {
        await desktopApi.openDevTools();
      });
    },

    async openLogsFolder() {
      await this.performDebugAction("debug-logs", async (desktopApi) => {
        await desktopApi.openLogsFolder();
      });
    },

    async openSystemStatus() {
      await this.performDebugAction("debug-status", async (desktopApi) => {
        await desktopApi.openSystemStatus();
      });
    },

    async copyDebugInfo() {
      if (!this.debugInfo) {
        await this.loadDebugInfo();
      }
      if (!this.debugInfo) return;
      await this.copyValue(JSON.stringify(this.debugInfo, null, 2), this.t("debug.info"));
    },

    async performDebugAction(key, action) {
      this.debugError = "";
      const desktopApi = this.desktopApi();
      if (!hasDesktopDebugApi(desktopApi)) {
        this.debugError = this.t("debug.desktopOnly");
        return;
      }
      this.markBusy(key, true);
      try {
        await action(desktopApi);
        await this.loadDebugInfo();
      } catch (error) {
        this.debugError = this.toFriendlyError(error.message);
      } finally {
        this.markBusy(key, false);
      }
    },

    setPage(page) {
      this.activePage = page;
      this.closeSidebar();
      this.activeMenuKey = "";
      if (page !== "contacts") {
        this.contactsSubView = "list";
      }
      this.syncReceivePolling();
      this.refreshActivePage();
    },

    async switchContactsSubView(view) {
      this.contactsSubView = view;
      this.activeMenuKey = "";
      if (view === "blacklist") {
        await this.loadBlacklist();
      } else {
        await this.loadContacts();
      }
    },

    async refreshActivePage() {
      this.refreshing = true;
      try {
        await this.loadDashboard();
        if (this.activePage === "send") {
          await Promise.all([this.loadTasks(), this.loadContacts()]);
        } else if (this.activePage === "receive") {
          await this.refreshReceivePage();
        } else if (this.contactsSubView === "blacklist") {
          await this.loadBlacklist();
        } else {
          await this.loadContacts();
        }
      } finally {
        this.refreshing = false;
      }
    },

    async refreshReceivePage(options = {}) {
      await Promise.all([
        this.loadTasks(options),
        this.loadIncoming(options),
        this.loadRetransmitRequests(options),
      ]);
    },

    syncReceivePolling() {
      if (this.activePage === "receive") {
        this.startReceivePolling();
      } else {
        this.stopReceivePolling();
      }
    },

    startReceivePolling() {
      if (this.receivePollTimer) return;
      this.receivePollTimer = window.setInterval(() => {
        this.pollReceivePage();
      }, 1000);
    },

    stopReceivePolling() {
      if (!this.receivePollTimer) return;
      window.clearInterval(this.receivePollTimer);
      this.receivePollTimer = null;
      this.receivePollInFlight = false;
    },

    startRetransmitPolling() {
      if (this.retransmitPollTimer) return;
      this.loadRetransmitRequests({ silent: true });
      this.retransmitPollTimer = window.setInterval(() => {
        this.pollRetransmitRequests();
      }, 1000);
    },

    stopRetransmitPolling() {
      if (!this.retransmitPollTimer) return;
      window.clearInterval(this.retransmitPollTimer);
      this.retransmitPollTimer = null;
      this.retransmitPollInFlight = false;
    },

    stopAllPolling() {
      this.stopReceivePolling();
      this.stopRetransmitPolling();
    },

    async pollReceivePage() {
      if (this.activePage !== "receive" || this.receivePollInFlight) return;
      this.receivePollInFlight = true;
      try {
        await this.refreshReceivePage({ silent: true });
      } finally {
        this.receivePollInFlight = false;
      }
    },

    async pollRetransmitRequests() {
      if (this.retransmitPollInFlight) return;
      this.retransmitPollInFlight = true;
      try {
        await this.loadRetransmitRequests({ silent: true, autoOpen: true });
      } finally {
        this.retransmitPollInFlight = false;
      }
    },

    async loadDashboard() {
      this.dashboardError = "";
      this.markBusy("dashboard", true);
      const results = await Promise.allSettled([
        this.requestJson("/api/system/status"),
        this.requestJson("/api/system/key"),
        this.requestJson("/api/system/connection-status"),
      ]);

      if (results[0].status === "fulfilled") this.systemStatus = results[0].value;
      if (results[1].status === "fulfilled") this.keyStatus = results[1].value;
      if (results[2].status === "fulfilled") this.connectionStatus = results[2].value;

      const failed = results.find((item) => item.status === "rejected");
      if (failed) {
        this.dashboardError = this.toFriendlyError(failed.reason.message);
      }
      this.markBusy("dashboard", false);
    },

    async loadTasks(options = {}) {
      const silent = options.silent === true;
      if (!silent) {
        this.taskError = "";
        this.markBusy("tasks", true);
      }
      try {
        this.tasks = await this.requestJson("/api/send/tasks");
      } catch (error) {
        this.taskError = this.toFriendlyError(error.message);
      } finally {
        if (!silent) {
          this.markBusy("tasks", false);
        }
      }
    },

    async loadIncoming(options = {}) {
      const silent = options.silent === true;
      if (!silent) {
        this.incomingError = "";
        this.markBusy("incoming", true);
      }
      try {
        this.incomingRequests = await this.requestJson("/api/receive/incoming");
      } catch (error) {
        this.incomingError = this.toFriendlyError(error.message);
      } finally {
        if (!silent) {
          this.markBusy("incoming", false);
        }
      }
    },

    async loadRetransmitRequests(options = {}) {
      const silent = options.silent === true;
      if (!silent) {
        this.retransmitError = "";
        this.markBusy("retransmit", true);
      }
      try {
        const previousCount = this.retransmitRequests.length;
        this.retransmitRequests = normalizeRetransmitRequests(await this.requestJson("/api/receive/retransmit-requests"));
        if (this.retransmitRequests.length > 0 && (options.autoOpen === true || previousCount === 0)) {
          this.showRetransmitPanel = true;
        }
        if (this.retransmitRequests.length === 0) {
          this.showRetransmitPanel = false;
        }
      } catch (error) {
        this.retransmitError = this.toFriendlyError(error.message);
      } finally {
        if (!silent) {
          this.markBusy("retransmit", false);
        }
      }
    },

    async loadContacts() {
      this.contactListError = "";
      this.markBusy("contacts", true);
      try {
        this.contacts = await this.requestJson("/api/contacts");
      } catch (error) {
        this.contactListError = this.toFriendlyError(error.message);
      } finally {
        this.markBusy("contacts", false);
      }
    },

    async loadBlacklist() {
      this.blacklistListError = "";
      this.markBusy("blacklist", true);
      try {
        this.blacklist = await this.requestJson("/api/contacts/blacklist");
      } catch (error) {
        this.blacklistListError = this.toFriendlyError(error.message);
      } finally {
        this.markBusy("blacklist", false);
      }
    },

    handleSendRecipientInput() {
      this.sendForm.targetAccountId = "";
      this.sendContactDropdownOpen = true;
    },

    selectSendContact(contact) {
      this.sendForm.targetAccountId = contact.accountId;
      this.sendRecipientInput = contact.alias || contact.accountId;
      this.sendContactDropdownOpen = false;
    },

    sendFormTargetAccountId() {
      return this.sendForm.targetAccountId || this.sendRecipientInput.trim();
    },

    desktopApi() {
      return typeof window !== "undefined" ? window.desktopApi || null : null;
    },

    async pickSendFile() {
      this.dragError = "";
      const desktopApi = this.desktopApi();
      if (!desktopApi?.pickSendFile) {
        this.dragError = this.t("errors.browserPath");
        return;
      }
      this.markBusy("pick-send-file", true);
      try {
        const result = await desktopApi.pickSendFile();
        if (!result || result.canceled || !result.filePath) return;
        this.sendForm.filePath = result.filePath;
      } catch (error) {
        this.dragError = this.toFriendlyError(error.message);
      } finally {
        this.markBusy("pick-send-file", false);
      }
    },

    async pickPrivateKeyFile() {
      this.keyActionError = "";
      const desktopApi = this.desktopApi();
      if (!desktopApi?.pickPrivateKeyFile) {
        this.keyActionError = this.t("errors.browserPath");
        return;
      }
      this.markBusy("pick-key-file", true);
      try {
        const result = await desktopApi.pickPrivateKeyFile();
        if (!result || result.canceled || !result.filePath) return;
        this.keyForm.privateKeyPath = result.filePath;
      } catch (error) {
        this.keyActionError = this.toFriendlyError(error.message);
      } finally {
        this.markBusy("pick-key-file", false);
      }
    },

    handleDragEnter() {
      this.dragActive = true;
    },

    handleDragOver() {
      this.dragActive = true;
    },

    handleDragLeave(event) {
      const nextTarget = event.relatedTarget;
      if (nextTarget && event.currentTarget?.contains(nextTarget)) return;
      this.dragActive = false;
    },

    handleFileDrop(event) {
      this.dragActive = false;
      this.dragError = "";
      const file = event.dataTransfer?.files?.[0];
      const selection = extractLocalFileSelection(file);
      if (!selection) {
        this.dragError = this.t("errors.browserPath");
        return;
      }
      this.sendForm.filePath = selection.path;
    },

    handleFilePathInput() {
      this.dragError = "";
    },

    async sendFile() {
      this.transferError = "";
      const targetAccountId = this.sendFormTargetAccountId();
      if (!targetAccountId) {
        this.transferError = this.t("errors.recipientRequired");
        return;
      }
      if (!this.sendForm.filePath) {
        this.transferError = this.t("errors.fileRequired");
        return;
      }

      this.markBusy("send", true);
      try {
        await this.requestJson("/api/send", {
          method: "POST",
          body: JSON.stringify({
            filePath: this.sendForm.filePath,
            targetAccountId,
          }),
        });
        await this.loadTasks();
      } catch (error) {
        this.transferError = this.toFriendlyError(error.message);
      } finally {
        this.markBusy("send", false);
      }
    },

    async acceptIncoming(transferId) {
      await this.performTransferAction(transferId, async () => {
        await this.requestJson("/api/receive/accept", {
          method: "POST",
          body: JSON.stringify({ transferId }),
        });
        await Promise.all([this.loadIncoming(), this.loadTasks()]);
      }, "incomingError");
    },

    async rejectIncoming(transferId) {
      await this.performTransferAction(transferId, async () => {
        await this.requestJson("/api/receive/reject", {
          method: "POST",
          body: JSON.stringify({ transferId }),
        });
        await Promise.all([this.loadIncoming(), this.loadTasks()]);
      }, "incomingError");
    },

    async manualAcceptReceive() {
      const id = this.manualReceiveId();
      if (!id) return;
      await this.acceptIncoming(id);
    },

    async manualRejectReceive() {
      const id = this.manualReceiveId();
      if (!id) return;
      await this.rejectIncoming(id);
    },

    async manualRequestRetransmit() {
      const id = this.manualReceiveId();
      if (!id) return;
      await this.performTransferAction(id, async () => {
        await this.requestJson("/api/receive/retransmit", {
          method: "POST",
          body: JSON.stringify({ taskIdOrTransferId: id }),
        });
        await this.loadRetransmitRequests();
      }, "incomingError");
    },

    async manualOpenReceived() {
      const id = this.manualReceiveId();
      if (!id) return;
      await this.performTransferAction(`open-${id}`, async () => {
        const result = await this.requestJson("/api/receive/open-received", {
          method: "POST",
          body: JSON.stringify({ taskIdOrTransferId: id }),
        });
        if (result.success === false) {
          throw new Error(result.message || "Open folder failed");
        }
      }, "incomingError");
    },

    async cancelSendTask(task) {
      const id = this.taskIdentifier(task);
      await this.performTransferAction(id, async () => {
        await this.requestJson(`/api/send/tasks/${encodeURIComponent(id)}/cancel`, { method: "POST" });
        await this.loadTasks();
      }, "taskError");
    },

    async requestRetransmit(task) {
      const id = this.taskIdentifier(task);
      await this.performTransferAction(id, async () => {
        await this.requestJson("/api/receive/retransmit", {
          method: "POST",
          body: JSON.stringify({ taskIdOrTransferId: id }),
        });
        await this.loadRetransmitRequests();
      }, "taskError");
    },

    async rejectReceiveTask(task) {
      const id = this.taskIdentifier(task);
      if (!id) return;
      await this.performTransferAction(`reject-${id}`, async () => {
        await this.requestJson("/api/receive/reject", {
          method: "POST",
          body: JSON.stringify({ transferId: id }),
        });
        await this.refreshReceivePage();
      }, "taskError");
    },

    async openReceived(task) {
      const id = this.taskIdentifier(task);
      await this.performTransferAction(`open-${id}`, async () => {
        const result = await this.requestJson("/api/receive/open-received", {
          method: "POST",
          body: JSON.stringify({ taskIdOrTransferId: id }),
        });
        if (result.success === false) {
          throw new Error(result.message || "Open folder failed");
        }
      }, "taskError");
    },

    async acceptRetransmit(transferId) {
      await this.performTransferAction(`rt-${transferId}`, async () => {
        await this.requestJson("/api/receive/retransmit-accept", {
          method: "POST",
          body: JSON.stringify({ transferId }),
        });
        await Promise.all([this.loadRetransmitRequests(), this.loadTasks()]);
      }, "retransmitError");
    },

    async rejectRetransmit(transferId) {
      await this.performTransferAction(`rt-${transferId}`, async () => {
        await this.requestJson("/api/receive/retransmit-reject", {
          method: "POST",
          body: JSON.stringify({ transferId }),
        });
        await Promise.all([this.loadRetransmitRequests(), this.loadTasks()]);
      }, "retransmitError");
    },

    manualReceiveId() {
      const id = this.receiveForm.transferId.trim();
      if (!id) {
        this.incomingError = this.t("errors.transferIdRequired");
      }
      return id;
    },

    async generateKey() {
      this.clearKeyFeedback();
      this.markBusy("key-generate", true);
      try {
        await this.requestJson("/api/system/key/generate", {
          method: "POST",
          body: JSON.stringify({}),
        });
        this.keyActionSuccess = this.t("info.keyGenerated");
        this.resetKeyForm();
        await this.loadDashboard();
      } catch (error) {
        this.keyActionError = this.toFriendlyError(error.message);
      } finally {
        this.markBusy("key-generate", false);
      }
    },

    async deleteKey() {
      this.clearKeyFeedback();
      this.markBusy("key-delete", true);
      try {
        await this.requestJson("/api/system/key/delete", {
          method: "POST",
          body: JSON.stringify({}),
        });
        this.keyActionSuccess = this.t("info.keyDeleted");
        this.resetKeyForm();
        await this.loadDashboard();
      } catch (error) {
        this.keyActionError = this.toFriendlyError(error.message);
      } finally {
        this.markBusy("key-delete", false);
      }
    },

    async importPrivateKeyText() {
      this.clearKeyFeedback();
      if (!this.keyForm.privateKey) {
        this.keyActionError = this.t("errors.keyTextRequired");
        return;
      }
      this.markBusy("key-import", true);
      try {
        await this.requestJson("/api/system/key/import-private", {
          method: "POST",
          body: JSON.stringify({ privateKey: this.keyForm.privateKey }),
        });
        this.keyActionSuccess = this.t("info.keyImported");
        this.resetKeyForm();
        await this.loadDashboard();
      } catch (error) {
        this.keyActionError = this.toFriendlyError(error.message);
      } finally {
        this.markBusy("key-import", false);
      }
    },

    async importPrivateKeyFile() {
      this.clearKeyFeedback();
      if (!this.keyForm.privateKeyPath) {
        this.keyActionError = this.t("errors.keyPathRequired");
        return;
      }
      this.markBusy("key-import-file", true);
      try {
        await this.requestJson("/api/system/key/import-private-file", {
          method: "POST",
          body: JSON.stringify({ privateKeyPath: this.keyForm.privateKeyPath }),
        });
        this.keyActionSuccess = this.t("info.keyFileImported");
        this.resetKeyForm();
        await this.loadDashboard();
      } catch (error) {
        this.keyActionError = this.toFriendlyError(error.message);
      } finally {
        this.markBusy("key-import-file", false);
      }
    },

    clearKeyFeedback() {
      this.keyActionError = "";
      this.keyActionSuccess = "";
    },

    resetKeyForm() {
      this.keyForm = {
        privateKey: "",
        privateKeyPath: "",
      };
    },

    openContactCreateModal() {
      this.contactModalMode = "create";
      this.contactModalError = "";
      this.searchResult = null;
      this.contactDraft = {
        contactIndex: null,
        accountId: "",
        alias: "",
        publicKey: "",
        reason: "",
      };
      this.showContactModal = true;
      this.activeMenuKey = "";
    },

    openContactEditModal(contact) {
      this.contactModalMode = "edit";
      this.contactModalError = "";
      this.searchResult = null;
      this.contactDraft = {
        contactIndex: contact.contactIndex,
        accountId: contact.accountId,
        alias: contact.alias || "",
        publicKey: contact.publicKey || "",
        reason: "",
      };
      this.showContactModal = true;
      this.activeMenuKey = "";
    },

    openBlacklistCreateModal() {
      this.contactModalMode = "blacklist";
      this.contactModalError = "";
      this.searchResult = null;
      this.contactDraft = {
        contactIndex: null,
        accountId: "",
        alias: "",
        publicKey: "",
        reason: "",
      };
      this.showContactModal = true;
      this.activeMenuKey = "";
    },

    closeContactModal() {
      this.showContactModal = false;
      this.contactModalError = "";
      this.searchResult = null;
    },

    async submitContactModal() {
      if (this.contactModalMode === "edit") {
        await this.updateContact();
      } else if (this.contactModalMode === "blacklist") {
        await this.addBlacklist();
      } else {
        await this.addContact();
      }
    },

    async addContact() {
      this.contactModalError = "";
      if (!this.contactDraft.accountId) {
        this.contactModalError = this.t("errors.contactAccountRequired");
        return;
      }
      this.markBusy("save-contact", true);
      try {
        await this.requestJson("/api/contacts", {
          method: "POST",
          body: JSON.stringify(this.compactBody({
            accountId: this.contactDraft.accountId,
            alias: this.contactDraft.alias,
            publicKey: this.contactDraft.publicKey,
          })),
        });
        await this.loadContacts();
        this.closeContactModal();
      } catch (error) {
        this.contactModalError = this.toFriendlyError(error.message);
      } finally {
        this.markBusy("save-contact", false);
      }
    },

    async updateContact() {
      this.contactModalError = "";
      this.markBusy("save-contact", true);
      try {
        await this.requestJson(`/api/contacts/${this.contactDraft.contactIndex}`, {
          method: "PUT",
          body: JSON.stringify(this.compactBody({
            alias: this.contactDraft.alias,
            publicKey: this.contactDraft.publicKey,
          })),
        });
        await this.loadContacts();
        this.closeContactModal();
      } catch (error) {
        this.contactModalError = this.toFriendlyError(error.message);
      } finally {
        this.markBusy("save-contact", false);
      }
    },

    async addBlacklist() {
      this.contactModalError = "";
      if (!this.contactDraft.accountId) {
        this.contactModalError = this.t("errors.blacklistAccountRequired");
        return;
      }
      this.markBusy("save-contact", true);
      try {
        await this.requestJson("/api/contacts/blacklist", {
          method: "POST",
          body: JSON.stringify(this.compactBody({
            accountId: this.contactDraft.accountId,
            reason: this.contactDraft.reason,
            publicKey: this.contactDraft.publicKey,
          })),
        });
        await this.loadBlacklist();
        this.closeContactModal();
        this.contactsSubView = "blacklist";
      } catch (error) {
        this.contactModalError = this.toFriendlyError(error.message);
      } finally {
        this.markBusy("save-contact", false);
      }
    },

    async searchUserFromModal() {
      this.contactModalError = "";
      this.searchResult = null;
      if (!this.contactDraft.accountId) {
        this.contactModalError = this.t("errors.contactAccountRequired");
        return;
      }
      this.markBusy("search-user", true);
      try {
        this.searchResult = await this.requestJson(`/api/contacts/search-user/${encodeURIComponent(this.contactDraft.accountId)}`);
      } catch (error) {
        this.contactModalError = this.toFriendlyError(error.message);
      } finally {
        this.markBusy("search-user", false);
      }
    },

    async searchUserAndAddFromModal() {
      this.contactModalError = "";
      if (!this.contactDraft.accountId) {
        this.contactModalError = this.t("errors.contactAccountRequired");
        return;
      }
      this.markBusy("search-add", true);
      try {
        const result = await this.requestJson("/api/contacts/search-user-add", {
          method: "POST",
          body: JSON.stringify({
            accountId: this.contactDraft.accountId,
            alias: this.contactDraft.alias,
          }),
        });
        if (result.success === false) {
          throw new Error(result.message || "Search and add failed");
        }
        await this.loadContacts();
        this.closeContactModal();
      } catch (error) {
        this.contactModalError = this.toFriendlyError(error.message);
      } finally {
        this.markBusy("search-add", false);
      }
    },

    async removeContact(contactIndex) {
      this.activeMenuKey = "";
      this.markBusy(`contact-${contactIndex}`, true);
      try {
        await this.requestJson(`/api/contacts/${contactIndex}`, { method: "DELETE" });
        await this.loadContacts();
      } catch (error) {
        this.contactListError = this.toFriendlyError(error.message);
      } finally {
        this.markBusy(`contact-${contactIndex}`, false);
      }
    },

    async blacklistContact(contact) {
      this.activeMenuKey = "";
      const key = `black-contact-${contact.contactIndex}`;
      this.markBusy(key, true);
      try {
        await this.requestJson(`/api/contacts/blacklist/contact/${contact.contactIndex}`, {
          method: "POST",
          body: JSON.stringify({ reason: "Moved from contact list" }),
        });
        await Promise.all([this.loadContacts(), this.loadBlacklist()]);
      } catch (error) {
        this.contactListError = this.toFriendlyError(error.message);
      } finally {
        this.markBusy(key, false);
      }
    },

    async removeBlacklist(accountId) {
      this.activeMenuKey = "";
      this.markBusy(`black-${accountId}`, true);
      try {
        await this.requestJson(`/api/contacts/blacklist/${encodeURIComponent(accountId)}`, { method: "DELETE" });
        await this.loadBlacklist();
      } catch (error) {
        this.blacklistListError = this.toFriendlyError(error.message);
      } finally {
        this.markBusy(`black-${accountId}`, false);
      }
    },

    toggleMenu(kind, id) {
      const key = `${kind}:${id}`;
      this.activeMenuKey = this.activeMenuKey === key ? "" : key;
    },

    isMenuOpen(kind, id) {
      return this.activeMenuKey === `${kind}:${id}`;
    },

    findPriorityTask(tasks) {
      return tasks.find((task) => !this.isTerminalTask(task)) || tasks[0] || null;
    },

    requestJson(path, options = {}) {
      return fetch(`${this.apiBase}${path}`, {
        headers: options.body ? { "Content-Type": "application/json" } : undefined,
        ...options,
      }).then(async (response) => {
        const text = await response.text();
        let payload = {};
        if (text) {
          try {
            payload = JSON.parse(text);
          } catch {
            throw new Error(`后端返回非 JSON：${text.slice(0, 80)}`);
          }
        }
        if (!response.ok) {
          throw new Error(payload.message || payload.error || `HTTP ${response.status}`);
        }
        return payload;
      });
    },

    compactBody(source) {
      return Object.fromEntries(
        Object.entries(source).filter(([, value]) => value !== null && value !== undefined && String(value).trim() !== ""),
      );
    },

    async performTransferAction(key, action, errorKey = "taskError") {
      if (errorKey && Object.prototype.hasOwnProperty.call(this, errorKey)) {
        this[errorKey] = "";
      }
      this.markBusy(key, true);
      try {
        await action();
      } catch (error) {
        const message = this.toFriendlyError(error.message);
        if (errorKey && Object.prototype.hasOwnProperty.call(this, errorKey)) {
          this[errorKey] = message;
        } else {
          this.taskError = message;
        }
      } finally {
        this.markBusy(key, false);
      }
    },

    async copyValue(value, label = "") {
      if (!value) return;
      const text = String(value);
      try {
        if (navigator.clipboard?.writeText) {
          await navigator.clipboard.writeText(text);
        } else {
          this.copyValueFallback(text);
        }
        this.showCopyFeedback(label ? `${label} ${this.t("common.copied")}` : this.t("common.copied"));
      } catch {
        this.copyValueFallback(text);
        this.showCopyFeedback(label ? `${label} ${this.t("common.copied")}` : this.t("common.copied"));
      }
    },

    copyValueFallback(text) {
      const input = document.createElement("textarea");
      input.value = text;
      input.setAttribute("readonly", "");
      input.style.position = "fixed";
      input.style.left = "-9999px";
      document.body.appendChild(input);
      input.select();
      document.execCommand("copy");
      document.body.removeChild(input);
    },

    showCopyFeedback(message) {
      this.copyFeedback = message;
      if (this.copyFeedbackTimer) {
        clearTimeout(this.copyFeedbackTimer);
      }
      this.copyFeedbackTimer = window.setTimeout(() => {
        this.copyFeedback = "";
        this.copyFeedbackTimer = null;
      }, 1400);
    },

    markBusy(key, value) {
      const next = new Set(this.busyKeys);
      if (value) next.add(key);
      else next.delete(key);
      this.busyKeys = next;
    },

    isBusy(key) {
      return this.busyKeys.has(key);
    },

    taskIdentifier(task) {
      return getTaskIdentifier(task);
    },

    isSendTask(task) {
      return String(task.direction || "").toUpperCase() === "SEND";
    },

    isReceiveTask(task) {
      return String(task.direction || "").toUpperCase() === "RECEIVE";
    },

    isTerminalTask(task) {
      return isTerminalTaskStatus(task?.status);
    },

    taskProgress(task) {
      const progress = Number(task.progress || 0);
      if (progress > 0 && progress <= 1) return Math.round(progress * 100);
      return Math.min(100, Math.max(0, Math.round(progress)));
    },

    taskSpeedText(task) {
      return formatTaskSpeedText(task);
    },

    statusText(status) {
      const value = String(status || "").toUpperCase();
      return getMessage(this.language, `tasks.${value}`) || getMessage(this.language, "tasks.unknown");
    },

    receiveHistoryTime(task) {
      const value = task?.transferStartedAt || task?.createdAt;
      if (!value) return "--";
      const date = new Date(value);
      if (Number.isNaN(date.getTime())) return "--";
      return new Intl.DateTimeFormat(this.language, {
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
      }).format(date);
    },

    formatBytes(bytes) {
      const value = Number(bytes || 0);
      if (!value) return "0 B";
      const units = ["B", "KB", "MB", "GB", "TB"];
      const exponent = Math.min(Math.floor(Math.log(value) / Math.log(1024)), units.length - 1);
      const scaled = value / 1024 ** exponent;
      return `${scaled.toFixed(scaled >= 10 || exponent === 0 ? 0 : 1)} ${units[exponent]}`;
    },

    shortId(value, length = 12) {
      return formatShortId(value, length);
    },

    toFriendlyError(message) {
      const translated = toFriendlyErrorMessage(message);
      if (translated === "本机已有密钥，无需重复生成。如需更换身份，请先删除旧密钥。" && this.language === "en-US") {
        return "A key pair already exists. Delete the old key first if you need a new identity.";
      }
      if (translated === "请先选择私钥文件或粘贴私钥内容。" && this.language === "en-US") {
        return "Choose a key file or paste the private key text first.";
      }
      if (translated === "服务没有正常响应，请确认本机服务已启动。" && this.language === "en-US") {
        return "The local service did not respond. Make sure your local stack is running.";
      }
      if (translated === "服务暂时不可用，请稍后重试或检查本机服务。" && this.language === "en-US") {
        return "The service is temporarily unavailable. Try again later or check your local stack.";
      }
      return translated;
    },

    refreshIcons() {
      this.$nextTick(() => {
        createIcons();
      });
    },
  },
};
</script>
