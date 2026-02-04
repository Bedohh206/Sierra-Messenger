import SwiftUI

@main
struct BluetoothMessengerApp: App {
    @StateObject private var router = MessageRouter()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(router)
        }
    }
}
