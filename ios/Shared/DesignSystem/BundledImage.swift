import SwiftUI
import UIKit

struct BundledImage: View {
    let name: String
    var contentMode: ContentMode = .fill

    var body: some View {
        Group {
            if let url = Bundle.main.url(forResource: name, withExtension: "png"),
               let image = UIImage(contentsOfFile: url.path) {
                Image(uiImage: image)
                    .resizable()
                    .aspectRatio(contentMode: contentMode)
            } else {
                Color.clear
            }
        }
        .accessibilityHidden(true)
    }
}
