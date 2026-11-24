import SwiftUI
import AVFoundation
import VisionKit

/// A scanned public key is transient: it never changes the contact book.
struct RecipientScannerView: View {
    var onSelect: (String) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var cameraReady = false
    @State private var publicKey: String?
    @State private var error: String?

    var body: some View {
        NavigationStack {
            Group {
                if let publicKey {
                    VStack(alignment: .leading, spacing: 24) {
                        Label("Scanned public key", systemImage: "checkmark.shield.fill")
                            .font(.title2.bold()).foregroundStyle(Palette.teal)
                        Text("Verify this fingerprint with the recipient before encrypting.")
                            .foregroundStyle(.secondary)
                        FingerprintView(pem: publicKey)
                        PrimaryButton(title: "Use this recipient", icon: "checkmark") {
                            onSelect(publicKey)
                            dismiss()
                        }
                        Button("Scan again") { self.publicKey = nil }
                        Spacer()
                    }.padding(24)
                } else if let error {
                    ContentUnavailableView {
                        Label("Unable to scan", systemImage: "qrcode.viewfinder")
                    } description: {
                        Text(error)
                    } actions: {
                        Button("Try again") { Task { await prepareCamera() } }
                    }
                } else if cameraReady {
                    QRScanner(onFailure: { error in self.error = error.localizedDescription }) { payload in
                        do { publicKey = try PublicKey.normalize(payload) }
                        catch { self.error = error.localizedDescription }
                    }
                } else {
                    ProgressView()
                }
            }
            .navigationTitle("Scan recipient public key")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
        .task { await prepareCamera() }
    }

    @MainActor private func prepareCamera() async {
        error = nil
        guard DataScannerViewController.isSupported else {
            error = String(localized: "Live QR scanning is unavailable on this device.")
            return
        }
        guard await AVCaptureDevice.requestAccess(for: .video) else {
            error = String(localized: "Allow camera access in iPhone Settings to scan a public key.")
            return
        }
        guard DataScannerViewController.isAvailable else {
            error = String(localized: "The camera is currently unavailable. Please try again.")
            return
        }
        cameraReady = true
    }
}
