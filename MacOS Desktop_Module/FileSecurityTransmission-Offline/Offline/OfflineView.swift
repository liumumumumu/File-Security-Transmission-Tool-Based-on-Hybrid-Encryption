import AppKit
import SwiftUI
import UniformTypeIdentifiers

struct OfflineView: View {
    @ObservedObject var viewModel: AppViewModel
    let onSectionChange: () -> Void
    @State private var selectedSection: OfflineSection = .fileEncrypt

    init(viewModel: AppViewModel, onSectionChange: @escaping () -> Void = {}) {
        self.viewModel = viewModel
        self.onSectionChange = onSectionChange
    }

    var body: some View {
        let strings = viewModel.strings

        VStack(spacing: 18) {
            Picker("", selection: $selectedSection) {
                ForEach(OfflineSection.allCases) { section in
                    Text(section.title(strings: strings)).tag(section)
                }
            }
            .pickerStyle(.segmented)
            .labelsHidden()

            switch selectedSection {
            case .fileEncrypt:
                OfflineFileEncryptView(viewModel: viewModel)
            case .fileDecrypt:
                OfflineFileDecryptView(viewModel: viewModel)
            case .textEncrypt:
                OfflineTextEncryptView(viewModel: viewModel)
            case .textDecrypt:
                OfflineTextDecryptView(viewModel: viewModel)
            }
        }
        .padding(24)
        .onChange(of: selectedSection) {
            onSectionChange()
        }
    }
}

private struct OfflineFileEncryptView: View {
    @ObservedObject var viewModel: AppViewModel
    @State private var filePath = ""
    @State private var outputDir = ""
    @State private var receiverMode: ReceiverSourceMode = .contact
    @State private var selectedContactIndex = 0
    @State private var receiverPublicKey = ""
    @State private var receiverPublicKeyPath = ""

    var body: some View {
        let strings = viewModel.strings

        HStack(alignment: .top, spacing: 18) {
            VStack(alignment: .leading, spacing: 16) {
                PathInputPanel(title: strings.text(.inputFile), path: $filePath, chooseTitle: strings.text(.chooseFile), allowDirectories: false)
                OutputDirectoryPanel(outputDir: $outputDir, strings: strings)
                OfflineResultPanel(result: viewModel.offlineFileResult, strings: strings)
            }
            .frame(maxWidth: .infinity)

            VStack(alignment: .leading, spacing: 16) {
                ReceiverSourcePanel(
                    viewModel: viewModel,
                    mode: $receiverMode,
                    selectedContactIndex: $selectedContactIndex,
                    publicKey: $receiverPublicKey,
                    publicKeyPath: $receiverPublicKeyPath
                )

                Button {
                    Task {
                        await viewModel.encryptOfflineFile(
                            filePath: filePath,
                            outputDir: outputDir,
                            receiver: receiverInput()
                        )
                    }
                } label: {
                    Label(strings.text(.encrypt), systemImage: "lock")
                }
                .buttonStyle(.borderedProminent)
                .disabled(viewModel.operationInProgress || viewModel.isPrivateKeyMissing)

                OperationMessage(viewModel: viewModel)
            }
            .frame(width: 360)
        }
        .task { await viewModel.refreshContacts() }
    }

    private func receiverInput() -> OfflineReceiverInput? {
        switch receiverMode {
        case .contact:
            guard selectedContactIndex > 0 else { return nil }
            return OfflineReceiverInput(contactIndex: selectedContactIndex, receiverPublicKey: nil, receiverPublicKeyPath: nil)
        case .publicKeyText:
            let value = receiverPublicKey.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !value.isEmpty else { return nil }
            return OfflineReceiverInput(contactIndex: nil, receiverPublicKey: value, receiverPublicKeyPath: nil)
        case .publicKeyFile:
            let value = receiverPublicKeyPath.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !value.isEmpty else { return nil }
            return OfflineReceiverInput(contactIndex: nil, receiverPublicKey: nil, receiverPublicKeyPath: value)
        }
    }
}

private struct OfflineFileDecryptView: View {
    @ObservedObject var viewModel: AppViewModel
    @State private var filePath = ""
    @State private var outputDir = ""

