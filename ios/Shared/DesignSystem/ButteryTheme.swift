import SwiftUI

enum ButteryTheme {
    static let paprika = Color(hex: 0xA95D32)
    static let herb = Color(hex: 0x718067)
    static let cream = Color(hex: 0xF4EFE6)
    static let charcoal = Color(hex: 0x211F1B)
    static let deepCharcoal = Color(hex: 0x121212)
    static let navy = Color(hex: 0x111A23)
    static let butter = Color(hex: 0xFFC857)
    static let gold = Color(hex: 0xC4A46B)
}

extension Color {
    init(hex: UInt, alpha: Double = 1) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xff) / 255,
            green: Double((hex >> 8) & 0xff) / 255,
            blue: Double(hex & 0xff) / 255,
            opacity: alpha
        )
    }
}
