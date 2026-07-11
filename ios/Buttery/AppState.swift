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

enum AppDestination: Hashable, Identifiable {
    case recipes
    case newRecipe
    case continueRecipe
    case favorites
    case grocery
    case explore
    case settings
    case inbox
    case privacySupport
    case myProfile
    case publicProfile(userId: String)

    var id: String {
        switch self {
        case .recipes: "recipes"
        case .newRecipe: "newRecipe"
        case .continueRecipe: "continueRecipe"
        case .favorites: "favorites"
        case .grocery: "grocery"
        case .explore: "explore"
        case .settings: "settings"
        case .inbox: "inbox"
        case .privacySupport: "privacySupport"
        case .myProfile: "myProfile"
        case .publicProfile(let userId): "publicProfile-\(userId)"
        }
    }

    var title: String {
        switch self {
        case .recipes: "Browse Recipes"
        case .newRecipe: "Enter New Recipe"
        case .continueRecipe: "Continue Recipe"
        case .favorites: "Favorites"
        case .grocery: "Grocery List"
        case .explore: "Explore"
        case .settings: "Settings"
        case .inbox: "Inbox"
        case .privacySupport: "Privacy & Support"
        case .myProfile: "My Profile"
        case .publicProfile: "Profile"
        }
    }
}
