import Foundation
import Observation

struct CookingSession: Codable, Equatable {
    let recipeId: String
    var lastOpenedTimestamp: Int64
    var scrollPosition: Int = 0
    var currentStep: Int?
}

@MainActor
@Observable
final class CookingSessionStore {
    private(set) var session: CookingSession?
    private let defaults: UserDefaults
    private let key = "buttery.cookingSession"

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        if let data = defaults.data(forKey: key) {
            session = try? JSONDecoder().decode(CookingSession.self, from: data)
        }
    }

    func recordRecipeOpened(_ recipeId: String) {
        let previous = session?.recipeId == recipeId ? session : nil
        session = CookingSession(
            recipeId: recipeId,
            lastOpenedTimestamp: Date.now.millisecondsSince1970,
            scrollPosition: previous?.scrollPosition ?? 0,
            currentStep: previous?.currentStep
        )
        persist()
    }

    func clear() {
        session = nil
        defaults.removeObject(forKey: key)
    }

    private func persist() {
        guard let session, let data = try? JSONEncoder().encode(session) else { return }
        defaults.set(data, forKey: key)
    }
}

@MainActor
@Observable
final class RecipeStore {
    private(set) var recipes: [Recipe] = []
    private(set) var albums: [RecipeAlbum] = []

    private var ownerId = "unassigned"
    private var fileURL: URL
    private let rootDirectory: URL
    private let mediaDirectory: URL

    init() {
        let root = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("Buttery", isDirectory: true)
        try? FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        rootDirectory = root
        mediaDirectory = root.appendingPathComponent("Media", isDirectory: true)
        try? FileManager.default.createDirectory(at: mediaDirectory, withIntermediateDirectories: true)
        fileURL = root.appendingPathComponent("recipes.json")
        // recipes.json is the pre-account, device-wide archive. It is intentionally left intact
        // rather than assigned to whichever account happens to launch the upgraded app first.
    }

    func activateOwner(_ userId: String?) {
        let newOwnerId = userId ?? "guest"
        guard newOwnerId != ownerId else { return }
        ownerId = newOwnerId
        let safeId = newOwnerId.replacingOccurrences(
            of: #"[^A-Za-z0-9_-]"#,
            with: "_",
            options: .regularExpression
        )
        fileURL = rootDirectory.appendingPathComponent("recipes_\(safeId).json")
        load()
    }

    func deleteCurrentOwnerData() {
        recipes = []
        albums = []
        try? FileManager.default.removeItem(at: fileURL)
    }

    func createAlbum(name: String, cover: URL? = nil) -> String {
        let now = Date.now.millisecondsSince1970
        let album = RecipeAlbum(
            id: UUID().uuidString,
            ownerId: ownerId,
            name: String(name.trimmingCharacters(in: .whitespacesAndNewlines).prefix(40)),
            customCoverImageUrl: cover,
            createdAt: now,
            updatedAt: now
        )
        albums.append(album)
        persist()
        return album.id
    }

    func updateAlbum(id: String, name: String, cover: URL?) {
        guard let index = albums.firstIndex(where: { $0.id == id }) else { return }
        albums[index].name = String(name.trimmingCharacters(in: .whitespacesAndNewlines).prefix(40))
        albums[index].customCoverImageUrl = cover
        albums[index].updatedAt = Date.now.millisecondsSince1970
        persist()
    }

    func deleteAlbum(id: String) {
        albums.removeAll { $0.id == id }
        for index in recipes.indices where recipes[index].albumId == id {
            recipes[index].albumId = nil
            recipes[index].updatedAt = Date.now.millisecondsSince1970
        }
        persist()
    }

