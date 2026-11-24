import AppKit
import SwiftUI
import UniformTypeIdentifiers

struct RelayView: View {
    @ObservedObject var viewModel: AppViewModel
    let onSectionChange: () -> Void
    @State private var selectedSection: RelaySection = .overview

    init(viewModel: AppViewModel, onSectionChange: @escaping () -> Void = {}) {
        self.viewModel = viewModel
        self.onSectionChange = onSectionChange
    }

    var body: some View {
        let strings = viewModel.strings

        VStack(spacing: 18) {
            Picker("", selection: $selectedSection) {
                ForEach(RelaySection.allCases) { section in
                    Text(section.title(strings: strings)).tag(section)
                }
            }
            .pickerStyle(.segmented)
            .labelsHidden()

            switch selectedSection {
            case .overview:
                RelayOverviewView(viewModel: viewModel)
            case .send:
                RelaySendView(viewModel: viewModel)
            case .receive:
                RelayReceiveView(viewModel: viewModel)
            case .tasks:
                RelayTasksView(viewModel: viewModel)
            case .messages:
                RelayMessagesView(viewModel: viewModel)
            case .contacts:
                RelayContactsView(viewModel: viewModel, onSectionChange: onSectionChange)
            }
        }
        .padding(24)
        .onChange(of: selectedSection) {
            onSectionChange()
        }
    }
}

private struct RelayMessagesView: View {
    @ObservedObject var viewModel: AppViewModel
    @State private var selectedPeerAccountId = ""
    @State private var manualTargetAccountId = ""
    @State private var draft = ""

    var body: some View {
        let strings = viewModel.strings

        HStack(alignment: .top, spacing: 18) {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    Text(strings.text(.conversations))
                        .font(.headline)
                    Spacer()
                    Button {
                        Task { await viewModel.refreshMessageSummaries() }
                    } label: {
                        Image(systemName: "arrow.clockwise")
                    }
                    .help(strings.text(.refresh))
                    .disabled(viewModel.operationInProgress)
                }

                if viewModel.messageSummaries.isEmpty {
                    PlaceholderPanel(title: strings.text(.conversations), message: strings.text(.noConversations))
                } else {
                    ScrollView {
                        LazyVStack(spacing: 10) {
                            ForEach(viewModel.messageSummaries) { summary in
                                ConversationSummaryRow(
                                    summary: summary,
                                    strings: strings,
                                    isSelected: selectedPeerAccountId == summary.peerAccountId
                                ) {
                                    selectedPeerAccountId = summary.peerAccountId
                                    manualTargetAccountId = summary.peerAccountId
                                    Task { await viewModel.loadConversation(peerAccountId: summary.peerAccountId) }
                                }
                            }
                        }
                    }
                }
            }
            .frame(width: 300)
            .panelStyle()

