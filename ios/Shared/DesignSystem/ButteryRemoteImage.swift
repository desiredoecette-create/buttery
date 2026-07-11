import SwiftUI
import UIKit
import ImageIO
@preconcurrency import FirebaseStorage

struct ButteryRemoteImage<Placeholder: View>: View {
    let url: URL?
    var contentMode: ContentMode = .fill
    var maxPixelDimension: CGFloat = 900
    @ViewBuilder var placeholder: () -> Placeholder

    @State private var image: UIImage?

    var body: some View {
        Group {
            if let image {
                Image(uiImage: image)
                    .resizable()
                    .aspectRatio(contentMode: contentMode)
            } else {
                placeholder()
            }
        }
        .task(id: url?.absoluteString) {
            image = nil
            guard let url else { return }
            if let cached = await ButteryRemoteImageCache.shared.image(for: url) {
                image = cached
                return
            }
            image = await ButteryRemoteImageCache.shared.load(url: url, maxPixelDimension: maxPixelDimension)
        }
    }
}

extension ButteryRemoteImage where Placeholder == MissingRemoteImagePlaceholder {
    init(url: URL?, contentMode: ContentMode = .fill, maxPixelDimension: CGFloat = 900) {
        self.url = url
        self.contentMode = contentMode
        self.maxPixelDimension = maxPixelDimension
        self.placeholder = { MissingRemoteImagePlaceholder() }
    }
}

struct MissingRemoteImagePlaceholder: View {
    var body: some View {
        Color.black.opacity(0.12)
            .overlay {
                ProgressView()
                    .tint(ButteryTheme.butter)
            }
    }
}

actor ButteryRemoteImageCache {
    static let shared = ButteryRemoteImageCache()

    private let cache = NSCache<NSURL, UIImage>()
    private var inFlight: [NSURL: Task<UIImage?, Never>] = [:]

    private init() {
        cache.countLimit = 260
        cache.totalCostLimit = 160 * 1024 * 1024
    }

    func image(for url: URL) -> UIImage? {
        cache.object(forKey: url as NSURL)
    }

    func insert(_ image: UIImage, for url: URL) {
        cache.setObject(image, forKey: url as NSURL, cost: image.cacheCost)
    }

    func load(url: URL, maxPixelDimension: CGFloat) async -> UIImage? {
        let key = url as NSURL
        if let cached = cache.object(forKey: key) { return cached }
        if let existing = inFlight[key] { return await existing.value }

        if url.isFileURL {
            do {
                let data = try Data(contentsOf: url)
                let image = UIImage.butteryDownsampled(from: data, maxPixelDimension: maxPixelDimension) ?? UIImage(data: data)
                if let image {
                    cache.setObject(image, forKey: key, cost: image.cacheCost)
                }
                return image
            } catch {
                return nil
            }
        }

        if url.scheme?.lowercased() == "gs" {
            let task = Task { () -> UIImage? in
                do {
                    let data = try await Storage.storage()
                        .reference(forURL: url.absoluteString)
                        .data(maxSize: 25 * 1_024 * 1_024)
                    return UIImage.butteryDownsampled(from: data, maxPixelDimension: maxPixelDimension) ?? UIImage(data: data)
                } catch {
                    return nil
                }
            }
            inFlight[key] = task
            let image = await task.value
            inFlight[key] = nil
            if let image {
                cache.setObject(image, forKey: key, cost: image.cacheCost)
            }
            return image
        }

        let task = Task.detached(priority: .utility) { () -> UIImage? in
            var request = URLRequest(url: url)
            request.cachePolicy = .returnCacheDataElseLoad
            request.timeoutInterval = 30

            do {
                let (data, response) = try await URLSession.shared.data(for: request)
                if let http = response as? HTTPURLResponse, !(200..<300).contains(http.statusCode) {
                    return nil
                }
                return UIImage.butteryDownsampled(from: data, maxPixelDimension: maxPixelDimension)
                    ?? UIImage(data: data)
            } catch {
                return nil
            }
        }

        inFlight[key] = task
        let image = await task.value
        inFlight[key] = nil
        if let image {
            cache.setObject(image, forKey: key, cost: image.cacheCost)
        }
        return image
    }
}

private extension UIImage {
    var cacheCost: Int {
        guard let cgImage else { return 1 }
        return cgImage.bytesPerRow * cgImage.height
    }

    static func butteryDownsampled(from data: Data, maxPixelDimension: CGFloat) -> UIImage? {
        guard maxPixelDimension > 0,
              let source = CGImageSourceCreateWithData(data as CFData, nil) else {
            return nil
        }

        let options: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceShouldCacheImmediately: true,
            kCGImageSourceThumbnailMaxPixelSize: Int(maxPixelDimension)
        ]

        guard let cgImage = CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary) else {
            return nil
        }
        return UIImage(cgImage: cgImage)
    }
}