    func saveRecipe(_ draft: RecipeDraft, id: String? = nil) -> String {
        let draft = sanitizedDraft(draft)
        let now = Date.now.millisecondsSince1970
        if let id, let index = recipes.firstIndex(where: { $0.id == id }) {
            recipes[index].title = String(draft.title.trimmed.prefix(60))
            recipes[index].notes = draft.notes.trimmed
            recipes[index].prepTime = draft.prepTime.trimmed
            recipes[index].cookTime = draft.cookTime.trimmed
            recipes[index].totalTime = draft.totalTime.trimmed
            recipes[index].servings = draft.servings.trimmed
            recipes[index].ingredients = draft.ingredients.trimmed
            recipes[index].instructions = draft.instructions.trimmed
            recipes[index].photoUrls = draft.photoUrls
            recipes[index].videoUrl = draft.videoUrl
            recipes[index].albumId = draft.albumId
            recipes[index].sourceUrl = draft.sourceUrl
            recipes[index].originalRawText = draft.originalRawText
            recipes[index].visibility = draft.visibility
            recipes[index].originalCreatorId = draft.originalCreatorId
            recipes[index].originalCreatorUsername = draft.originalCreatorUsername
            recipes[index].updatedAt = now
            persist()
            return id
        }

        let newId = UUID().uuidString
        recipes.insert(
            Recipe(
                id: newId,
                ownerId: ownerId,
                title: String(draft.title.trimmed.prefix(60)),
                notes: draft.notes.trimmed,
                prepTime: draft.prepTime.trimmed,
                cookTime: draft.cookTime.trimmed,
                totalTime: draft.totalTime.trimmed,
                servings: draft.servings.trimmed,
                ingredients: draft.ingredients.trimmed,
                instructions: draft.instructions.trimmed,
                photoUrls: draft.photoUrls,
                videoUrl: draft.videoUrl,
                sourceUrl: draft.sourceUrl,
                originalRawText: draft.originalRawText,
                albumId: draft.albumId,
                isFavorite: false,
                createdAt: now,
                updatedAt: now,
                visibility: draft.visibility,
                likeCount: 0,
                originalCreatorId: draft.originalCreatorId,
                originalCreatorUsername: draft.originalCreatorUsername
            ),
            at: 0
        )
        persist()
        return newId
    }

    func deleteRecipe(id: String) {
        recipes.removeAll { $0.id == id }
        persist()
    }

    func toggleFavorite(id: String) {
        guard let index = recipes.firstIndex(where: { $0.id == id }) else { return }
        recipes[index].isFavorite.toggle()
        recipes[index].updatedAt = Date.now.millisecondsSince1970
        persist()
    }

    func updateVisibility(id: String, visibility: RecipeVisibility) {
        guard let index = recipes.firstIndex(where: { $0.id == id }) else { return }
        recipes[index].visibility = visibility
        recipes[index].updatedAt = Date.now.millisecondsSince1970
        if visibility == .public, recipes[index].publicPublishedAt == nil {
            recipes[index].publicPublishedAt = Date.now.millisecondsSince1970
        }
        persist()
    }

    func savePublicCopy(
        _ recipe: Recipe,
        albumId: String?,
        creatorUsername: String?
    ) -> String {
        var draft = RecipeDraft(recipe: recipe)
        draft.albumId = albumId
        draft.visibility = .private
        draft.originalCreatorId = recipe.ownerId
        draft.originalCreatorUsername = creatorUsername ?? recipe.ownerUsername
        return saveRecipe(draft)
    }

    func addSharedRecipe(_ shared: SharedRecipeSnapshot) -> String {
        var draft = RecipeDraft()
        draft.title = shared.title
        draft.notes = shared.notes
        draft.prepTime = shared.prepTime
        draft.cookTime = shared.cookTime
        draft.totalTime = shared.totalTime
        draft.servings = shared.servings
        draft.ingredients = shared.ingredients
        draft.instructions = shared.instructions
        draft.photoUrls = shared.photoUrls.compactMap(URL.init(string:))
        draft.videoUrl = shared.videoUrl.flatMap(URL.init(string:))
        draft.sourceUrl = shared.sourceUrl.flatMap(URL.init(string:))
        draft.originalRawText = shared.originalRawText
        return saveRecipe(draft)
    }

    func storePhoto(data: Data) throws -> URL {
        let url = mediaDirectory.appendingPathComponent("\(UUID().uuidString).jpg")
        try data.write(to: url, options: .atomic)
        return url
    }

    func storeVideo(data: Data) throws -> URL {
        guard data.count <= 50 * 1_024 * 1_024 else {
            throw MediaError.videoTooLarge
        }
        let url = mediaDirectory.appendingPathComponent("\(UUID().uuidString).mp4")
        try data.write(to: url, options: .atomic)
        return url
    }

