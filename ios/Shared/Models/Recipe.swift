import Foundation

struct Recipe: Codable, Hashable, Identifiable {
    let id: String
    let ownerId: String
    var title: String
    var notes: String
    var prepTime: String
    var cookTime: String
    var totalTime: String
    var servings: String
    var ingredients: String
    var instructions: String
    var photoUrls: [URL]
    var videoUrl: URL?
    var sourceUrl: URL?
    var originalRawText: String
    var albumId: String?
    var isFavorite: Bool
    var createdAt: Int64
    var updatedAt: Int64
    var schemaVersion: Int = 1
}

struct RecipeAlbum: Codable, Hashable, Identifiable {
    let id: String
    let ownerId: String
    var name: String
    var customCoverImageUrl: URL?
    var createdAt: Int64
    var updatedAt: Int64
    var schemaVersion: Int = 1
}
