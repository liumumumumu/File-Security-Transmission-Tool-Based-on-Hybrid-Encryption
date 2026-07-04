import SwiftUI

struct ContentView: View {
    @StateObject private var viewModel = AppViewModel()
    @State private var consoleError: String?
    @State private var selectedTab: AppTab = .relay
    @State private var scrollResetToken = 0

    var body: some View {
        let strings = viewModel.strings

        GeometryReader { viewport in
            ScrollViewReader { scrollProxy in
                ScrollView([.horizontal, .vertical]) {
                    VStack(alignment: .leading, spacing: 0) {
                        Color.clear
                            .frame(width: 1, height: 1)
                            .id(ScrollAnchor.topLeading)

                        HeaderBar(viewModel: viewModel) { serviceAvailable in
                            openConsole(serviceAvailable: serviceAvailable)
                        }

                        switch viewModel.serviceState {
                        case .starting:
                            ServiceStartingView(strings: strings)
                        case .ready:
                            VStack(spacing: 0) {
                                KeySetupPanel(viewModel: viewModel)
                                    .padding(.horizontal, 22)
                                    .padding(.top, 16)
                                    .padding(.bottom, 10)

                                if let notification = viewModel.inAppNotification {
                                    InAppNotificationBanner(message: notification, dismiss: viewModel.dismissInAppNotification)
                                        .padding(.horizontal, 22)
                                        .padding(.bottom, 10)
                                }

                                TabView(selection: $selectedTab) {
                                    RelayView(viewModel: viewModel, onSectionChange: requestScrollReset)
                                        .tabItem {
                                            Label(strings.text(.relayTab), systemImage: "arrow.left.arrow.right")
                                        }
                                        .tag(AppTab.relay)

                                    OfflineView(viewModel: viewModel, onSectionChange: requestScrollReset)
                                        .tabItem {
                                            Label(strings.text(.offlineTab), systemImage: "lock.doc")
                                        }
                                        .tag(AppTab.offline)
                                }
                                .frame(height: max(viewport.size.height - 180, 500))
                            }
                        case .unavailable(let message):
                            ServiceUnavailableView(
                                strings: strings,
                                message: message,
                                retry: {
                                    Task { await viewModel.bootstrap() }
                                },
                                openConsole: {
                                    openConsole(serviceAvailable: false)
                                }
                            )
                        }
                    }
                    .frame(
                        minWidth: max(viewport.size.width, 980),
                        minHeight: max(viewport.size.height, 680),
                        alignment: .topLeading
                    )
                }
                .scrollIndicators(.automatic)
                .onChange(of: selectedTab) {
                    requestScrollReset()
                }
                .onChange(of: scrollResetToken) {
                    withAnimation(.snappy) {
                        scrollProxy.scrollTo(ScrollAnchor.topLeading, anchor: .topLeading)
                    }
                }
            }
        }
        .frame(minWidth: 980, minHeight: 680)
        .task {
            await viewModel.bootstrap()
        }
        .alert("Console Unavailable", isPresented: Binding(
            get: { consoleError != nil },
            set: { if !$0 { consoleError = nil } }
        )) {
            Button("OK", role: .cancel) { consoleError = nil }
        } message: {
            Text(consoleError ?? "")
        }
    }

    private func openConsole(serviceAvailable: Bool) {
        do {
            try ConsoleLauncher.openConsole(serviceAvailable: serviceAvailable)
        } catch {
            consoleError = error.localizedDescription
        }
    }

    private func requestScrollReset() {
        scrollResetToken &+= 1
    }
}

private enum AppTab: Hashable {
    case relay
    case offline
}

private enum ScrollAnchor {
    static let topLeading = "content-top-leading"
}

private struct InAppNotificationBanner: View {
    let message: String
    let dismiss: () -> Void

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "bell.badge")
                .foregroundStyle(Color.accentColor)
            Text(message)
                .font(.callout.weight(.medium))
            Spacer()
            Button(action: dismiss) {
                Image(systemName: "xmark")
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .background(Color.accentColor.opacity(0.10), in: RoundedRectangle(cornerRadius: 8))
        .overlay {
            RoundedRectangle(cornerRadius: 8)
                .stroke(Color.accentColor.opacity(0.22))
        }
    }
}

private struct HeaderBar: View {
    @ObservedObject var viewModel: AppViewModel
    let openConsole: (Bool) -> Void

    var body: some View {
        let strings = viewModel.strings

        HStack(spacing: 16) {
            VStack(alignment: .leading, spacing: 3) {
                Text(strings.text(.appTitle))
                    .font(.headline)
                serviceLine(strings: strings)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            Spacer()

            Picker(strings.text(.language), selection: Binding(
                get: { viewModel.language },
                set: { newValue in
                    Task { await viewModel.setLanguage(newValue) }
                }
            )) {
                ForEach(AppLanguage.allCases) { language in
                    Text(language.displayName).tag(language)
                }
            }
            .pickerStyle(.segmented)
            .frame(width: 180)

            Label(
                viewModel.eventStreamConnected ? strings.text(.eventStreamConnected) : strings.text(.eventStreamDisconnected),
                systemImage: viewModel.eventStreamConnected ? "dot.radiowaves.left.and.right" : "arrow.triangle.2.circlepath"
            )
            .font(.caption)
            .foregroundStyle(viewModel.eventStreamConnected ? .green : .secondary)

            Button {
                let serviceAvailable = viewModel.serviceState == .ready
                openConsole(serviceAvailable)
            } label: {
                Label(strings.text(.openConsole), systemImage: "terminal")
            }
        }
        .padding(.horizontal, 22)
        .padding(.vertical, 14)
        .background(.bar)
    }

    @ViewBuilder
    private func serviceLine(strings: AppStrings) -> some View {
        if let status = viewModel.systemStatus {
            Text("\(strings.text(.serviceReady)) · \(strings.text(.server)): \(status.clientServerHost ?? "-"):\(status.clientServerPort.map(String.init) ?? "-")")
        } else {
            Text(strings.text(.localService))
        }
    }
}

private struct ServiceStartingView: View {
    let strings: AppStrings

    var body: some View {
        VStack(spacing: 14) {
            ProgressView()
                .controlSize(.large)
            Text(strings.text(.starting))
                .font(.title3.weight(.medium))
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

private struct ServiceUnavailableView: View {
    let strings: AppStrings
    let message: String
    let retry: () -> Void
    let openConsole: () -> Void

    var body: some View {
        VStack(spacing: 18) {
            Image(systemName: "exclamationmark.triangle")
                .font(.system(size: 44, weight: .regular))
                .foregroundStyle(.orange)

            VStack(spacing: 8) {
                Text(strings.text(.unavailable))
                    .font(.title2.weight(.semibold))
                Text(message)
                    .font(.callout)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: 560)
            }

            HStack(spacing: 12) {
                Button(action: retry) {
                    Label(strings.text(.retry), systemImage: "arrow.clockwise")
                }
                .buttonStyle(.borderedProminent)

                Button(action: openConsole) {
                    Label(strings.text(.openConsole), systemImage: "terminal")
                }
            }
        }
        .padding(32)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}
