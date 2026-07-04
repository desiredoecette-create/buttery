import SwiftUI

struct AmbientSlideshowView: View {
    let onDismiss: () -> Void
    @State private var imageIndex = 0
    @State private var images = availableImages.shuffled()

    private static let availableImages = [
        "ambient_pancakes",
        "ambient_salad",
        "ambient_salmon",
        "ambient_bread",
        "ambient_extra_01",
        "ambient_extra_02",
        "ambient_extra_03",
        "ambient_extra_04",
        "ambient_extra_05",
        "ambient_extra_06",
        "ambient_extra_07",
        "ambient_extra_08",
        "ambient_extra_09",
        "ambient_extra_10"
    ]

    var body: some View {
        GeometryReader { geometry in
            ZStack {
                BundledImage(name: images[imageIndex])
                    .frame(width: geometry.size.width, height: geometry.size.height)
                    .clipped()
                    .id(imageIndex)
                    .transition(.opacity)

                LinearGradient(
                    colors: [.clear, .clear, .black.opacity(0.7)],
                    startPoint: .top,
                    endPoint: .bottom
                )

                Text("Tap to start cooking")
                    .font(.system(size: 20, design: .serif))
                    .foregroundStyle(ButteryTheme.cream)
                    .padding(.bottom, 30)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)

                BundledImage(name: "buttery_wordmark", contentMode: .fit)
                    .frame(width: min(145, geometry.size.width * 0.4), height: 58)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
                    .padding(.bottom, 52)
            }
            .frame(width: geometry.size.width, height: geometry.size.height)
            .contentShape(Rectangle())
            .onTapGesture(perform: onDismiss)
        }
        .ignoresSafeArea()
        .background(.black)
        .task {
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(8))
                guard !Task.isCancelled else { return }
                withAnimation(.easeInOut(duration: 1.5)) {
                    imageIndex = (imageIndex + 1) % images.count
                }
            }
        }
    }
}
