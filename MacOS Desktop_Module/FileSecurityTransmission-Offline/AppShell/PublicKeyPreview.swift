import AppKit
import SwiftUI

struct PublicKeyPreview: View {
    @ObservedObject var viewModel: AppViewModel
    @Environment(\.dismiss) private var dismiss
    @State private var image: NSImage?
    @State private var fingerprint: String?
    @State private var isLoading = true
    @State private var failed = false

    var body: some View {
        let strings = viewModel.strings

        VStack(spacing: 20) {
            Text(strings.text(.showPublicKey))
                .font(.title2.weight(.semibold))

            if isLoading {
                ProgressView()
                    .frame(width: 300, height: 300)
            } else if let image, let fingerprint {
                Image(nsImage: image)
                    .resizable()
                    .interpolation(.none)
                    .scaledToFit()
                    .frame(width: 300, height: 300)
                    .padding(16)
                    .background(Color.white, in: RoundedRectangle(cornerRadius: 8))
                    .accessibilityLabel(strings.text(.qrPreview))

                VStack(spacing: 8) {
                    Text(strings.text(.publicKeyFingerprint))
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.secondary)
                    Text(fingerprint)
                        .font(.system(.callout, design: .monospaced))
                        .multilineTextAlignment(.center)
                        .textSelection(.enabled)
                        .fixedSize(horizontal: false, vertical: true)
                }
            } else if failed {
                Text(strings.text(.publicKeyPreviewUnavailable))
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .frame(minHeight: 180)
            }

            Button(strings.text(.close)) {
                dismiss()
            }
            .keyboardShortcut(.cancelAction)
        }
        .padding(24)
        .frame(width: 420)
        .task {
            do {
                let preview = try await viewModel.publicKeyPreview()
                guard let path = preview.artifact.pngPath?.nilIfBlank,
                      let qrImage = NSImage(contentsOfFile: path) else {
                    failed = true
                    isLoading = false
                    return
                }
                image = qrImage
                fingerprint = preview.fingerprint
            } catch {
                failed = true
            }
            isLoading = false
        }
    }
}