            VStack(alignment: .leading, spacing: 14) {
                HStack {
                    Text(strings.text(.message))
                        .font(.headline)
                    Spacer()
                    TextField(strings.text(.accountId), text: $manualTargetAccountId)
                        .textFieldStyle(.roundedBorder)
                        .frame(width: 280)
                    Button {
                        selectedPeerAccountId = manualTargetAccountId
                        Task { await viewModel.loadConversation(peerAccountId: manualTargetAccountId) }
                    } label: {
                        Label(strings.text(.refresh), systemImage: "arrow.clockwise")
                    }
                }

                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 10) {
                        ForEach(viewModel.currentConversation) { message in
                            MessageBubble(message: message)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(2)
                }
                .frame(maxWidth: .infinity, minHeight: 280)
                .background(Color(nsColor: .textBackgroundColor), in: RoundedRectangle(cornerRadius: 8))
                .overlay {
                    RoundedRectangle(cornerRadius: 8)
                        .stroke(Color.secondary.opacity(0.16))
                }

                TextEditor(text: $draft)
                    .font(.body)
                    .frame(minHeight: 95)
                    .scrollContentBackground(.hidden)
                    .background(Color(nsColor: .textBackgroundColor), in: RoundedRectangle(cornerRadius: 8))
                    .overlay {
                        RoundedRectangle(cornerRadius: 8)
                            .stroke(Color.secondary.opacity(0.16))
                    }

                HStack {
                    Button {
                        Task {
                            await viewModel.sendRelayMessage(targetAccountId: resolvedTarget(), text: draft)
                            if viewModel.operationMessage == strings.text(.messageSent) {
                                draft = ""
                                selectedPeerAccountId = resolvedTarget()
                            }
                        }
                    } label: {
                        Label(strings.text(.sendMessage), systemImage: "paperplane")
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(viewModel.operationInProgress || viewModel.isPrivateKeyMissing)

                    Button {
                        draft = ""
                    } label: {
                        Label(strings.text(.clear), systemImage: "xmark")
                    }

                    Spacer()

                    if let message = viewModel.operationMessage {
                        Text(message)
                            .font(.callout)
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .panelStyle()
        }
        .task {
            await viewModel.refreshMessageSummaries()
        }
    }

    private func resolvedTarget() -> String {
        manualTargetAccountId.nilIfBlank ?? selectedPeerAccountId
    }
}

private struct ConversationSummaryRow: View {
    let summary: MessageConversationSummary
    let strings: AppStrings
    let isSelected: Bool
    let select: () -> Void

    var body: some View {
        Button(action: select) {
            VStack(alignment: .leading, spacing: 6) {
                HStack {
                    Text(summary.displayName)
                        .font(.headline)
                        .lineLimit(1)
                    Spacer()
                    if (summary.unreadCount ?? 0) > 0 {
                        Text("\(summary.unreadCount ?? 0)")
                            .font(.caption.weight(.bold))
                            .padding(.horizontal, 7)
                            .padding(.vertical, 3)
                            .background(Color.accentColor, in: Capsule())
                            .foregroundStyle(.white)
                            .help(strings.text(.unread))
                    }
                }
                Text(summary.peerAccountId)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
                HStack {
                    Text(summary.lastDirection ?? "-")
                    Text(summary.lastStatus ?? "-")
                    Spacer()
                    Text(summary.lastMessageTime ?? "-")
                        .lineLimit(1)
                }
                .font(.caption2)
                .foregroundStyle(.secondary)
            }
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(isSelected ? Color.accentColor.opacity(0.10) : Color(nsColor: .controlBackgroundColor), in: RoundedRectangle(cornerRadius: 8))
            .overlay {
                RoundedRectangle(cornerRadius: 8)
                    .stroke(isSelected ? Color.accentColor.opacity(0.45) : Color.secondary.opacity(0.16))
            }
        }
        .buttonStyle(.plain)
    }
}

private struct MessageBubble: View {
    let message: TextMessageItem

    var body: some View {
        HStack {
            if message.isOutgoing {
                Spacer(minLength: 60)
            }

            VStack(alignment: .leading, spacing: 5) {
                Text(message.body ?? "")
                    .textSelection(.enabled)
                HStack {
                    Text(message.status ?? "-")
                    Text(message.createdAt ?? message.receivedAt ?? "-")
                }
                .font(.caption2)
                .foregroundStyle(.secondary)
                if let error = message.errorMessage?.nilIfBlank {
                    Text(error)
                        .font(.caption)
                        .foregroundStyle(.red)
                }
            }
            .padding(11)
            .background(message.isOutgoing ? Color.accentColor.opacity(0.14) : Color(nsColor: .controlBackgroundColor), in: RoundedRectangle(cornerRadius: 8))
            .overlay {
                RoundedRectangle(cornerRadius: 8)
                    .stroke(message.isOutgoing ? Color.accentColor.opacity(0.25) : Color.secondary.opacity(0.14))
            }
            .frame(maxWidth: 520, alignment: message.isOutgoing ? .trailing : .leading)

            if !message.isOutgoing {
                Spacer(minLength: 60)
            }
        }
    }
}

private enum ContactsSubsection: String, CaseIterable, Identifiable {
    case contacts
    case blacklist

    var id: String { rawValue }

    func title(strings: AppStrings) -> String {
        switch self {
        case .contacts:
            strings.text(.contacts)
        case .blacklist:
            strings.text(.blacklist)
        }
    }
}

private struct RelayContactsView: View {
    @ObservedObject var viewModel: AppViewModel
    let onSectionChange: () -> Void
    @State private var selectedSubsection: ContactsSubsection = .contacts

    var body: some View {
        let strings = viewModel.strings

        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Picker("", selection: $selectedSubsection) {
                    ForEach(ContactsSubsection.allCases) { section in
                        Text(section.title(strings: strings)).tag(section)
                    }
                }
                .pickerStyle(.segmented)
                .labelsHidden()
                .frame(width: 280)

                Spacer()

                Button {
                    Task {
                        await viewModel.refreshContacts()
                        await viewModel.refreshBlacklist()
                    }
                } label: {
                    Label(strings.text(.refresh), systemImage: "arrow.clockwise")
                }
                .disabled(viewModel.operationInProgress)
            }

            switch selectedSubsection {
            case .contacts:
                ContactsManagementView(viewModel: viewModel)
            case .blacklist:
                BlacklistManagementView(viewModel: viewModel)
            }
        }
        .task {
            await viewModel.refreshContacts()
            await viewModel.refreshBlacklist()
        }
        .onChange(of: selectedSubsection) {
            onSectionChange()
        }
    }
}

private struct ContactsManagementView: View {
    @ObservedObject var viewModel: AppViewModel
    @State private var selectedContactIndex = 0
    @State private var accountId = ""
    @State private var alias = ""
    @State private var publicKey = ""
    @State private var publicKeyPath = ""

    private var selectedContact: ContactSummary? {
        viewModel.contacts.first { $0.contactIndex == selectedContactIndex }
    }

