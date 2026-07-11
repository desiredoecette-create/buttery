import SwiftUI

struct FeaturePlaceholderView: View {
    let destination: AppDestination

    var body: some View {
        ZStack {
            RadialGradient(
                colors: [Color(hex: 0x49331E), Color(hex: 0x0A0A08)],
                center: .center,
                startRadius: 20,
                endRadius: 650
            )
            .ignoresSafeArea()

            VStack(spacing: 16) {
                Image(systemName: symbol)
                    .font(.system(size: 42))
                    .foregroundStyle(ButteryTheme.butter)
                Text(destination.title)
                    .font(.system(size: 32, design: .serif))
                    .foregroundStyle(ButteryTheme.cream)
                Text("This feature is ready for the next iOS implementation chunk.")
                    .multilineTextAlignment(.center)
                    .foregroundStyle(ButteryTheme.cream.opacity(0.72))
                    .padding(.horizontal, 34)
            }
        }
        .navigationBarTitleDisplayMode(.inline)
    }

    private var symbol: String {
        switch destination {
        case .recipes: "books.vertical"
        case .newRecipe: "plus.circle"
        case .continueRecipe: "play.circle"
        case .favorites: "heart"
        case .grocery: "cart"
        case .explore: "globe.americas.fill"
        case .settings: "gearshape"
        case .inbox: "tray.full"
        case .privacySupport: "hand.raised"
        case .myProfile: "person.crop.circle"
        case .publicProfile: "person.crop.circle.badge.checkmark"
        }
    }
}