    private func load() {
        guard let data = try? Data(contentsOf: fileURL),
              let state = try? JSONDecoder().decode(PersistedState.self, from: data) else {
            recipes = []
            albums = []
            return
        }
        recipes = state.recipes.map { stored in
            var recipe = stored
            recipe.photoUrls = stored.photoUrls.map(resolveMediaURL)
            recipe.videoUrl = stored.videoUrl.map(resolveMediaURL)
            sanitizeRecipe(&recipe)
            return recipe
        }
        albums = state.albums.map { stored in
            var album = stored
            album.customCoverImageUrl = stored.customCoverImageUrl.map(resolveMediaURL)
            return album
        }
        // Persist repaired sandbox paths so subsequent launches do not repeat the migration.
        persist()
    }

    private func resolveMediaURL(_ storedURL: URL) -> URL {
        guard storedURL.isFileURL,
              !FileManager.default.fileExists(atPath: storedURL.path) else {
            return storedURL
        }
        let repaired = mediaDirectory.appendingPathComponent(storedURL.lastPathComponent)
        return FileManager.default.fileExists(atPath: repaired.path) ? repaired : storedURL
    }

    private func persist() {
        guard let data = try? JSONEncoder().encode(PersistedState(recipes: recipes, albums: albums))
        else { return }
        try? data.write(to: fileURL, options: .atomic)
    }

    private func sanitizedDraft(_ draft: RecipeDraft) -> RecipeDraft {
        var clean = draft
        clean.title = RecipeImporter.cleanImportedRecipeField(clean.title)
        clean.notes = RecipeImporter.cleanImportedRecipeField(clean.notes)
        clean.ingredients = RecipeImporter.cleanImportedRecipeField(clean.ingredients)
        clean.instructions = RecipeImporter.cleanImportedRecipeField(clean.instructions)
        clean.originalRawText = RecipeImporter.cleanImportedRecipeField(clean.originalRawText)
        return clean
    }

    private func sanitizeRecipe(_ recipe: inout Recipe) {
        recipe.title = RecipeImporter.cleanImportedRecipeField(recipe.title)
        recipe.notes = RecipeImporter.cleanImportedRecipeField(recipe.notes)
        recipe.ingredients = RecipeImporter.cleanImportedRecipeField(recipe.ingredients)
        recipe.instructions = RecipeImporter.cleanImportedRecipeField(recipe.instructions)
        recipe.originalRawText = RecipeImporter.cleanImportedRecipeField(recipe.originalRawText)
    }
}

struct RecipeDraft {
    var title = ""
    var notes = ""
    var prepTime = ""
    var cookTime = ""
    var totalTime = ""
    var servings = ""
    var ingredients = ""
    var instructions = ""
    var photoUrls: [URL] = []
    var videoUrl: URL?
    var albumId: String?
    var sourceUrl: URL?
    var originalRawText = ""
    var visibility: RecipeVisibility? = .private
    var originalCreatorId: String?
    var originalCreatorUsername: String?

    init() {}

    init(recipe: Recipe) {
        title = recipe.title
        notes = recipe.notes
        prepTime = recipe.prepTime
        cookTime = recipe.cookTime
        totalTime = recipe.totalTime
        servings = recipe.servings
        ingredients = recipe.ingredients
        instructions = recipe.instructions
        photoUrls = recipe.photoUrls
        videoUrl = recipe.videoUrl
        albumId = recipe.albumId
        sourceUrl = recipe.sourceUrl
        originalRawText = recipe.originalRawText
        visibility = recipe.visibility ?? .private
        originalCreatorId = recipe.originalCreatorId
        originalCreatorUsername = recipe.originalCreatorUsername
    }
}

enum MediaError: LocalizedError {
    case videoTooLarge
    var errorDescription: String? { "Video must be 50 MB or smaller." }
}

private struct PersistedState: Codable {
    let recipes: [Recipe]
    let albums: [RecipeAlbum]
}

private extension String {
    var trimmed: String { trimmingCharacters(in: .whitespacesAndNewlines) }
}

private extension Date {
    var millisecondsSince1970: Int64 { Int64(timeIntervalSince1970 * 1_000) }
}