    var body: some View {
        let strings = viewModel.strings

        HStack(alignment: .top, spacing: 18) {
            VStack(alignment: .leading, spacing: 14) {
                Text(strings.text(.addContact))
                    .font(.headline)

                Grid(alignment: .leading, horizontalSpacing: 12, verticalSpacing: 10) {
                    GridRow {
                        Text(strings.text(.accountId)).foregroundStyle(.secondary)
                        TextField(strings.text(.accountId), text: $accountId)
                            .textFieldStyle(.roundedBorder)
                    }
                    GridRow {
                        Text(strings.text(.alias)).foregroundStyle(.secondary)
                        TextField(strings.text(.alias), text: $alias)
                            .textFieldStyle(.roundedBorder)
                    }
                    GridRow {
                        Text(strings.text(.publicKeyPath)).foregroundStyle(.secondary)
                        HStack {
                            TextField(strings.text(.publicKeyPath), text: $publicKeyPath)
                                .textFieldStyle(.roundedBorder)
                            Button {
                                choosePublicKeyFile()
                            } label: {
                                Image(systemName: "folder")
                            }
                            .help(strings.text(.chooseFile))
                        }
                    }
                }

                VStack(alignment: .leading, spacing: 8) {
                    Text(strings.text(.publicKey))
                        .foregroundStyle(.secondary)
                    TextEditor(text: $publicKey)
                        .font(.system(.body, design: .monospaced))
                        .frame(minHeight: 130)
                        .scrollContentBackground(.hidden)
                        .background(Color(nsColor: .textBackgroundColor), in: RoundedRectangle(cornerRadius: 8))
                        .overlay {
                            RoundedRectangle(cornerRadius: 8)
                                .stroke(Color.secondary.opacity(0.18))
                        }
                }

                HStack {
                    Button {
                        Task { await viewModel.addContact(accountId: accountId, alias: alias, publicKey: publicKey, publicKeyPath: publicKeyPath) }
                    } label: {
                        Label(strings.text(.addContact), systemImage: "person.badge.plus")
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(viewModel.operationInProgress)

                    Button {
                        Task { await viewModel.searchUser(accountId: accountId) }
                    } label: {
                        Label(strings.text(.searchUser), systemImage: "magnifyingglass")
                    }
                    .disabled(viewModel.operationInProgress)

                    Button {
                        Task { await viewModel.searchUserAndAddContact(accountId: accountId, alias: alias) }
                    } label: {
                        Label(strings.text(.searchAndAdd), systemImage: "person.crop.circle.badge.plus")
                    }
                    .disabled(viewModel.operationInProgress)

                    Button {
                        clearForm()
                    } label: {
                        Label(strings.text(.clear), systemImage: "xmark")
                    }
                }

                if let result = viewModel.searchUserResult {
                    SearchResultView(result: result, strings: strings)
                }

                if let message = viewModel.operationMessage {
                    Text(message)
                        .font(.callout)
                        .foregroundStyle(.secondary)
                }
            }
            .panelStyle()

            VStack(alignment: .leading, spacing: 12) {
                Text(strings.text(.contacts))
                    .font(.headline)

                if viewModel.contacts.isEmpty {
                    PlaceholderPanel(title: strings.text(.contacts), message: strings.text(.noContacts))
                } else {
                    ScrollView {
                        LazyVStack(spacing: 10) {
                            ForEach(viewModel.contacts) { contact in
                                ContactRow(
                                    contact: contact,
                                    strings: strings,
                                    isSelected: selectedContactIndex == contact.contactIndex,
                                    select: {
                                        select(contact)
                                    },
                                    update: {
                                        Task { await viewModel.updateContact(contact, alias: alias, publicKey: publicKey, publicKeyPath: publicKeyPath) }
                                    },
                                    delete: {
                                        Task { await viewModel.removeContact(contact) }
                                    },
                                    blacklist: {
                                        Task { await viewModel.addBlacklist(contact: contact, reason: "") }
                                    },
                                    isBusy: viewModel.operationInProgress
                                )
                            }
                        }
                    }
                }
            }
            .panelStyle()
        }
    }

    private func select(_ contact: ContactSummary) {
        selectedContactIndex = contact.contactIndex
        accountId = contact.accountId ?? ""
        alias = contact.alias ?? ""
        publicKey = contact.publicKey ?? ""
        publicKeyPath = ""
    }

    private func clearForm() {
        selectedContactIndex = 0
        accountId = ""
        alias = ""
        publicKey = ""
        publicKeyPath = ""
    }

    private func choosePublicKeyFile() {
        let panel = NSOpenPanel()
        panel.canChooseFiles = true
        panel.canChooseDirectories = false
        panel.allowsMultipleSelection = false
        if panel.runModal() == .OK, let url = panel.url {
            publicKeyPath = url.path
        }
    }
}

private struct ContactRow: View {
    let contact: ContactSummary
    let strings: AppStrings
    let isSelected: Bool
    let select: () -> Void
    let update: () -> Void
    let delete: () -> Void
    let blacklist: () -> Void
    let isBusy: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(contact.displayName)
                        .font(.headline)
                    Text(contact.accountId ?? "-")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .textSelection(.enabled)
                }

                Spacer()

                Text("contact-\(contact.contactIndex)")
                    .font(.caption.weight(.semibold))
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(Color.secondary.opacity(0.12), in: Capsule())
            }

            if let publicKey = contact.publicKey?.nilIfBlank {
                Text(publicKey)
                    .font(.caption.monospaced())
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
                    .textSelection(.enabled)
            }

            ViewThatFits(in: .horizontal) {
                HStack {
                    contactActionButtons(strings: strings)
                    Spacer()
                    deleteButton(strings: strings)
                }

                VStack(alignment: .leading, spacing: 8) {
                    contactActionButtons(strings: strings)
                    deleteButton(strings: strings)
                }
            }
        }
        .padding(14)
        .background(isSelected ? Color.accentColor.opacity(0.09) : Color(nsColor: .controlBackgroundColor), in: RoundedRectangle(cornerRadius: 8))
        .overlay {
            RoundedRectangle(cornerRadius: 8)
                .stroke(isSelected ? Color.accentColor.opacity(0.45) : Color.secondary.opacity(0.16))
        }
    }

    private func contactActionButtons(strings: AppStrings) -> some View {
        HStack(spacing: 8) {
            Button(action: select) {
                Label(strings.text(.selectContact), systemImage: isSelected ? "checkmark.circle.fill" : "circle")
                    .lineLimit(1)
                    .fixedSize(horizontal: true, vertical: false)
            }

            Button(action: update) {
                Label(strings.text(.updateContact), systemImage: "square.and.pencil")
                    .lineLimit(1)
                    .fixedSize(horizontal: true, vertical: false)
            }
            .disabled(!isSelected || isBusy)

            Button(action: blacklist) {
                Label(strings.text(.addToBlacklist), systemImage: "hand.raised")
                    .lineLimit(1)
                    .fixedSize(horizontal: true, vertical: false)
            }
            .disabled(isBusy)
        }
    }

    private func deleteButton(strings: AppStrings) -> some View {
        Button(role: .destructive, action: delete) {
            Label(strings.text(.deleteContact), systemImage: "trash")
                .lineLimit(1)
                .fixedSize(horizontal: true, vertical: false)
        }
        .disabled(isBusy)
    }
}