    var body: some View {
        let strings = viewModel.strings

        HStack(alignment: .top, spacing: 18) {
            VStack(alignment: .leading, spacing: 16) {
                PathInputPanel(title: strings.text(.inputFile), path: $filePath, chooseTitle: strings.text(.chooseFile), allowDirectories: false)
                OutputDirectoryPanel(outputDir: $outputDir, strings: strings)
                OfflineResultPanel(result: viewModel.offlineFileResult, strings: strings)
            }
            .frame(maxWidth: .infinity)

            VStack(alignment: .leading, spacing: 16) {
                Button {
                    Task { await viewModel.decryptOfflineFile(filePath: filePath, outputDir: outputDir) }
                } label: {
                    Label(strings.text(.decrypt), systemImage: "lock.open")
                }
                .buttonStyle(.borderedProminent)
                .disabled(viewModel.operationInProgress || viewModel.isPrivateKeyMissing)

                OperationMessage(viewModel: viewModel)
            }
            .frame(width: 300, alignment: .topLeading)
        }
    }
}

private struct OfflineTextEncryptView: View {
    @ObservedObject var viewModel: AppViewModel
    @State private var input = ""
    @State private var receiverMode: ReceiverSourceMode = .contact
    @State private var selectedContactIndex = 0
    @State private var receiverPublicKey = ""
    @State private var receiverPublicKeyPath = ""

    var body: some View {
        let strings = viewModel.strings

        HStack(alignment: .top, spacing: 18) {
            TextWorkspace(
                inputTitle: strings.text(.inputText),
                outputTitle: strings.text(.output),
                input: $input,
                output: viewModel.offlineTextOutput,
                strings: strings,
                clearOutput: viewModel.clearOfflineTextOutput
            )

            VStack(alignment: .leading, spacing: 16) {
                ReceiverSourcePanel(
                    viewModel: viewModel,
                    mode: $receiverMode,
                    selectedContactIndex: $selectedContactIndex,
                    publicKey: $receiverPublicKey,
                    publicKeyPath: $receiverPublicKeyPath
                )

                Button {
                    Task { await viewModel.encryptOfflineText(text: input, receiver: receiverInput()) }
                } label: {
                    Label(strings.text(.encrypt), systemImage: "lock")
                }
                .buttonStyle(.borderedProminent)
                .disabled(viewModel.operationInProgress || viewModel.isPrivateKeyMissing)

                OperationMessage(viewModel: viewModel)
            }
            .frame(width: 360)
        }
        .task { await viewModel.refreshContacts() }
    }

    private func receiverInput() -> OfflineReceiverInput? {
        switch receiverMode {
        case .contact:
            guard selectedContactIndex > 0 else { return nil }
            return OfflineReceiverInput(contactIndex: selectedContactIndex, receiverPublicKey: nil, receiverPublicKeyPath: nil)
        case .publicKeyText:
            let value = receiverPublicKey.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !value.isEmpty else { return nil }
            return OfflineReceiverInput(contactIndex: nil, receiverPublicKey: value, receiverPublicKeyPath: nil)
        case .publicKeyFile:
            let value = receiverPublicKeyPath.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !value.isEmpty else { return nil }
            return OfflineReceiverInput(contactIndex: nil, receiverPublicKey: nil, receiverPublicKeyPath: value)
        }
    }
}

private struct OfflineTextDecryptView: View {
    @ObservedObject var viewModel: AppViewModel
    @State private var input = ""

    var body: some View {
        let strings = viewModel.strings

        HStack(alignment: .top, spacing: 18) {
            TextWorkspace(
                inputTitle: strings.text(.inputText),
                outputTitle: strings.text(.output),
                input: $input,
                output: viewModel.offlineTextOutput,
                strings: strings,
                clearOutput: viewModel.clearOfflineTextOutput
            )

            VStack(alignment: .leading, spacing: 16) {
                Button {
                    Task { await viewModel.decryptOfflineText(payload: input) }
                } label: {
                    Label(strings.text(.decrypt), systemImage: "lock.open")
                }
                .buttonStyle(.borderedProminent)
                .disabled(viewModel.operationInProgress || viewModel.isPrivateKeyMissing)

                OperationMessage(viewModel: viewModel)
            }
            .frame(width: 300, alignment: .topLeading)
        }
    }
}

private struct ReceiverSourcePanel: View {
    @ObservedObject var viewModel: AppViewModel
    @Binding var mode: ReceiverSourceMode
    @Binding var selectedContactIndex: Int
    @Binding var publicKey: String
    @Binding var publicKeyPath: String

