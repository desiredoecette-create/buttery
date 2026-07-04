import SwiftUI
import UIKit

private struct KeepScreenAwakeModifier: ViewModifier {
    func body(content: Content) -> some View {
        content
            .onAppear {
                UIApplication.shared.isIdleTimerDisabled = true
            }
            .onDisappear {
                UIApplication.shared.isIdleTimerDisabled = false
            }
    }
}

extension View {
    func keepScreenAwake() -> some View {
        modifier(KeepScreenAwakeModifier())
    }
}
