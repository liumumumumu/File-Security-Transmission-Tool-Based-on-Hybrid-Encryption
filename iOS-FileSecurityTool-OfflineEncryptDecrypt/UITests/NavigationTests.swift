import XCTest

final class NavigationTests: XCTestCase {
    @MainActor func testKeySetupOrOfflineNavigation() throws {
        let app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(en)", "-AppleLocale", "en_US", "-language", "en"]
        app.launch()
        if app.buttons["Create local key"].waitForExistence(timeout: 2) || app.buttons["Restore public key display"].exists {
            XCTAssertFalse(app.buttons["file-picker"].exists)
            XCTAssertFalse(app.tabBars.buttons["Workspace"].exists)
            return
        }
        XCTAssertTrue(app.buttons["file-picker"].waitForExistence(timeout: 10))
        let multiple = app.switches["Multiple recipients"]
        XCTAssertTrue(multiple.exists)
        multiple.tap()
        XCTAssertFalse(app.buttons["Encrypt securely"].isEnabled)
        multiple.tap()
        XCTAssertTrue(app.buttons["scan-recipient"].exists)
        app.buttons["scan-recipient"].tap()
        XCTAssertTrue(app.navigationBars["Scan recipient public key"].waitForExistence(timeout: 5))
        app.buttons["Cancel"].tap()
        app.segmentedControls.buttons["Text"].tap()
        XCTAssertTrue(app.textViews["Message"].exists)
        app.segmentedControls.buttons["Decrypt"].tap()
        XCTAssertTrue(app.textViews["Encrypted text"].exists)
        XCTAssertFalse(app.buttons["scan-recipient"].exists)
        XCTAssertFalse(app.buttons["Decrypt securely"].isEnabled)
        app.tabBars.buttons["Contacts"].tap()
        app.buttons["Add contact"].tap()
        XCTAssertFalse(app.buttons["Save"].isEnabled)
        app.textFields["Name"].tap(); app.textFields["Name"].typeText("Invalid key")
        XCTAssertFalse(app.buttons["Save"].isEnabled)
        app.buttons["Cancel"].tap()
        app.tabBars.buttons["My key"].tap()
        XCTAssertTrue(app.buttons["Create local key"].exists || app.staticTexts["Local key is ready"].exists || app.buttons["Restore public key display"].exists)
        app.tabBars.buttons["Settings"].tap()
        XCTAssertTrue(app.staticTexts["Privacy & storage"].exists)
        let attachment = XCTAttachment(screenshot: app.screenshot()); attachment.lifetime = .keepAlways; add(attachment)
    }
    @MainActor func testChineseWorkspace() throws {
        let app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(zh-Hans)", "-AppleLocale", "zh_CN", "-language", "zh-Hans"]
        app.launch()
        if app.buttons["创建本机密钥"].waitForExistence(timeout: 2) || app.buttons["恢复公钥显示"].exists {
            XCTAssertFalse(app.buttons["file-picker"].exists)
            return
        }
        XCTAssertTrue(app.staticTexts["让隐私，留在你手中。"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.buttons["file-picker"].exists)
        let attachment = XCTAttachment(screenshot: app.screenshot()); attachment.lifetime = .keepAlways; add(attachment)
    }
}
