import SwiftUI
import GoogleSignIn

@main
struct ButteryApp: App {
    @State private var appState = AppState()
    @State private var recipeStore = RecipeStore()
    @State private var cookingSessionStore = CookingSessionStore()
    @State private var groceryListStore = GroceryListStore()
    @State private var authService = AuthService()
    @State private var sharingService = SharingService()
    @State private var socialProfileService = SocialProfileService()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(appState)
                .environment(recipeStore)
                .environment(cookingSessionStore)
                .environment(groceryListStore)
                .environment(authService)
                .environment(sharingService)
                .environment(socialProfileService)
                .preferredColorScheme(.dark)
                .onOpenURL { url in
                    GIDSignIn.sharedInstance.handle(url)
                }
        }
    }
}
