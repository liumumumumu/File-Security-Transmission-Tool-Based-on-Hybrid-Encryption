// swift-tools-version: 5.9
import PackageDescription
let package = Package(name: "FileSecurityCore", platforms: [.macOS(.v13), .iOS(.v17)], products: [.library(name: "FileSecurityCore", targets: ["FileSecurityCore"])], targets: [.target(name: "FileSecurityCore", path: "FileSecurity/Core"), .testTarget(name: "FileSecurityCoreTests", dependencies: ["FileSecurityCore"], path: "Tests", resources: [.copy("Vectors.json")])])
