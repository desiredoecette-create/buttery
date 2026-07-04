import AuthenticationServices
import SwiftUI

struct AuthFlowView: View {
    @Environment(AuthService.self) private var auth
    @Environment(AppState.self) private var appState
    @State private var screen: AuthScreen = .welcome

    var body: some View {
        ZStack {
            RadialGradient(
                colors: [Color(hex: 0x293A43), ButteryTheme.navy],
                center: .center,
                startRadius: 10,
                endRadius: 650
            )
            .ignoresSafeArea()

            switch auth.route {
            case .loading:
                ProgressView("Checking your account…")
                    .tint(ButteryTheme.butter)
                    .foregroundStyle(ButteryTheme.cream)
            case .needsUsername:
                CompleteProfileScreen()
            default:
                ScrollView {
                    VStack(spacing: 22) {
                        BundledImage(name: "buttery_wordmark", contentMode: .fit)
                            .frame(width: 260, height: 125)
                        Text("Save recipes. Share the good ones.")
                            .font(.system(size: 19, design: .serif))
                            .foregroundStyle(ButteryTheme.cream)

                        Group {
                            switch screen {
                            case .welcome:
                                WelcomeAuthCard(
                                    showLogin: { screen = .login },
                                    showSignup: { screen = .signup },
                                    continueAsGuest: {
                                        appState.isGuest = true
                                        appState.path.removeAll()
                                    }
                                )
                            case .login:
                                EmailLoginScreen { screen = .welcome }
                            case .signup:
                                EmailSignUpScreen { screen = .welcome }
                            }
                        }
                        .frame(maxWidth: 480)
                    }
                    .padding(.horizontal, 20)
                    .padding(.vertical, 32)
                    .frame(maxWidth: .infinity)
                }
            }
        }
        .preferredColorScheme(.dark)
    }
}

private struct WelcomeAuthCard: View {
    @Environment(AuthService.self) private var auth
    let showLogin: () -> Void
    let showSignup: () -> Void
    let continueAsGuest: () -> Void
    @State private var isWorking = false
    @State private var errorMessage: String?
    @State private var appleNonce: String?

    var body: some View {
        AuthCard {
            Text("Glad to see you!")
                .authTitle()
                .frame(maxWidth: .infinity, alignment: .center)

            if case .configurationMissing(let message) = auth.route {
                Label(message, systemImage: "exclamationmark.triangle.fill")
                    .font(.footnote)
                    .foregroundStyle(Color(hex: 0x9B493D))
                    .padding(12)
                    .background(Color(hex: 0x9B493D).opacity(0.1), in: RoundedRectangle(cornerRadius: 10))
            }

            Button {
                runGoogleSignIn()
            } label: {
                HStack(spacing: 10) {
                    Text("G")
                        .font(.title3.bold())
                        .foregroundStyle(
                            AngularGradient(
                                colors: [
                                    Color(hex: 0x4285F4),
                                    Color(hex: 0xEA4335),
                                    Color(hex: 0xFBBC05),
                                    Color(hex: 0x34A853),
                                    Color(hex: 0x4285F4)
                                ],
                                center: .center
                            )
                        )
                    Text("Continue with Google")
                }
                .frame(maxWidth: .infinity)
            }
            .buttonStyle(AuthOutlineButtonStyle())
            .disabled(isWorking)

            SignInWithAppleButton(.continue) { request in
                do {
                    appleNonce = try auth.prepareAppleSignIn(request)
                    errorMessage = nil
                } catch {
                    appleNonce = nil
                    errorMessage = error.localizedDescription
                }
            } onCompletion: { result in
                handleAppleSignIn(result)
            }
            .signInWithAppleButtonStyle(.black)
            .frame(height: 52)
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .disabled(isWorking)

            Button("Sign Up with Email", action: showSignup)
                .buttonStyle(AuthPrimaryButtonStyle())
            Button("Sign In", action: showLogin)
                .buttonStyle(AuthOutlineButtonStyle())
            Button("Continue as Guest", action: continueAsGuest)
                .font(.subheadline)
                .foregroundStyle(ButteryTheme.charcoal.opacity(0.7))
                .frame(maxWidth: .infinity, alignment: .center)

            if isWorking { ProgressView() }
            if let errorMessage { AuthErrorText(errorMessage) }
        }
    }

