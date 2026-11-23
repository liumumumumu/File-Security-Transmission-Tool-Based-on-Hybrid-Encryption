import SwiftUI

@main struct FileSecurityApp: App {
    @StateObject private var store = AppStore()
    @AppStorage("appearance") private var appearance = "system"
    @AppStorage("language") private var language = "system"
    var body: some Scene {
        WindowGroup {
            RootView().environmentObject(store)
                .tint(Palette.teal)
                .preferredColorScheme(appearance == "system" ? nil : appearance == "dark" ? .dark : .light)
                .environment(\.locale, language == "system" ? .current : Locale(identifier: language))
                .onOpenURL { store.incoming = $0 }
                .alert("Unable to complete", isPresented: Binding(get: { store.error != nil }, set: { if !$0 { store.error = nil } })) {
                    Button("OK", role: .cancel) { store.error = nil }
                } message: { Text(store.error ?? "") }
        }
    }
}
struct RootView: View {
    @EnvironmentObject var store: AppStore
    @State private var tab = 0
    var body: some View {
        TabView(selection: $tab) {
            WorkspaceView().tabItem { Label("Workspace", systemImage: "lock.doc") }.tag(0)
            ContactsView().tabItem { Label("Contacts", systemImage: "person.2") }.tag(1)
            KeysView().tabItem { Label("My key", systemImage: "key") }.tag(2)
            SettingsView().tabItem { Label("Settings", systemImage: "slider.horizontal.3") }.tag(3)
        }
        .onChange(of: store.incoming) { _, value in if value != nil { tab = 0 } }
    }
}
enum Palette {
    static let teal = Color(red: 0.04, green: 0.51, blue: 0.48)
    static let navy = Color(red: 0.06, green: 0.15, blue: 0.22)
}
struct Card<Content: View>: View {
    @ViewBuilder var content: Content
    var body: some View {
        VStack(alignment: .leading, spacing: 16) { content }
            .padding(20).frame(maxWidth: .infinity, alignment: .leading)
            .background(Color(uiColor: .secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 24))
    }
}
struct Page<Content: View>: View {
    let title: LocalizedStringKey
    @ViewBuilder var content: Content
    var body: some View {
        NavigationStack {
            ScrollView { VStack(alignment: .leading, spacing: 20) { content }.padding(20).frame(maxWidth: 650).frame(maxWidth: .infinity) }
                .background(Color(uiColor: .systemGroupedBackground))
                .navigationTitle(title)
        }
    }
}
struct PrimaryButton: View {
    let title: LocalizedStringKey
    let icon: String
    var action: () -> Void
    var body: some View {
        Button(action: action) { Label(title, systemImage: icon).font(.headline).frame(maxWidth: .infinity).padding(.vertical, 9) }
            .buttonStyle(.borderedProminent).buttonBorderShape(.roundedRectangle(radius: 16))
    }
}
struct FingerprintView: View {
    let pem: String
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("SHA-256 fingerprint").font(.caption).foregroundStyle(.secondary)
            Text((try? PublicKey.fingerprint(pem)) ?? "—").font(.system(.caption, design: .monospaced)).textSelection(.enabled).foregroundStyle(.secondary)
        }
    }
}