    var body: some View {
        let strings = viewModel.strings

        VStack(alignment: .leading, spacing: 14) {
            Text(strings.text(.receiverSource))
                .font(.headline)

            Picker("", selection: $mode) {
                ForEach(ReceiverSourceMode.allCases) { source in
                    Text(source.title(strings: strings)).tag(source)
                }
            }
            .pickerStyle(.segmented)
            .labelsHidden()

            switch mode {
            case .contact:
                if viewModel.contacts.isEmpty {
                    Text(strings.text(.noContacts))
                        .foregroundStyle(.secondary)
                } else {
                    Picker(strings.text(.savedContact), selection: $selectedContactIndex) {
                        Text(strings.text(.noContacts)).tag(0)
                        ForEach(viewModel.contacts) { contact in
                            Text("\(contact.displayName) · \(contact.accountId ?? "-")")
                                .tag(contact.contactIndex)
                        }
                    }
                    .labelsHidden()
                    .frame(minWidth: 260, maxWidth: .infinity, alignment: .leading)
                }
            case .publicKeyText:
                TextEditor(text: $publicKey)
                    .font(.system(.body, design: .monospaced))
                    .frame(minHeight: 160)
                    .scrollContentBackground(.hidden)
                    .background(Color(nsColor: .textBackgroundColor), in: RoundedRectangle(cornerRadius: 8))
                    .overlay {
                        RoundedRectangle(cornerRadius: 8)
                            .stroke(Color.secondary.opacity(0.18))
                    }
            case .publicKeyFile:
                PathInputPanel(title: strings.text(.publicKeyFile), path: $publicKeyPath, chooseTitle: strings.text(.chooseFile), allowDirectories: false)
            }
        }
        .panelStyle()
    }
}

private struct PathInputPanel: View {
    let title: String
    @Binding var path: String
    let chooseTitle: String
    let allowDirectories: Bool
    @State private var isDropTargeted = false

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(title)
                .font(.headline)

            VStack(spacing: 10) {
                Image(systemName: allowDirectories ? "folder.badge.plus" : "doc.badge.plus")
                    .font(.system(size: 30, weight: .regular))
                    .foregroundStyle(isDropTargeted ? Color.accentColor : Color.secondary)
                Text(path.isEmpty ? "Drop here" : path)
                    .font(path.isEmpty ? .headline : .callout)
                    .foregroundStyle(path.isEmpty ? .primary : .secondary)
                    .lineLimit(3)
                    .textSelection(.enabled)
                    .frame(maxWidth: .infinity)
                Button {
                    choosePath()
                } label: {
                    Label(chooseTitle, systemImage: allowDirectories ? "folder" : "doc")
                }
            }
            .frame(maxWidth: .infinity, minHeight: 150)
            .background(isDropTargeted ? Color.accentColor.opacity(0.08) : Color(nsColor: .textBackgroundColor), in: RoundedRectangle(cornerRadius: 8))
            .overlay {
                RoundedRectangle(cornerRadius: 8)
                    .stroke(isDropTargeted ? Color.accentColor : Color.secondary.opacity(0.25), style: StrokeStyle(lineWidth: 1.2, dash: [7, 5]))
            }
            .onDrop(of: [UTType.fileURL.identifier], isTargeted: $isDropTargeted) { providers in
                loadPath(from: providers)
            }
        }
        .panelStyle()
    }

    private func choosePath() {
        let panel = NSOpenPanel()
        panel.canChooseFiles = !allowDirectories
        panel.canChooseDirectories = allowDirectories
        panel.allowsMultipleSelection = false
        if panel.runModal() == .OK, let url = panel.url {
            path = url.path
        }
    }

    private func loadPath(from providers: [NSItemProvider]) -> Bool {
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
                    path = url.path
                }
            }
        }
        return true
    }
}

private struct OutputDirectoryPanel: View {
    @Binding var outputDir: String
    let strings: AppStrings

    var body: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                Text(strings.text(.outputDirectory))
                    .font(.headline)
                Text(outputDir.isEmpty ? "-" : outputDir)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
                    .textSelection(.enabled)
            }
            Spacer()
            Button {
                chooseFolder()
            } label: {
                Label(strings.text(.chooseFolder), systemImage: "folder")
            }
            Button {
                outputDir = ""
            } label: {
                Label(strings.text(.clear), systemImage: "xmark")
            }
        }
        .panelStyle()
    }

    private func chooseFolder() {
        let panel = NSOpenPanel()
        panel.canChooseFiles = false
        panel.canChooseDirectories = true
        panel.allowsMultipleSelection = false
        if panel.runModal() == .OK, let url = panel.url {
            outputDir = url.path
        }
    }
}

private struct OfflineResultPanel: View {
    let result: OfflineFileResult?
    let strings: AppStrings

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(strings.text(.result))
                .font(.headline)