    private func runGoogleSignIn() {
        isWorking = true
        errorMessage = nil
        Task {
            do { try await auth.signInWithGoogle() }
            catch { errorMessage = error.localizedDescription }
            isWorking = false
        }
    }

    private func handleAppleSignIn(_ result: Result<ASAuthorization, Error>) {
        switch result {
        case .success(let authorization):
            guard let credential = authorization.credential as? ASAuthorizationAppleIDCredential,
                  let appleNonce else {
                errorMessage = AuthServiceError.appleUnavailable.localizedDescription
                return
            }
            isWorking = true
            errorMessage = nil
            Task {
                do {
                    try await auth.signInWithApple(credential: credential, nonce: appleNonce)
                } catch {
                    errorMessage = error.localizedDescription
                }
                self.appleNonce = nil
                isWorking = false
            }
        case .failure(let error):
            appleNonce = nil
            if let appleError = error as? ASAuthorizationError,
               appleError.code == .canceled {
                errorMessage = AuthServiceError.appleCanceled.localizedDescription
            } else {
                errorMessage = error.localizedDescription
            }
        }
    }
}

private struct EmailLoginScreen: View {
    @Environment(AuthService.self) private var auth
    let back: () -> Void
    @State private var email = ""
    @State private var password = ""
    @State private var isWorking = false
    @State private var errorMessage: String?

    var body: some View {
        AuthCard {
            Text("Welcome back").authTitle()
            AuthField("Email", text: $email, contentType: .emailAddress)
                .keyboardType(.emailAddress)
                .textInputAutocapitalization(.never)
            AuthSecureField("Password", text: $password, contentType: .password)
            Button("Sign In") {
                isWorking = true
                errorMessage = nil
                Task {
                    do { try await auth.signInWithEmail(email: email, password: password) }
                    catch { errorMessage = error.localizedDescription }
                    isWorking = false
                }
            }
            .buttonStyle(AuthPrimaryButtonStyle())
            .disabled(isWorking)
            if isWorking { ProgressView() }
            if let errorMessage { AuthErrorText(errorMessage) }
            Button("Back to all sign-in options", action: back)
                .foregroundStyle(ButteryTheme.charcoal.opacity(0.72))
        }
    }
}

private struct EmailSignUpScreen: View {
    @Environment(AuthService.self) private var auth
    let back: () -> Void
    @State private var username = ""
    @State private var displayName = ""
    @State private var email = ""
    @State private var password = ""
    @State private var confirmPassword = ""
    @State private var isWorking = false
    @State private var errorMessage: String?

    var body: some View {
        AuthCard {
            Text("Create your account").authTitle()
            Text("Sign up with your email address. You’ll use your email and password to log in.")
                .font(.subheadline)
                .foregroundStyle(ButteryTheme.charcoal.opacity(0.72))
            AuthField("Username", text: $username, contentType: .username)
                .textInputAutocapitalization(.never)
            Text("3–24 letters, numbers, or underscores. Your username must be unique.")
                .authFieldHelp()
            AuthField("Display name (optional)", text: $displayName, contentType: .name)
            Text("The name other Buttery users will see. If left blank, we’ll use your username.")
                .authFieldHelp()
            AuthField("Email", text: $email, contentType: .emailAddress)
                .keyboardType(.emailAddress)
                .textInputAutocapitalization(.never)
            AuthSecureField("Password", text: $password, contentType: .newPassword)
            Text("Use at least 6 characters.")
                .authFieldHelp()
            AuthSecureField("Confirm password", text: $confirmPassword, contentType: .newPassword)
            Button("Create Account") {
                guard password == confirmPassword else {
                    errorMessage = "Passwords do not match."
                    return
                }
                isWorking = true
                errorMessage = nil
                Task {
                    do {
                        try await auth.signUpWithEmail(
                            email: email,
                            password: password,
                            username: username,
                            displayName: displayName
                        )
                    } catch {
                        errorMessage = error.localizedDescription
                    }
                    isWorking = false
                }
            }
            .buttonStyle(AuthPrimaryButtonStyle())
            .disabled(isWorking)
            if isWorking { ProgressView() }
            if let errorMessage { AuthErrorText(errorMessage) }
            Button("Back to all sign-in options", action: back)
                .foregroundStyle(ButteryTheme.charcoal.opacity(0.72))
        }
    }
}