private struct SearchResultView: View {
    let result: SearchUserResult
    let strings: AppStrings

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Label(result.found == true ? strings.text(.connected) : strings.text(.unavailable), systemImage: result.found == true ? "checkmark.circle" : "xmark.circle")
                    .foregroundStyle(result.found == true ? .green : .secondary)
                Spacer()
                Text(result.accountId ?? "-")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .textSelection(.enabled)
            }
            if let message = result.message?.nilIfBlank {
                Text(message)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            if let publicKey = result.publicKey?.nilIfBlank {
                Text(publicKey)
                    .font(.caption.monospaced())
                    .foregroundStyle(.secondary)
                    .lineLimit(3)
                    .textSelection(.enabled)
            }
        }
        .padding(12)
        .background(Color(nsColor: .textBackgroundColor), in: RoundedRectangle(cornerRadius: 8))
    }
}

private struct BlacklistManagementView: View {
    @ObservedObject var viewModel: AppViewModel
    @State private var accountId = ""
    @State private var publicKey = ""
    @State private var reason = ""

    var body: some View {
        let strings = viewModel.strings

        HStack(alignment: .top, spacing: 18) {
            VStack(alignment: .leading, spacing: 14) {
                Text(strings.text(.blacklist))
                    .font(.headline)

                TextField(strings.text(.accountId), text: $accountId)
                    .textFieldStyle(.roundedBorder)
                TextField(strings.text(.reason), text: $reason)
                    .textFieldStyle(.roundedBorder)

                VStack(alignment: .leading, spacing: 8) {
                    Text(strings.text(.publicKey))
                        .foregroundStyle(.secondary)
                    TextEditor(text: $publicKey)
                        .font(.system(.body, design: .monospaced))
                        .frame(minHeight: 150)
                        .scrollContentBackground(.hidden)
                        .background(Color(nsColor: .textBackgroundColor), in: RoundedRectangle(cornerRadius: 8))
                        .overlay {
                            RoundedRectangle(cornerRadius: 8)
                                .stroke(Color.secondary.opacity(0.18))
                        }
                }

                HStack {
                    Button {
                        Task { await viewModel.addBlacklist(accountId: accountId, publicKey: publicKey, reason: reason) }
                    } label: {
                        Label(strings.text(.addToBlacklist), systemImage: "hand.raised")
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(viewModel.operationInProgress)

                    Button {
                        accountId = ""
                        publicKey = ""
                        reason = ""
                    } label: {
                        Label(strings.text(.clear), systemImage: "xmark")
                    }
                }

                if let message = viewModel.operationMessage {
                    Text(message)
                        .font(.callout)
                        .foregroundStyle(.secondary)
                }
            }
            .panelStyle()

            VStack(alignment: .leading, spacing: 12) {
                Text(strings.text(.blacklist))
                    .font(.headline)

                if viewModel.blacklistEntries.isEmpty {
                    PlaceholderPanel(title: strings.text(.blacklist), message: strings.text(.placeholder))
                } else {
                    ScrollView {
                        LazyVStack(spacing: 10) {
                            ForEach(viewModel.blacklistEntries) { entry in
                                BlacklistRow(entry: entry, strings: strings) {
                                    Task { await viewModel.removeBlacklist(entry) }
                                }
                            }
                        }
                    }
                }
            }
            .panelStyle()
        }
    }
}

private struct BlacklistRow: View {
    let entry: BlacklistEntry
    let strings: AppStrings
    let remove: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(entry.accountId)
                    .font(.headline)
                    .textSelection(.enabled)
                Spacer()
                Button(role: .destructive, action: remove) {
                    Label(strings.text(.removeFromBlacklist), systemImage: "trash")
                }
            }
            if let reason = entry.reason?.nilIfBlank {
                Text(reason)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            if let publicKey = entry.publicKey?.nilIfBlank {
                Text(publicKey)
                    .font(.caption.monospaced())
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
                    .textSelection(.enabled)
            }
            if let createdAt = entry.createdAt?.nilIfBlank {
                Text(createdAt)
                    .font(.caption2)
                    .foregroundStyle(.tertiary)
            }
        }
        .padding(14)
        .background(Color(nsColor: .controlBackgroundColor), in: RoundedRectangle(cornerRadius: 8))
        .overlay {
            RoundedRectangle(cornerRadius: 8)
                .stroke(Color.secondary.opacity(0.16))
        }
    }
}

private struct RelayReceiveView: View {
    @ObservedObject var viewModel: AppViewModel

    var body: some View {
        let strings = viewModel.strings

        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Text(strings.text(.incomingRequests))
                    .font(.headline)
                Spacer()
                Button {
                    Task { await viewModel.refreshIncomingTransfers() }
                } label: {
                    Label(strings.text(.refresh), systemImage: "arrow.clockwise")
                }
                .disabled(viewModel.operationInProgress)
            }

