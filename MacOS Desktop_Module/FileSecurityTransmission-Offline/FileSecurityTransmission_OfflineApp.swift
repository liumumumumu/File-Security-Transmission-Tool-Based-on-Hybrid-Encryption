//
//  FileSecurityTransmission_OfflineApp.swift
//  FileSecurityTransmission-Offline
//
//  Created by zero on 6/9/26.
//

import SwiftUI

@main
struct FileSecurityTransmission_OfflineApp: App {
    @NSApplicationDelegateAdaptor(AppTerminationDelegate.self) private var appDelegate

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
