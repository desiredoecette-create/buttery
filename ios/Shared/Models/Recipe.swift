import Foundation

enum RecipeVisibility: String, Codable, Hashable, CaseIterable {
    case `private`
    case `public`
    case shared
}

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
    var visibility: RecipeVisibility?
    var ownerUsername: String?
    var ownerDisplayName: String?
    var ownerProfilePhotoUrl: String?
    var videoUrls: [URL]?
    var thumbnailUrl: URL?
    var publicPublishedAt: Int64?
    var saveCount: Int?
    var likeCount: Int?
    var viewCount: Int?
    var ratingAverage: Double?
    var ratingCount: Int?
    var originalCreatorId: String?
    var originalCreatorUsername: String?
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
