import SwiftUI
import UniformTypeIdentifiers

struct WorkspaceView: View {
    @EnvironmentObject var store: AppStore
    @State private var decrypt = false
    @State private var textMode = false
    @State private var recipient = "self"
    @State private var scannedRecipient: String?
    @State private var scanningRecipient = false
    @State private var text = ""
    @State private var result = ""
    @State private var file: URL?
    @State private var importing = false
    @Environment(\.scenePhase) private var scenePhase
    var recipientPEM: String? {
        if recipient == "scanned" { return scannedRecipient }
        return recipient == "self" ? (store.publicPEM.isEmpty ? nil : store.publicPEM) : store.contacts.first { $0.id.uuidString == recipient }?.pem
    }
    var body: some View {
        Page(title: "Workspace") {
            HStack(alignment: .top, spacing: 16) {
                Image(systemName: "lock.shield.fill").font(.system(size: 32)).foregroundStyle(.mint).accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 8) {
                    Text("Private by design.").font(.title2.bold())
                    Text("Your files. Your keys. Entirely offline.").font(.subheadline).foregroundStyle(.white.opacity(0.75))
                }
                Spacer(minLength: 0)
            }.padding(24).frame(maxWidth: .infinity, alignment: .leading)
                .background(LinearGradient(colors: [Palette.navy, Palette.teal], startPoint: .topLeading, endPoint: .bottomTrailing), in: RoundedRectangle(cornerRadius: 26)).foregroundStyle(.white)
            Card {
                Picker("Operation", selection: $decrypt) { Text("Encrypt").tag(false); Text("Decrypt").tag(true) }.pickerStyle(.segmented)
                Picker("Content", selection: $textMode) { Label("File", systemImage: "doc").tag(false); Label("Text", systemImage: "text.alignleft").tag(true) }.pickerStyle(.segmented)
                if !decrypt {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Recipient").font(.headline)
                        Picker("Recipient", selection: $recipient) {
                            Text("My local key").tag("self")
                            if scannedRecipient != nil { Text("Scanned public key").tag("scanned") }
                            ForEach(store.contacts) { Text($0.name).tag($0.id.uuidString) }
                        }.pickerStyle(.menu).frame(maxWidth: .infinity, alignment: .leading)
                        Button { scanningRecipient = true } label: {
                            Label("Scan recipient public key", systemImage: "qrcode.viewfinder")
                        }.accessibilityIdentifier("scan-recipient")
                        if let pem = recipientPEM { FingerprintView(pem: pem) }
                        else { Text("Create your key, scan a public key, or add a contact to begin.").font(.footnote).foregroundStyle(.secondary) }
                        if recipient == "scanned" {
                            Text("Used for this session only. Not saved to Contacts.").font(.caption).foregroundStyle(.secondary)
                        }
                    }
                }
                if textMode {
                    HStack { Text(decrypt ? LocalizedStringKey("Encrypted text") : LocalizedStringKey("Message")).font(.headline); Spacer(); Button("Paste") { text = UIPasteboard.general.string ?? "" } }
                    TextEditor(text: $text).frame(minHeight: 150).scrollContentBackground(.hidden).padding(10)
                        .background(Color(uiColor: .tertiarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 14))
                        .accessibilityLabel(decrypt ? "Encrypted text" : "Message")
                    Text(decrypt ? LocalizedStringKey("Paste an FST-TEXT1 payload.") : LocalizedStringKey("Up to 16 KiB of UTF-8 text.")).font(.caption).foregroundStyle(.secondary)
                } else {
                    Button { importing = true } label: {
                        VStack(spacing: 12) {
                            Image(systemName: file == nil ? "doc.badge.plus" : "doc.fill").font(.system(size: 34))
                            if let file { Text(file.lastPathComponent).font(.headline).lineLimit(2) }
                            else { Text("Choose a file").font(.headline) }
                            Text("Browse Files or iCloud Drive").font(.caption).foregroundStyle(.secondary)
                        }.frame(maxWidth: .infinity).padding(.vertical, 28)
                            .background(Palette.teal.opacity(0.07), in: RoundedRectangle(cornerRadius: 18))
                            .overlay(RoundedRectangle(cornerRadius: 18).strokeBorder(Palette.teal.opacity(0.25), style: StrokeStyle(lineWidth: 1, dash: [6])))
                    }.buttonStyle(.plain).accessibilityLabel("Choose a file").accessibilityIdentifier("file-picker")
                    Text(decrypt ? LocalizedStringKey("Select an FST2 file encrypted for your local key.") : LocalizedStringKey("The original filename is hidden inside the encrypted file.")).font(.footnote).foregroundStyle(.secondary)
                }
                PrimaryButton(title: decrypt ? "Decrypt securely" : "Encrypt securely", icon: decrypt ? "lock.open" : "lock.fill") {
                    result = ""
                    if textMode { store.transformText(text, recipient: decrypt ? nil : recipientPEM) { result = $0 } }
                    else if let file { store.processFile(file, recipient: decrypt ? nil : recipientPEM) }
                }.disabled(store.busy || (!decrypt && recipientPEM == nil) || (decrypt && !KeyVault.exists) || (textMode ? text.isEmpty : file == nil))
            }.disabled(store.busy)
            if store.busy {
                Card {
                    HStack { ProgressView(); Text("Working securely…").font(.headline) }
                    if !textMode { ProgressView(value: store.progress); Button("Cancel task", role: .destructive) { store.cancel() } }
                    Text("Keep the app open while processing files.").font(.footnote).foregroundStyle(.secondary)
                }
            }
            if !result.isEmpty {
                Card {
                    Label("Result", systemImage: "checkmark.seal.fill").font(.headline).foregroundStyle(Palette.teal)
                    Text(result).font(.system(.callout, design: .monospaced)).textSelection(.enabled).lineLimit(12)
                    HStack { Button("Copy") { UIPasteboard.general.setItems([[UIPasteboard.typeAutomatic: result]], options: [.localOnly: true, .expirationDate: Date().addingTimeInterval(120)]) }; Spacer(); ShareLink(item: result) { Label("Share", systemImage: "square.and.arrow.up") } }
                    Button("Clear result", role: .destructive) { result = "" }
                }
            }
            if let output = store.output {
                Card {
                    Label("File ready", systemImage: "checkmark.seal.fill").font(.headline).foregroundStyle(Palette.teal)
                    Text(output.lastPathComponent).font(.callout).textSelection(.enabled)
                    Text("Save or share this file before clearing it or closing the app.").font(.footnote).foregroundStyle(.secondary)
                    ShareLink(item: output) { Label("Save or share file", systemImage: "square.and.arrow.up") }.buttonStyle(.borderedProminent)
                    Button("Clear temporary file", role: .destructive) { store.clearTemporaryFiles() }
                }
            }
            Label("AES-256-GCM · RSA-OAEP SHA-256", systemImage: "checkmark.shield").font(.caption).foregroundStyle(.secondary).frame(maxWidth: .infinity)
        }
        .sheet(isPresented: $scanningRecipient) {
            RecipientScannerView { pem in
                scannedRecipient = pem
                recipient = "scanned"
                result = ""
            }
        }
        .fileImporter(isPresented: $importing, allowedContentTypes: [.data], allowsMultipleSelection: false) { outcome in
            do { file = try outcome.get().first } catch { store.error = error.localizedDescription }
        }
        .onChange(of: store.incoming) { _, incoming in
            if let incoming { file = incoming; decrypt = true; textMode = false; result = ""; store.incoming = nil }
        }
        .onChange(of: decrypt) { _, _ in result = "" }
        .onChange(of: textMode) { _, _ in result = "" }
        .onChange(of: scenePhase) { _, phase in
            if phase == .background { store.cancel(); result = ""; text = "" }
        }
    }
}