            if viewModel.incomingTransfers.isEmpty {
                PlaceholderPanel(title: strings.text(.relayReceive), message: strings.text(.noIncomingTransfers))
            } else {
                ScrollView {
                    LazyVStack(spacing: 10) {
                        ForEach(viewModel.incomingTransfers) { request in
                            IncomingTransferRow(
                                request: request,
                                strings: strings,
                                isBusy: viewModel.operationInProgress || viewModel.isPrivateKeyMissing,
                                accept: {
                                    Task { await viewModel.acceptIncomingTransfer(request) }
                                },
                                reject: {
                                    Task { await viewModel.rejectIncomingTransfer(request) }
                                }
                            )
                        }
                    }
                    .padding(2)
                }
            }

            if let message = viewModel.operationMessage {
                Text(message)
                    .font(.callout)
                    .foregroundStyle(.secondary)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .task {
            await viewModel.refreshIncomingTransfers()
        }
    }
}

private struct IncomingTransferRow: View {
    let request: IncomingTransferRequest
    let strings: AppStrings
    let isBusy: Bool
    let accept: () -> Void
    let reject: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 5) {
                    Text(request.fileName?.nilIfBlank ?? "-")
                        .font(.headline)
                    Text(request.senderDeviceId?.nilIfBlank ?? "-")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .textSelection(.enabled)
                }

                Spacer()

                Image(systemName: "tray.and.arrow.down")
                    .font(.title3)
                    .foregroundStyle(.blue)
                    .padding(8)
                    .background(Color.blue.opacity(0.12), in: Circle())
            }

            Grid(alignment: .leading, horizontalSpacing: 14, verticalSpacing: 6) {
                GridRow {
                    Text(strings.text(.transferId)).foregroundStyle(.secondary)
                    Text(request.transferId).textSelection(.enabled)
                }
                GridRow {
                    Text(strings.text(.fileSize)).foregroundStyle(.secondary)
                    Text(formattedBytes(request.fileSize))
                }
                GridRow {
                    Text(strings.text(.blocks)).foregroundStyle(.secondary)
                    Text(request.totalBlocks.map(String.init) ?? "-")
                }
                GridRow {
                    Text(strings.text(.receivedAt)).foregroundStyle(.secondary)
                    Text(request.receivedAt?.nilIfBlank ?? "-")
                }
            }
            .font(.caption)

            HStack {
                Spacer()
                Button(role: .destructive, action: reject) {
                    Label(strings.text(.reject), systemImage: "xmark.circle")
                }
                .disabled(isBusy)

                Button(action: accept) {
                    Label(strings.text(.accept), systemImage: "checkmark.circle")
                }
                .buttonStyle(.borderedProminent)
                .disabled(isBusy)
            }
        }
        .padding(16)
        .background(Color(nsColor: .controlBackgroundColor), in: RoundedRectangle(cornerRadius: 8))
        .overlay {
            RoundedRectangle(cornerRadius: 8)
                .stroke(Color.secondary.opacity(0.16))
        }
    }

    private func formattedBytes(_ value: Int64?) -> String {
        guard let value else {
            return "-"
        }
        return ByteCountFormatter.string(fromByteCount: value, countStyle: .file)
    }
}

private struct RelayTasksView: View {
    @ObservedObject var viewModel: AppViewModel

    var body: some View {
        let strings = viewModel.strings

        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Text(strings.text(.relayTasks))
                    .font(.headline)
                Spacer()
                Button {
                    Task { await viewModel.refreshTransferTasks() }
                } label: {
                    Label(strings.text(.refresh), systemImage: "arrow.clockwise")
                }
                .disabled(viewModel.operationInProgress)
            }

            if viewModel.transferTasks.isEmpty {
                PlaceholderPanel(title: strings.text(.relayTasks), message: strings.text(.noTasks))
            } else {
                HStack(alignment: .top, spacing: 14) {
                    ScrollView {
                        LazyVStack(spacing: 10) {
                            ForEach(viewModel.transferTasks) { task in
                                TaskRow(
                                    task: task,
                                    strings: strings,
                                    details: {
                                        Task { await viewModel.watchTransferTask(task) }
                                    },
                                    cancel: {
                                        Task { await viewModel.cancelTransfer(task) }
                                    }
                                )
                            }
                        }
                        .padding(2)
                    }

                    if let detail = viewModel.selectedTransferTaskDetail {
                        TaskDetailPanel(
                            detail: detail,
                            strings: strings,
                            isStreaming: viewModel.taskDetailStreamConnected,
                            close: viewModel.closeTransferTaskDetail
                        )
                        .frame(width: 320)
                    }
                }
            }

            if let message = viewModel.operationMessage {
                Text(message)
                    .font(.callout)
                    .foregroundStyle(.secondary)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .task {
            await viewModel.refreshTransferTasks()
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(3))
                await viewModel.refreshTransferTasks()
            }
        }
    }
}

