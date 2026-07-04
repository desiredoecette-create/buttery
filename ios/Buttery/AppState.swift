import Foundation
import Observation

@MainActor
@Observable
final class AppState {
    var hasFinishedIntro = false
    var isShowingAmbient = false
    var isGuest = false
    var path: [AppDestination] = []
}

enum AppDestination: String, Hashable, Identifiable {
    case recipes
    case newRecipe
    case continueRecipe
    case favorites
    case grocery
    case settings
    case inbox
    case privacySupport

    var id: String { rawValue }

    var title: String {
        switch self {
        case .recipes: "Browse Recipes"
        case .newRecipe: "Enter New Recipe"
        case .continueRecipe: "Continue Recipe"
        case .favorites: "Favorites"
        case .grocery: "Grocery List"
        case .settings: "Settings"
        case .inbox: "Inbox"
        case .privacySupport: "Privacy & Support"
        }
    }
}
