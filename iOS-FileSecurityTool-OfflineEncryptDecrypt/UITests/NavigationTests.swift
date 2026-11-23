import XCTest

final class NavigationTests: XCTestCase {
    @MainActor func testOfflineNavigationAndContactValidation() throws {
        let app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(en)", "-AppleLocale", "en_US", "-language", "en"]
        app.launch()
        XCTAssertTrue(app.buttons["Choose a file"].waitForExistence(timeout: 10))
        app.segmentedControls.buttons["Text"].tap()
        XCTAssertTrue(app.textViews["Message"].exists)
        app.segmentedControls.buttons["Decrypt"].tap()
        XCTAssertTrue(app.textViews["Encrypted text"].exists)
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
        XCTAssertTrue(app.staticTexts["让隐私，留在你手中。"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.buttons["选择文件"].exists)
        let attachment = XCTAttachment(screenshot: app.screenshot()); attachment.lifetime = .keepAlways; add(attachment)
    }
}