private struct TaskRow: View {
    let task: TransferTaskSummary
    let strings: AppStrings
    let details: () -> Void
    let cancel: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 5) {
                    Text(task.fileName?.nilIfBlank ?? "-")
                        .font(.headline)
                    Text(task.message?.nilIfBlank ?? task.taskId?.nilIfBlank ?? "-")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .textSelection(.enabled)
                }

                Spacer()

                Text(task.status ?? "-")
                    .font(.caption.weight(.semibold))
                    .padding(.horizontal, 9)
                    .padding(.vertical, 5)
                    .background(statusColor.opacity(0.14), in: Capsule())
                    .foregroundStyle(statusColor)
            }

            ProgressView(value: normalizedProgress)

            Grid(alignment: .leading, horizontalSpacing: 14, verticalSpacing: 6) {
                GridRow {
                    Text(strings.text(.progress)).foregroundStyle(.secondary)
                    Text(progressText)
                }
                GridRow {
                    Text(strings.text(.direction)).foregroundStyle(.secondary)
                    Text(task.direction ?? "-")
                }
                GridRow {
                    Text(strings.text(.taskId)).foregroundStyle(.secondary)
                    Text(task.taskId ?? "-")
                        .textSelection(.enabled)
                }
            }
            .font(.caption)

            HStack {
                Button(action: details) {
                    Label("Details", systemImage: "sidebar.right")
                }

                Spacer()

                Button(role: .destructive, action: cancel) {
                    Label(strings.text(.cancel), systemImage: "xmark.circle")
                }
                .disabled(isTerminal)
            }
        }
        .padding(16)
        .background(Color(nsColor: .controlBackgroundColor), in: RoundedRectangle(cornerRadius: 8))
        .overlay {
            RoundedRectangle(cornerRadius: 8)
                .stroke(Color.secondary.opacity(0.16))
        }
    }

    private var normalizedProgress: Double {
        min(max((task.progress ?? 0) / 100, 0), 1)
    }

    private var progressText: String {
        String(format: "%.1f%%", task.progress ?? 0)
    }

    private var statusColor: Color {
        let value = task.status?.lowercased() ?? ""
        if value.contains("complete") || value.contains("success") {
            return .green
        }
        if value.contains("fail") || value.contains("cancel") || value.contains("reject") {
            return .red
        }
        return .blue
    }

    private var isTerminal: Bool {
        let value = task.status?.lowercased() ?? ""
        return value.contains("complete") || value.contains("fail") || value.contains("cancel") || value.contains("reject")
    }
}

private struct TaskDetailPanel: View {
    let detail: TransferTaskDetail
    let strings: AppStrings
    let isStreaming: Bool
    let close: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("Task Details")
                    .font(.headline)
                Spacer()
                Button(action: close) {
                    Image(systemName: "xmark")
                }
                .buttonStyle(.borderless)
            }

            Label(isStreaming ? "Live" : "Snapshot", systemImage: isStreaming ? "dot.radiowaves.left.and.right" : "pause.circle")
                .font(.caption.weight(.semibold))
                .foregroundStyle(isStreaming ? .green : .secondary)

            ProgressView(value: normalizedProgress)

            Grid(alignment: .leading, horizontalSpacing: 12, verticalSpacing: 8) {
                detailRow(strings.text(.status), detail.status)
                detailRow(strings.text(.progress), progressText)
                detailRow(strings.text(.taskId), detail.taskId)
                detailRow("Transfer ID", detail.transferId)
                detailRow(strings.text(.direction), detail.direction)
                detailRow("Peer", detail.peerDeviceId)
                detailRow("Bytes", bytesText)
                detailRow("Blocks", blocksText)
                detailRow("Speed", detail.speedText)
                detailRow("Local Path", detail.localPath)
                detailRow("Created", detail.createdAt)
                detailRow("Started", detail.transferStartedAt)
            }
            .font(.caption)

            if let message = detail.message?.nilIfBlank {
                Text(message)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .textSelection(.enabled)
            }

            if let localPath = detail.localPath?.nilIfBlank {
                ViewThatFits(in: .horizontal) {
                    HStack {
                        fileActionButtons(localPath: localPath)
                    }
                    VStack(alignment: .leading, spacing: 8) {
                        fileActionButtons(localPath: localPath)
                    }
                }
            }

            Spacer(minLength: 0)
        }
        .padding(14)
        .background(Color(nsColor: .controlBackgroundColor), in: RoundedRectangle(cornerRadius: 8))
        .overlay {
            RoundedRectangle(cornerRadius: 8)
                .stroke(Color.secondary.opacity(0.16))
        }
    }

    private func detailRow(_ label: String, _ value: String?) -> some View {
        GridRow {
            Text(label)
                .foregroundStyle(.secondary)
            Text(value?.nilIfBlank ?? "-")
                .lineLimit(3)
                .textSelection(.enabled)
        }
    }

    private func fileActionButtons(localPath: String) -> some View {
        Group {
            Button {
                NSPasteboard.general.clearContents()
                NSPasteboard.general.setString(localPath, forType: .string)
            } label: {
                Label(strings.text(.copyPath), systemImage: "doc.on.doc")
                    .lineLimit(1)
                    .fixedSize(horizontal: true, vertical: false)
            }

            Button {
                NSWorkspace.shared.activateFileViewerSelecting([URL(fileURLWithPath: localPath)])
            } label: {
                Label(strings.text(.revealInFinder), systemImage: "finder")
                    .lineLimit(1)
                    .fixedSize(horizontal: true, vertical: false)
            }
        }
    }

    private var normalizedProgress: Double {
        min(max((detail.progress ?? 0) / 100, 0), 1)
    }

    private var progressText: String {
        String(format: "%.1f%%", detail.progress ?? 0)
    }

    private var bytesText: String {
        guard let transferred = detail.transferredBytes, let total = detail.totalBytes else {
            return "-"
        }
        return "\(ByteCountFormatter.string(fromByteCount: transferred, countStyle: .file)) / \(ByteCountFormatter.string(fromByteCount: total, countStyle: .file))"
    }

    private var blocksText: String {
        guard let transferred = detail.transferredBlocks, let total = detail.totalBlocks else {
            return "-"
        }
        return "\(transferred) / \(total)"
    }
}

private enum SendTargetMode: String, CaseIterable, Identifiable {
    case manual
    case contact

    var id: String { rawValue }

    func title(strings: AppStrings) -> String {
        switch self {
        case .manual:
            strings.text(.manualAccount)
        case .contact:
            strings.text(.savedContact)
        }
    }
}

