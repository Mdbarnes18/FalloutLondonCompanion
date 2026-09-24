import SwiftUI

@main
struct FalloutLondonApp: App {
    @StateObject private var appState = AppState()
    var body: some Scene {
        WindowGroup { RootView().environmentObject(appState) }
    }
}
