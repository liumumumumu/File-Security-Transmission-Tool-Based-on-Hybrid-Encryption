import SwiftUI
import PhotosUI
import Vision
import VisionKit
import AVFoundation

struct ContactsView: View {
    @EnvironmentObject var store: AppStore
    @State private var adding = false
    @State private var search = ""
    @State private var deleting: Contact?
    var filtered: [Contact] { store.contacts.filter { search.isEmpty || $0.name.localizedCaseInsensitiveContains(search) } }
    var body: some View {
        Page(title: "Contacts") {
            Card {
                Label("A trusted address book", systemImage: "person.crop.circle.badge.checkmark").font(.headline)
                Text("Store public keys locally. Verify fingerprints with your contacts before encrypting sensitive content.").font(.subheadline).foregroundStyle(.secondary)
                PrimaryButton(title: "Add contact", icon: "person.badge.plus") { adding = true }
                TextField("Search contacts", text: $search).textFieldStyle(.roundedBorder)
            }
            if filtered.isEmpty {
                ContentUnavailableView("No contacts yet", systemImage: "person.2", description: Text("Add a public key by pasting, importing, or scanning a QR code."))
            }
            ForEach(filtered) { contact in
                Card {
                    HStack {
                        Text(String(contact.name.prefix(1)).uppercased()).font(.title2.bold()).foregroundStyle(Palette.teal).frame(width: 48, height: 48).background(Palette.teal.opacity(0.1), in: Circle())
                        Text(contact.name).font(.headline)
                        Spacer()
                        Button(role: .destructive) { deleting = contact } label: { Image(systemName: "trash") }.accessibilityLabel("Delete contact")
                    }
                    FingerprintView(pem: contact.pem)
                    ShareLink(item: (try? PublicKey.artifact(contact.pem)) ?? contact.pem) { Label("Share public key", systemImage: "square.and.arrow.up") }
                }
            }
        }
        .sheet(isPresented: $adding) { AddContactView() }
        .confirmationDialog("Delete contact?", isPresented: Binding(get: { deleting != nil }, set: { if !$0 { deleting = nil } }), titleVisibility: .visible) {
            Button("Delete contact", role: .destructive) {
                do { try store.saveContacts(store.contacts.filter { $0.id != deleting?.id }); deleting = nil } catch { store.error = error.localizedDescription }
            }
        }
    }
}
struct AddContactView: View {
    @EnvironmentObject var store: AppStore
    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var payload = ""
    @State private var importing = false
    @State private var scanning = false
    @State private var photo: PhotosPickerItem?
    @State private var error: String?
    var body: some View {
        NavigationStack {
            Form {
                Section("Contact") { TextField("Name", text: $name).textInputAutocapitalization(.words) }
                Section("Public key") {
                    TextEditor(text: $payload).frame(minHeight: 150).font(.system(.caption, design: .monospaced)).autocorrectionDisabled().textInputAutocapitalization(.never).accessibilityLabel("Public key")
                    Button("Paste public key") { payload = UIPasteboard.general.string ?? "" }
                    Button("Import key file") { importing = true }
                    PhotosPicker(selection: $photo, matching: .images) { Label("Read QR from image", systemImage: "photo") }
                    Button { requestScan() } label: { Label("Scan QR code", systemImage: "qrcode.viewfinder") }
                }
                if let normalized = try? PublicKey.normalize(payload) {
                    Section { FingerprintView(pem: normalized) }
                }
                Section { Text("Accepts PEM, FST-PUB1 and FST-KEY1 public keys.").font(.footnote) }
            }
            .navigationTitle("Add contact").navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) { Button("Save") {
                    do { try store.addContact(name: name, payload: payload); dismiss() } catch { self.error = error.localizedDescription }
                }.disabled(name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || (try? PublicKey.normalize(payload)) == nil) }
            }
        }
        .fileImporter(isPresented: $importing, allowedContentTypes: [.data]) { result in
            do {
                let url = try result.get(); let access = url.startAccessingSecurityScopedResource(); defer { if access { url.stopAccessingSecurityScopedResource() } }
                let handle = try FileHandle(forReadingFrom: url); defer { try? handle.close() }
                let data = try handle.read(upToCount: 16385) ?? Data(); try require(data.count <= 16384, "Public key file is too large")
                guard let string = String(data: data, encoding: .utf8) else { throw FSTError.invalid("Invalid UTF-8") }; payload = try PublicKey.normalize(string)
            } catch { self.error = error.localizedDescription }
        }
        .sheet(isPresented: $scanning) {
            NavigationStack {
                QRScanner { value in
                    scanning = false
                    do { payload = try PublicKey.normalize(value) } catch { self.error = error.localizedDescription }
                }
                    .navigationTitle("Scan public key").navigationBarTitleDisplayMode(.inline)
                    .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Cancel") { scanning = false } } }
            }
        }
        .onChange(of: photo) { _, selected in
            Task {
                do {
                    guard let data = try await selected?.loadTransferable(type: Data.self) else { return }
                    let value = try await Task.detached {
                        let request = VNDetectBarcodesRequest(); request.symbologies = [.qr]
                        try VNImageRequestHandler(data: data).perform([request])
                        guard let payload = request.results?.first?.payloadStringValue else { throw FSTError.invalid("No QR code found") }; return payload
                    }.value
                    payload = try PublicKey.normalize(value)
                } catch { self.error = error.localizedDescription }
            }
        }
        .alert("Unable to import", isPresented: Binding(get: { error != nil }, set: { if !$0 { error = nil } })) { Button("OK", role: .cancel) {} } message: { Text(error ?? "") }
    }
    private func requestScan() {
        guard DataScannerViewController.isSupported else { error = "Live scanning is unavailable. Import a QR image instead."; return }
        Task {
            guard await AVCaptureDevice.requestAccess(for: .video) else { error = "Allow camera access in iPhone Settings to scan a QR code."; return }
            guard DataScannerViewController.isAvailable else { error = "Camera is currently unavailable."; return }
            scanning = true
        }
    }
}
struct QRScanner: UIViewControllerRepresentable {
    var onFailure: (Error) -> Void = { _ in }
    var onRead: (String) -> Void
    func makeCoordinator() -> Coordinator { Coordinator(onRead: onRead, onFailure: onFailure) }
    func makeUIViewController(context: Context) -> DataScannerViewController {
        let controller = DataScannerViewController(recognizedDataTypes: [.barcode(symbologies: [.qr])], qualityLevel: .balanced, recognizesMultipleItems: false, isHighlightingEnabled: true)
        controller.delegate = context.coordinator
        do { try controller.startScanning() }
        catch { DispatchQueue.main.async { context.coordinator.onFailure(error) } }
        return controller
    }
    func updateUIViewController(_ controller: DataScannerViewController, context: Context) {}
    static func dismantleUIViewController(_ controller: DataScannerViewController, coordinator: Coordinator) { controller.stopScanning() }
    final class Coordinator: NSObject, DataScannerViewControllerDelegate {
        let onRead: (String) -> Void
        var delivered = false
        let onFailure: (Error) -> Void
        init(onRead: @escaping (String) -> Void, onFailure: @escaping (Error) -> Void) {
            self.onRead = onRead
            self.onFailure = onFailure
        }
        func dataScanner(_ scanner: DataScannerViewController, becameUnavailableWithError error: DataScannerViewController.ScanningUnavailable) {
            onFailure(error)
        }
        func dataScanner(_ scanner: DataScannerViewController, didAdd addedItems: [RecognizedItem], allItems: [RecognizedItem]) {
            guard !delivered else { return }
            for item in addedItems {
                if case .barcode(let code) = item, let value = code.payloadStringValue {
                    delivered = true; scanner.stopScanning(); onRead(value); return
                }
            }
        }
    }
}