private struct RelaySendView: View {
    @ObservedObject var viewModel: AppViewModel
    @State private var filePath = ""
    @State private var isDropTargeted = false
    @State private var targetMode: SendTargetMode = .manual
    @State private var manualAccountId = ""
    @State private var selectedContactIndex = 0

    var body: some View {
        let strings = viewModel.strings

        VStack(alignment: .leading, spacing: 18) {
            HStack(alignment: .top, spacing: 18) {
                filePanel(strings: strings)
                targetPanel(strings: strings)
            }

            if let message = viewModel.operationMessage {
                Text(message)
                    .font(.callout)
                    .foregroundStyle(.secondary)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 10)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color(nsColor: .controlBackgroundColor), in: RoundedRectangle(cornerRadius: 8))
            }

            Spacer(minLength: 0)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .task {
            await viewModel.refreshContacts()
        }
    }

    private func filePanel(strings: AppStrings) -> some View {
        VStack(alignment: .leading, spacing: 16) {
            Text(strings.text(.selectedFile))
                .font(.headline)

            DropZone(
                strings: strings,
                filePath: $filePath,
                isDropTargeted: $isDropTargeted,
                chooseFile: chooseFile
            )

            if !filePath.isEmpty {
                Text(filePath)
                    .font(.callout)
                    .foregroundStyle(.secondary)
                    .textSelection(.enabled)
                    .lineLimit(3)
            }
        }
        .panelStyle()
    }

    private func targetPanel(strings: AppStrings) -> some View {
        VStack(alignment: .leading, spacing: 16) {
            Text(strings.text(.target))
                .font(.headline)

            Picker("", selection: $targetMode) {
                ForEach(SendTargetMode.allCases) { mode in
                    Text(mode.title(strings: strings)).tag(mode)
                }
            }
            .pickerStyle(.segmented)
            .labelsHidden()

            switch targetMode {
            case .manual:
                TextField(strings.text(.accountId), text: $manualAccountId)
                    .textFieldStyle(.roundedBorder)
            case .contact:
                if viewModel.contacts.isEmpty {
                    Text(strings.text(.noContacts))
                        .foregroundStyle(.secondary)
                } else {
                    Picker(strings.text(.savedContact), selection: $selectedContactIndex) {
                        Text(strings.text(.noContacts)).tag(0)
                        ForEach(viewModel.contacts) { contact in
                            Text(contactTitle(contact))
                                .tag(contact.contactIndex)
                        }
                    }
                    .labelsHidden()
                    .frame(minWidth: 260, maxWidth: .infinity, alignment: .leading)
                }
            }

            HStack {
                Button {
                    Task {
                        await viewModel.sendRelayFile(filePath: filePath, targetAccountId: resolvedTargetAccountId())
                    }
                } label: {
                    Label(strings.text(.sendFile), systemImage: "paperplane")
                }
                .buttonStyle(.borderedProminent)
                .disabled(viewModel.operationInProgress || viewModel.isPrivateKeyMissing)

                Button {
                    Task { await viewModel.refreshContacts() }
                } label: {
                    Label(strings.text(.refresh), systemImage: "arrow.clockwise")
                }
                .disabled(viewModel.operationInProgress)
            }
        }
        .panelStyle()
    }

    private func chooseFile() {
        let panel = NSOpenPanel()
        panel.canChooseFiles = true
        panel.canChooseDirectories = false
        panel.allowsMultipleSelection = false
        if panel.runModal() == .OK, let url = panel.url {
            filePath = url.path
        }
    }

    private func resolvedTargetAccountId() -> String {
        switch targetMode {
        case .manual:
            manualAccountId
        case .contact:
            viewModel.contacts.first(where: { $0.contactIndex == selectedContactIndex })?.accountId ?? ""
        }
    }

    private func contactTitle(_ contact: ContactSummary) -> String {
        let account = contact.accountId ?? "-"
        return "\(contact.displayName) · \(account)"
    }
}

private struct DropZone: View {
    let strings: AppStrings
    @Binding var filePath: String
    @Binding var isDropTargeted: Bool
    let chooseFile: () -> Void

    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: "doc.badge.plus")
                .font(.system(size: 34, weight: .regular))
                .foregroundStyle(isDropTargeted ? Color.accentColor : Color.secondary)
            Text(strings.text(.dropFile))
                .font(.headline)
            Button(action: chooseFile) {
                Label(strings.text(.chooseFile), systemImage: "folder")
            }
        }
        .frame(maxWidth: .infinity, minHeight: 190)
        .background(dropBackground, in: RoundedRectangle(cornerRadius: 8))
        .overlay {
            RoundedRectangle(cornerRadius: 8)
                .stroke(isDropTargeted ? Color.accentColor : Color.secondary.opacity(0.25), style: StrokeStyle(lineWidth: 1.2, dash: [7, 5]))
        }
        .onDrop(of: [UTType.fileURL.identifier], isTargeted: $isDropTargeted) { providers in
            loadFilePath(from: providers)
        }
    }

    private var dropBackground: Color {
        isDropTargeted ? Color.accentColor.opacity(0.08) : Color(nsColor: .textBackgroundColor)
    }

    private func loadFilePath(from providers: [NSItemProvider]) -> Bool {
        guard let provider = providers.first(where: { $0.hasItemConformingToTypeIdentifier(UTType.fileURL.identifier) }) else {
            return false
        }

        provider.loadItem(forTypeIdentifier: UTType.fileURL.identifier, options: nil) { item, _ in
            let url: URL?
            if let data = item as? Data {
                url = URL(dataRepresentation: data, relativeTo: nil)
            } else {
                url = item as? URL
            }

            if let url {
                DispatchQueue.main.async {
                    filePath = url.path
                }
            }
        }
        return true
    }
}

