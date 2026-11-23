import SwiftUI
import CoreImage.CIFilterBuiltins

struct KeysView: View {
    @EnvironmentObject var store: AppStore
    @State private var deleting = false
    var qr: UIImage? {
        guard let artifact = try? PublicKey.artifact(store.publicPEM) else { return nil }
        let filter = CIFilter.qrCodeGenerator(); filter.message = Data(artifact.utf8); filter.correctionLevel = "M"
        guard let image = filter.outputImage, let cg = CIContext().createCGImage(image.transformed(by: CGAffineTransform(scaleX: 8, y: 8)), from: image.extent.applying(CGAffineTransform(scaleX: 8, y: 8))) else { return nil }
        return UIImage(cgImage: cg)
    }
    var body: some View {
        Page(title: "My key") {
            Card {
                Label("Your local identity", systemImage: "key.horizontal.fill").font(.headline)
                Text("People encrypt with your public key. Only your private key can unlock their files and messages.").font(.subheadline).foregroundStyle(.secondary)
                if store.publicPEM.isEmpty {
                    if KeyVault.exists {
                        PrimaryButton(title: "Restore public key display", icon: "arrow.clockwise") { store.recoverPublicKey() }
                    } else {
                        PrimaryButton(title: "Create local key", icon: "key.fill") { store.generateKey() }
                    }
                    Text("A device passcode is required. Keep this key: losing it makes old encrypted content unreadable.").font(.footnote).foregroundStyle(.secondary)
                } else {
                    Label("Local key is ready", systemImage: "checkmark.shield.fill").foregroundStyle(Palette.teal)
                    if let qr {
                        Image(uiImage: qr).interpolation(.none).resizable().scaledToFit().frame(maxWidth: 240).padding(16).background(.white, in: RoundedRectangle(cornerRadius: 18)).frame(maxWidth: .infinity)
                        ShareLink(item: Image(uiImage: qr), preview: SharePreview("Public key QR", image: Image(uiImage: qr))) { Label("Share QR image", systemImage: "qrcode") }.frame(maxWidth: .infinity)
                    }
                    FingerprintView(pem: store.publicPEM)
                    ShareLink(item: (try? PublicKey.artifact(store.publicPEM)) ?? store.publicPEM) { Label("Share public key", systemImage: "square.and.arrow.up") }.buttonStyle(.borderedProminent)
                    DisclosureGroup("View PEM public key") { Text(store.publicPEM).font(.system(.caption2, design: .monospaced)).textSelection(.enabled) }
                }
            }
            Card {
                Label("Protected on this iPhone", systemImage: "iphone.gen3.radiowaves.left.and.right").font(.headline)
                Text("The RSA private key stays in this device’s Keychain. Decryption requires Face ID, Touch ID or your device passcode. Keys are excluded from backup and cannot be exported.").font(.subheadline).foregroundStyle(.secondary)
                Text("RSA-2048 · OAEP SHA-256 / MGF1-SHA256").font(.caption).foregroundStyle(.secondary)
                if KeyVault.exists { Button("Delete local key", role: .destructive) { deleting = true }.disabled(store.busy) }
            }
        }
        .disabled(store.busy)
        .confirmationDialog("Permanently delete local key?", isPresented: $deleting, titleVisibility: .visible) {
            Button("Delete permanently", role: .destructive) { store.deleteKey() }
            Button("Cancel", role: .cancel) {}
        } message: { Text("All content encrypted for this key will become permanently unreadable. This cannot be undone.") }
    }
}
struct SettingsView: View {
    @EnvironmentObject var store: AppStore
    @AppStorage("appearance") private var appearance = "system"
    @AppStorage("language") private var language = "system"
    var body: some View {
        Page(title: "Settings") {
            Card {
                Label("Make it yours", systemImage: "paintpalette").font(.headline)
                Picker("Appearance", selection: $appearance) { Text("System").tag("system"); Text("Light").tag("light"); Text("Dark").tag("dark") }
                Picker("Language", selection: $language) { Text("System").tag("system"); Text("简体中文").tag("zh-Hans"); Text("English").tag("en") }
            }
            Card {
                Label("Privacy & storage", systemImage: "hand.raised").font(.headline)
                Text("No accounts, servers, analytics or network requests. Contacts stay on this device. Files you choose from iCloud may be downloaded by the system Files provider.").font(.subheadline).foregroundStyle(.secondary)
                Text("Results are temporary. Use Save or share to keep them. Temporary files are cleared on the next launch.").font(.subheadline).foregroundStyle(.secondary)
                Button("Clear temporary files", role: .destructive) { store.clearTemporaryFiles() }.disabled(store.busy)
            }
            Card {
                Label("File Security", systemImage: "lock.shield").font(.headline)
                Text("Version 1.0.0 · iOS 17+").font(.subheadline).foregroundStyle(.secondary)
                Text("Compatible with Android and Java FST2 / FST-TEXT1. This development build still needs cross-device and real-iPhone validation before release.").font(.footnote).foregroundStyle(.secondary)
            }
        }
    }
}
