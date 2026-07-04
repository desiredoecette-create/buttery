import SwiftUI
import UIKit

struct RootView: View {
    @Environment(AppState.self) private var appState
    @Environment(AuthService.self) private var authService
    @Environment(SharingService.self) private var sharingService
    @Environment(RecipeStore.self) private var recipeStore
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        Group {
            if !appState.hasFinishedIntro {
                IntroView {
                    withAnimation(.easeInOut(duration: 0.35)) {
                        appState.hasFinishedIntro = true
                    }
                }
            } else if appState.isGuest || authService.route == .authenticated {
                dashboardExperience
            } else {
                AuthFlowView()
            }
        }
        .tint(ButteryTheme.butter)
        .onAppear(perform: updateIdleTimer)
        .onAppear {
            recipeStore.activateOwner(appState.isGuest ? nil : authService.currentUser?.uid)
        }
        .onChange(of: appState.hasFinishedIntro) { _, _ in updateIdleTimer() }
        .onChange(of: appState.isShowingAmbient) { _, _ in updateIdleTimer() }
        .onChange(of: appState.path) { _, _ in updateIdleTimer() }
        .onChange(of: authService.route) { _, route in
            if route == .authenticated {
                appState.isGuest = false
                appState.path.removeAll()
            }
            updateIdleTimer()
        }
        .onChange(of: authService.currentUser?.uid) { _, uid in
            sharingService.listen(for: uid)
            if !appState.isGuest {
                recipeStore.activateOwner(uid)
            }
        }
        .onChange(of: appState.isGuest) { _, isGuest in
            recipeStore.activateOwner(isGuest ? nil : authService.currentUser?.uid)
        }
        .onChange(of: scenePhase) { _, _ in updateIdleTimer() }
        .onDisappear {
            UIApplication.shared.isIdleTimerDisabled = false
        }
    }

    @ViewBuilder
    private var dashboardExperience: some View {
        if appState.isShowingAmbient {
            AmbientSlideshowView {
                withAnimation(.easeInOut(duration: 0.35)) {
                    appState.isShowingAmbient = false
                }
            }
            .transition(.opacity)
        } else {
            @Bindable var appState = appState
            NavigationStack(path: $appState.path) {
                HomeView()
                    .navigationDestination(for: AppDestination.self) { destination in
                        if destination == .recipes {
                            RecipeLibraryView()
                        } else if destination == .newRecipe {
                            NewRecipeView()
                        } else if destination == .continueRecipe {
                            ContinueRecipeView()
                        } else if destination == .favorites {
                            FavoritesView()
                        } else if destination == .grocery {
                            GroceryListView()
                        } else if destination == .settings {
                            ProfileSettingsView()
                        } else if destination == .inbox {
                            RecipeInboxView()
                        } else if destination == .privacySupport {
                            PrivacySupportView()
                        } else {
                            FeaturePlaceholderView(destination: destination)
                        }
                }
            }
        }
    }

    private func updateIdleTimer() {
        let dashboardExperienceIsVisible =
            appState.hasFinishedIntro &&
            (appState.isGuest || authService.route == .authenticated) &&
            (appState.isShowingAmbient || appState.path.isEmpty)
        UIApplication.shared.isIdleTimerDisabled =
            scenePhase == .active && dashboardExperienceIsVisible
    }
}