private enum RelaySection: String, CaseIterable, Identifiable {
    case overview
    case send
    case receive
    case tasks
    case messages
    case contacts

    var id: String { rawValue }

    func title(strings: AppStrings) -> String {
        switch self {
        case .overview:
            strings.text(.relayOverview)
        case .send:
            strings.text(.relaySend)
        case .receive:
            strings.text(.relayReceive)
        case .tasks:
            strings.text(.relayTasks)
        case .messages:
            strings.text(.relayMessages)
        case .contacts:
            strings.text(.relayContacts)
        }
    }
}

private struct RelayOverviewView: View {
    @ObservedObject var viewModel: AppViewModel

    var body: some View {
        let strings = viewModel.strings

        VStack(alignment: .leading, spacing: 18) {
            HStack(alignment: .top, spacing: 18) {
                StatusPanel(viewModel: viewModel)
                ConnectionPanel(viewModel: viewModel)
            }

            if let message = viewModel.operationMessage {
                Text(message)
                    .font(.callout)
                    .foregroundStyle(.secondary)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 10)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color(nsColor: .controlBackgroundColor), in: RoundedRectangle(cornerRadius: 8))
            }

            Spacer(minLength: 0)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .task {
            await viewModel.refreshConnectionStatus()
        }
        .accessibilityLabel(strings.text(.relayOverview))
    }
}

private struct StatusPanel: View {
    @ObservedObject var viewModel: AppViewModel

    var body: some View {
        let strings = viewModel.strings
        let status = viewModel.connectionStatus
        let isAuthenticated = status?.authenticated == true

        VStack(alignment: .leading, spacing: 16) {
            HStack {
                Text(strings.text(.localService))
                    .font(.headline)
                Spacer()
                Label(
                    isAuthenticated ? strings.text(.authenticated) : strings.text(.notAuthenticated),
                    systemImage: isAuthenticated ? "checkmark.seal.fill" : "xmark.seal"
                )
                .foregroundStyle(isAuthenticated ? .green : .secondary)
            }

            Grid(alignment: .leading, horizontalSpacing: 14, verticalSpacing: 10) {
                GridRow {
                    Text(strings.text(.deviceId)).foregroundStyle(.secondary)
                    Text(status?.deviceId?.nilIfBlank ?? viewModel.systemStatus?.deviceId?.nilIfBlank ?? "-")
                        .textSelection(.enabled)
                }
                GridRow {
                    Text(strings.text(.accountId)).foregroundStyle(.secondary)
                    Text(status?.accountId?.nilIfBlank ?? viewModel.systemStatus?.accountId?.nilIfBlank ?? "-")
                        .textSelection(.enabled)
                }
                GridRow {
                    Text(strings.text(.connected)).foregroundStyle(.secondary)
                    Text(status?.connected == true ? strings.text(.connected) : strings.text(.disconnected))
                }
                GridRow {
                    Text(strings.text(.server)).foregroundStyle(.secondary)
                    Text(connectionEndpoint(status: status))
                        .textSelection(.enabled)
                }
            }
            .font(.callout)
        }
        .panelStyle()
    }

    private func connectionEndpoint(status: ConnectionStatus?) -> String {
        guard let host = status?.connectedHost?.nilIfBlank, let port = status?.connectedPort, port > 0 else {
            return "-"
        }
        return "\(host):\(port)"
    }
}

private struct ConnectionPanel: View {
    @ObservedObject var viewModel: AppViewModel

    var body: some View {
        let strings = viewModel.strings

        VStack(alignment: .leading, spacing: 16) {
            Text(strings.text(.temporarySettings))
                .font(.headline)

            Grid(alignment: .leading, horizontalSpacing: 12, verticalSpacing: 12) {
                GridRow {
                    Text(strings.text(.host))
                        .foregroundStyle(.secondary)
                    TextField(strings.text(.host), text: $viewModel.relayHost)
                        .textFieldStyle(.roundedBorder)
                        .frame(minWidth: 260)
                }
                GridRow {
                    Text(strings.text(.port))
                        .foregroundStyle(.secondary)
                    TextField(strings.text(.port), text: $viewModel.relayPort)
                        .textFieldStyle(.roundedBorder)
                        .frame(width: 120)
                }
            }

            HStack {
                Button {
                    Task { await viewModel.connectRelay() }
                } label: {
                    Label(strings.text(.connect), systemImage: "bolt.horizontal.circle")
                }
                .buttonStyle(.borderedProminent)
                .disabled(viewModel.operationInProgress)

                Button {
                    Task { await viewModel.disconnectRelay() }
                } label: {
                    Label(strings.text(.disconnect), systemImage: "xmark.circle")
                }
                .disabled(viewModel.operationInProgress)

                Spacer()

                Button {
                    Task { await viewModel.refreshConnectionStatus() }
                } label: {
                    Label(strings.text(.refresh), systemImage: "arrow.clockwise")
                }
                .disabled(viewModel.operationInProgress)
            }
        }
        .panelStyle()
    }
}

extension View {
    func panelStyle() -> some View {
        self
            .frame(maxWidth: .infinity, alignment: .topLeading)
            .padding(20)
            .background(Color(nsColor: .controlBackgroundColor), in: RoundedRectangle(cornerRadius: 8))
            .overlay {
                RoundedRectangle(cornerRadius: 8)
                    .stroke(Color.secondary.opacity(0.18))
            }
    }
}

extension String {
    var nilIfBlank: String? {
        let trimmed = trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }
}