private struct CompleteProfileScreen: View {
    @Environment(AuthService.self) private var auth
    @State private var username = ""
    @State private var isWorking = false
    @State private var errorMessage: String?

    var body: some View {
        AuthCard {
            Text("Choose your username").authTitle()
            Text("Every Buttery account needs a unique username for future recipe sharing.")
                .foregroundStyle(ButteryTheme.charcoal.opacity(0.72))
            if let message = auth.lastError {
                AuthErrorText(message)
            }
            AuthField("Username", text: $username, contentType: .username)
                .textInputAutocapitalization(.never)
            Button("Finish Account Setup") {
                isWorking = true
                errorMessage = nil
                Task {
                    do { try await auth.completeFederatedProfile(username: username) }
                    catch { errorMessage = error.localizedDescription }
                    isWorking = false
                }
            }
            .buttonStyle(AuthPrimaryButtonStyle())
            .disabled(isWorking)
            if isWorking { ProgressView() }
            if let errorMessage { AuthErrorText(errorMessage) }
            Button("Use a different account") {
                try? auth.signOut()
            }
            .foregroundStyle(ButteryTheme.charcoal.opacity(0.72))
        }
        .padding(20)
    }
}

private enum AuthScreen { case welcome, login, signup }

private struct AuthCard<Content: View>: View {
    @ViewBuilder let content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 15) {
            content
        }
        .foregroundStyle(ButteryTheme.charcoal)
        .padding(24)
        .background(ButteryTheme.cream, in: RoundedRectangle(cornerRadius: 24))
        .shadow(color: .black.opacity(0.38), radius: 14, y: 8)
    }
}

private struct AuthField: View {
    let label: String
    @Binding var text: String
    let contentType: UITextContentType?

    init(_ label: String, text: Binding<String>, contentType: UITextContentType? = nil) {
        self.label = label
        _text = text
        self.contentType = contentType
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label)
                .font(.subheadline.weight(.semibold))
            TextField("", text: $text, prompt: Text(fieldPrompt))
                .textContentType(contentType)
                .padding(13)
                .background(.white.opacity(0.7), in: RoundedRectangle(cornerRadius: 11))
                .overlay(RoundedRectangle(cornerRadius: 11).stroke(ButteryTheme.paprika.opacity(0.3)))
        }
    }

    private var fieldPrompt: String {
        switch label {
        case "Display name (optional)": "Enter a display name"
        case "Email": "Enter your email address"
        default: "Enter your \(label.lowercased())"
        }
    }
}

private struct AuthSecureField: View {
    let label: String
    @Binding var text: String
    let contentType: UITextContentType?

    init(_ label: String, text: Binding<String>, contentType: UITextContentType? = nil) {
        self.label = label
        _text = text
        self.contentType = contentType
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label)
                .font(.subheadline.weight(.semibold))
            SecureField("", text: $text, prompt: Text("Enter your \(label.lowercased())"))
                .textContentType(contentType)
                .padding(13)
                .background(.white.opacity(0.7), in: RoundedRectangle(cornerRadius: 11))
                .overlay(RoundedRectangle(cornerRadius: 11).stroke(ButteryTheme.paprika.opacity(0.3)))
        }
    }
}

private extension View {
    func authFieldHelp() -> some View {
        font(.caption)
            .foregroundStyle(ButteryTheme.charcoal.opacity(0.62))
            .padding(.top, -8)
    }
}

private struct AuthErrorText: View {
    let message: String
    init(_ message: String) { self.message = message }
    var body: some View {
        Text(message)
            .font(.footnote)
            .foregroundStyle(Color(hex: 0x9B493D))
    }
}

private struct AuthPrimaryButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .fontWeight(.semibold)
            .foregroundStyle(ButteryTheme.navy)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .background(ButteryTheme.butter.opacity(configuration.isPressed ? 0.7 : 1), in: RoundedRectangle(cornerRadius: 12))
    }
}

private struct AuthOutlineButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .fontWeight(.semibold)
            .foregroundStyle(ButteryTheme.navy)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .padding(.horizontal, 14)
            .background(.white.opacity(configuration.isPressed ? 0.35 : 0.65), in: RoundedRectangle(cornerRadius: 12))
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(ButteryTheme.navy.opacity(0.2)))
    }
}

private extension Text {
    func authTitle() -> some View {
        font(.system(size: 29, design: .serif))
            .foregroundStyle(ButteryTheme.charcoal)
    }
}
