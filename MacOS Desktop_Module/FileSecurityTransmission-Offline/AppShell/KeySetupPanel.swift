import AppKit
import SwiftUI
import UniformTypeIdentifiers

struct KeySetupPanel: View {
    @ObservedObject var viewModel: AppViewModel
    @State private var privateKeyText = ""
    @State private var privateKeyPath = ""
    @State private var showImport = false
    @State private var isDropTargeted = false

    var body: some View {
        let strings = viewModel.strings

        VStack(alignment: .leading, spacing: 14) {
            HStack(alignment: .top, spacing: 14) {
                Image(systemName: viewModel.isPrivateKeyMissing ? "key.slash" : "key")
                    .font(.title2)
                    .foregroundStyle(viewModel.isPrivateKeyMissing ? .orange : .green)
                    .frame(width: 30)

                VStack(alignment: .leading, spacing: 6) {
                    Text(viewModel.isPrivateKeyMissing ? strings.text(.keyMissing) : strings.text(.keyStatus))
                        .font(.headline)
                    Text(statusDetail(strings: strings))
                        .font(.callout)
                        .foregroundStyle(.secondary)
                        .textSelection(.enabled)
                }

                Spacer()

                if viewModel.isPrivateKeyMissing {
                    missingKeyActions(strings: strings)
                } else {
                    exportActions(strings: strings)
                }
            }

            if showImport {
                importPanel(strings: strings)
            }

            if let artifact = viewModel.keyExportArtifact {
                exportResult(artifact: artifact, strings: strings)
            }

            if let message = viewModel.keyOperationMessage {
                Text(message)
                    .font(.callout)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(16)
        .background(Color(nsColor: .controlBackgroundColor), in: RoundedRectangle(cornerRadius: 8))
        .overlay {
            RoundedRectangle(cornerRadius: 8)
                .stroke(Color.secondary.opacity(0.18))
        }
        .task {
            await viewModel.refreshKeyState()
        }
    }

    private func statusDetail(strings: AppStrings) -> String {
        if viewModel.isPrivateKeyMissing {
            return strings.text(.keyMissingDetail)
        }
        return viewModel.keyStatus?.displayAccountId ?? viewModel.systemStatus?.accountId?.nilIfBlank ?? "-"
    }

    private func missingKeyActions(strings: AppStrings) -> some View {
        HStack(spacing: 10) {
            Button {
                Task { await viewModel.generateStartupKey() }
            } label: {
                Label(strings.text(.generateKey), systemImage: "plus.circle")
            }
            .buttonStyle(.borderedProminent)
            .disabled(viewModel.operationInProgress)

            Button {
                withAnimation(.snappy) {
                    showImport.toggle()
                }
            } label: {
                Label(strings.text(.importPrivateKey), systemImage: "square.and.arrow.down")
            }
            .disabled(viewModel.operationInProgress)

            Button {
                Task { await viewModel.skipStartupKeySetup() }
            } label: {
                Label(strings.text(.skipForNow), systemImage: "forward")
            }
            .disabled(viewModel.operationInProgress)
        }
    }

    private func exportActions(strings: AppStrings) -> some View {
        Grid(horizontalSpacing: 10, verticalSpacing: 10) {
            GridRow {
                Button {
                    Task { await viewModel.exportPublicKey() }
                } label: {
                    Label(strings.text(.exportPublicKey), systemImage: "person.crop.circle.badge.checkmark")
                        .frame(maxWidth: .infinity)
                }
                .disabled(viewModel.operationInProgress)

                Button {
                    Task { await viewModel.exportPrivateKey() }
                } label: {
                    Label(strings.text(.exportPrivateKey), systemImage: "lock.square")
                        .frame(maxWidth: .infinity)
                }
                .disabled(viewModel.operationInProgress)
            }

            GridRow {
                Button {
                    withAnimation(.snappy) {
                        showImport.toggle()
                    }
                } label: {
                    Label(strings.text(.importPrivateKey), systemImage: "square.and.arrow.down")
                        .frame(maxWidth: .infinity)
                }
                .disabled(viewModel.operationInProgress)

                Button {
                    Task { await viewModel.generateKeyPair() }
                } label: {
                    Label(strings.text(.generateKey), systemImage: "plus.circle")
                        .frame(maxWidth: .infinity)
                }
                .disabled(viewModel.operationInProgress)
            }
        }
        .frame(width: 430)
    }

    private func importPanel(strings: AppStrings) -> some View {
        HStack(alignment: .top, spacing: 14) {
            VStack(alignment: .leading, spacing: 8) {
                Text(strings.text(.pastePrivateKey))
                    .font(.subheadline.weight(.semibold))
                TextEditor(text: $privateKeyText)
                    .font(.system(.body, design: .monospaced))
                    .frame(minHeight: 150)
                    .scrollContentBackground(.hidden)
                    .background(Color(nsColor: .textBackgroundColor), in: RoundedRectangle(cornerRadius: 8))
                    .overlay {
                        RoundedRectangle(cornerRadius: 8)
                            .stroke(Color.secondary.opacity(0.18))
                    }
            }

            VStack(alignment: .leading, spacing: 8) {
                Text(strings.text(.privateKeyFile))
                    .font(.subheadline.weight(.semibold))

                VStack(spacing: 10) {
                    Image(systemName: "qrcode.viewfinder")
                        .font(.title2)
                        .foregroundStyle(isDropTargeted ? Color.accentColor : Color.secondary)
                    Text(privateKeyPath.isEmpty ? strings.text(.dropFile) : privateKeyPath)
                        .font(.callout)
                        .foregroundStyle(.secondary)
                        .lineLimit(3)
                        .textSelection(.enabled)
                    Button {
                        choosePrivateKeyFile()
                    } label: {
                        Label(strings.text(.chooseFile), systemImage: "folder")
                    }
                }
                .frame(maxWidth: .infinity, minHeight: 150)
                .background(isDropTargeted ? Color.accentColor.opacity(0.08) : Color(nsColor: .textBackgroundColor), in: RoundedRectangle(cornerRadius: 8))
                .overlay {
                    RoundedRectangle(cornerRadius: 8)
                        .stroke(isDropTargeted ? Color.accentColor : Color.secondary.opacity(0.25), style: StrokeStyle(lineWidth: 1.2, dash: [7, 5]))
                }
                .onDrop(of: [UTType.fileURL.identifier], isTargeted: $isDropTargeted) { providers in
                    loadFilePath(from: providers)
                }
            }

            VStack(spacing: 10) {
                Button {
                    Task {
                        if viewModel.isPrivateKeyMissing {
                            await viewModel.importStartupPrivateKey(privateKey: privateKeyText, privateKeyPath: privateKeyPath)
                        } else {
                            await viewModel.importPrivateKey(privateKey: privateKeyText, privateKeyPath: privateKeyPath)
                        }
                    }
                } label: {
                    Label(strings.text(.importPrivateKey), systemImage: "checkmark.circle")
                }
                .buttonStyle(.borderedProminent)
                .disabled(viewModel.operationInProgress)

                Button {
                    privateKeyText = ""
                    privateKeyPath = ""
                } label: {
                    Label(strings.text(.clear), systemImage: "xmark")
                }
            }
            .frame(width: 170)
        }
    }

    private func exportResult(artifact: KeyExportArtifact, strings: AppStrings) -> some View {
        HStack(alignment: .top, spacing: 16) {
            VStack(alignment: .leading, spacing: 8) {
                Grid(alignment: .leading, horizontalSpacing: 14, verticalSpacing: 6) {
                    GridRow {
                        Text(strings.text(.output)).foregroundStyle(.secondary)
                        Text(artifact.textPath ?? "-").textSelection(.enabled)
                    }
                    GridRow {
                        Text("PNG").foregroundStyle(.secondary)
                        Text(artifact.pngPath ?? "-").textSelection(.enabled)
                    }
                    GridRow {
                        Text("ASCII").foregroundStyle(.secondary)
                        Text(artifact.asciiPath ?? "-").textSelection(.enabled)
                    }
                }
                .font(.caption)

                HStack {
                    Button {
                        copy(artifact.keyText)
                    } label: {
                        Label(strings.text(.copy), systemImage: "doc.on.doc")
                    }
                    .disabled(artifact.keyText == nil)

                    Button {
                        copy(artifact.textPath)
                    } label: {
                        Label(strings.text(.copyPath), systemImage: "link")
                    }
                    .disabled(artifact.textPath?.nilIfBlank == nil)

                    Button {
                        reveal(artifact.pngPath ?? artifact.textPath)
                    } label: {
                        Label(strings.text(.revealInFinder), systemImage: "finder")
                    }
                    .disabled((artifact.pngPath ?? artifact.textPath)?.nilIfBlank == nil)
                }
            }

            Spacer()

            VStack(alignment: .leading, spacing: 8) {
                Text(strings.text(.qrPreview))
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.secondary)
                if let image = qrImage(path: artifact.pngPath) {
                    Image(nsImage: image)
                        .resizable()
                        .interpolation(.none)
                        .scaledToFit()
                        .frame(width: 128, height: 128)
                        .background(Color.white, in: RoundedRectangle(cornerRadius: 6))
                } else {
                    RoundedRectangle(cornerRadius: 6)
                        .fill(Color(nsColor: .textBackgroundColor))
                        .frame(width: 128, height: 128)
                        .overlay {
                            Image(systemName: "qrcode")
                                .font(.title)
                                .foregroundStyle(.secondary)
                        }
                }
            }
        }
        .padding(12)
        .background(Color(nsColor: .textBackgroundColor), in: RoundedRectangle(cornerRadius: 8))
    }

    private func choosePrivateKeyFile() {
        let panel = NSOpenPanel()
        panel.canChooseFiles = true
        panel.canChooseDirectories = false
        panel.allowsMultipleSelection = false
        if panel.runModal() == .OK, let url = panel.url {
            privateKeyPath = url.path
        }
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
                    privateKeyPath = url.path
                }
            }
        }
        return true
    }

    private func qrImage(path: String?) -> NSImage? {
        guard let path = path?.nilIfBlank else {
            return nil
        }
        return NSImage(contentsOfFile: path)
    }

    private func copy(_ value: String?) {
        guard let value = value?.nilIfBlank else {
            return
        }
        NSPasteboard.general.clearContents()
        NSPasteboard.general.setString(value, forType: .string)
    }

    private func reveal(_ value: String?) {
        guard let value = value?.nilIfBlank else {
            return
        }
        NSWorkspace.shared.activateFileViewerSelecting([URL(fileURLWithPath: value)])
    }
}