            if let result {
                Grid(alignment: .leading, horizontalSpacing: 14, verticalSpacing: 6) {
                    GridRow {
                        Text(strings.text(.fileName)).foregroundStyle(.secondary)
                        Text(result.fileName ?? "-")
                    }
                    GridRow {
                        Text(strings.text(.fileSize)).foregroundStyle(.secondary)
                        Text(ByteCountFormatter.string(fromByteCount: result.fileSize ?? 0, countStyle: .file))
                    }
                    GridRow {
                        Text(strings.text(.blocks)).foregroundStyle(.secondary)
                        Text(result.totalBlocks.map(String.init) ?? "-")
                    }
                    GridRow {
                        Text(strings.text(.output)).foregroundStyle(.secondary)
                        Text(result.outputPath ?? "-")
                            .textSelection(.enabled)
                    }
                }
                .font(.caption)

                HStack {
                    Button {
                        copy(result.outputPath)
                    } label: {
                        Label(strings.text(.copyPath), systemImage: "doc.on.doc")
                    }
                    .disabled(result.outputPath?.isEmpty != false)

                    Button {
                        reveal(result.outputPath)
                    } label: {
                        Label(strings.text(.revealInFinder), systemImage: "finder")
                    }
                    .disabled(result.outputPath?.isEmpty != false)
                }
            } else {
                Text("-")
                    .foregroundStyle(.secondary)
            }
        }
        .panelStyle()
    }

    private func copy(_ value: String?) {
        guard let value, !value.isEmpty else { return }
        NSPasteboard.general.clearContents()
        NSPasteboard.general.setString(value, forType: .string)
    }

    private func reveal(_ value: String?) {
        guard let value, !value.isEmpty else { return }
        NSWorkspace.shared.activateFileViewerSelecting([URL(fileURLWithPath: value)])
    }
}

private struct TextWorkspace: View {
    let inputTitle: String
    let outputTitle: String
    @Binding var input: String
    let output: String
    let strings: AppStrings
    let clearOutput: () -> Void

    var body: some View {
        HStack(spacing: 18) {
            editorPanel(title: inputTitle, text: $input, readOnlyText: nil, clear: { input = "" })
            editorPanel(title: outputTitle, text: .constant(output), readOnlyText: output, clear: clearOutput)
        }
        .frame(maxWidth: .infinity)
    }

    private func editorPanel(title: String, text: Binding<String>, readOnlyText: String?, clear: @escaping () -> Void) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text(title)
                    .font(.headline)
                Spacer()
                Button {
                    copy(readOnlyText ?? text.wrappedValue)
                } label: {
                    Label(strings.text(.copy), systemImage: "doc.on.doc")
                }
                Button {
                    clear()
                } label: {
                    Label(strings.text(.clear), systemImage: "xmark")
                }
            }

            TextEditor(text: text)
                .font(.system(.body, design: .monospaced))
                .scrollContentBackground(.hidden)
                .background(Color(nsColor: .textBackgroundColor), in: RoundedRectangle(cornerRadius: 8))
                .overlay {
                    RoundedRectangle(cornerRadius: 8)
                        .stroke(Color.secondary.opacity(0.18))
                }
        }
        .panelStyle()
    }

    private func copy(_ value: String) {
        NSPasteboard.general.clearContents()
        NSPasteboard.general.setString(value, forType: .string)
    }
}

private struct OperationMessage: View {
    @ObservedObject var viewModel: AppViewModel

    var body: some View {
        if let message = viewModel.operationMessage {
            Text(message)
                .font(.callout)
                .foregroundStyle(.secondary)
        }
    }
}

private enum ReceiverSourceMode: String, CaseIterable, Identifiable {
    case contact
    case publicKeyText
    case publicKeyFile

    var id: String { rawValue }

    func title(strings: AppStrings) -> String {
        switch self {
        case .contact:
            strings.text(.savedContact)
        case .publicKeyText:
            strings.text(.publicKeyText)
        case .publicKeyFile:
            strings.text(.publicKeyFile)
        }
    }
}

private enum OfflineSection: String, CaseIterable, Identifiable {
    case fileEncrypt
    case fileDecrypt
    case textEncrypt
    case textDecrypt

    var id: String { rawValue }

    func title(strings: AppStrings) -> String {
        switch self {
        case .fileEncrypt:
            strings.text(.fileEncrypt)
        case .fileDecrypt:
            strings.text(.fileDecrypt)
        case .textEncrypt:
            strings.text(.textEncrypt)
        case .textDecrypt:
            strings.text(.textDecrypt)
        }
    }
}
