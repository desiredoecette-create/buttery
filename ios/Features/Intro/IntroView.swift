import SwiftUI

struct IntroView: View {
    let onFinished: () -> Void
    @State private var revealLogo = false
    @State private var revealTagline = false

    var body: some View {
        ZStack {
            ButteryTheme.deepCharcoal.ignoresSafeArea()

            VStack(spacing: 4) {
                BundledImage(name: "buttery_wordmark", contentMode: .fit)
                    .frame(maxWidth: 320)
                    .scaleEffect(revealLogo ? 1 : 0.9)
                    .opacity(revealLogo ? 1 : 0)

                Text("your personal kitchen dashboard")
                    .font(.system(size: 14))
                    .tracking(1.7)
                    .foregroundStyle(.white)
                    .opacity(revealTagline ? 1 : 0)
            }
            .padding(.horizontal, 30)
        }
        .task {
            withAnimation(.easeOut(duration: 0.65)) { revealLogo = true }
            try? await Task.sleep(for: .milliseconds(650))
            withAnimation(.easeOut(duration: 0.5)) { revealTagline = true }
            try? await Task.sleep(for: .seconds(1))
            onFinished()
        }
    }
}
