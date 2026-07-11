@preconcurrency import FirebaseAuth
@preconcurrency import FirebaseCore
@preconcurrency import FirebaseFirestore
@preconcurrency import FirebaseStorage
import AuthenticationServices
import CryptoKit
import Foundation
@preconcurrency import GoogleSignIn
import Observation
import Security
import UIKit

struct ButteryUserProfile: Equatable {
    let uid: String
    let username: String
    let usernameLowercase: String
    let email: String
    let displayName: String
    let profilePhotoUrl: String?
    let provider: String
}

enum AuthRoute: Equatable {
    case loading
    case configurationMissing(String)
    case signedOut
    case needsUsername
    case authenticated
}

@MainActor
@Observable
final class AuthService {
    private(set) var route: AuthRoute = .loading
    private(set) var currentUser: User?
    private(set) var profile: ButteryUserProfile?
    private(set) var configurationProjectID: String?
    var lastError: String?

    private var listener: AuthStateDidChangeListenerHandle?
    private var database: Firestore?

    init() {
        configureFirebase()
    }

    func signUpWithEmail(
        email: String,
        password: String,
        username: String,
        displayName: String? = nil
    ) async throws {
        let fields = try validatedSignup(email: email, password: password, username: username)
        guard FirebaseApp.app() != nil else { throw AuthServiceError.configurationMissing }
        do {
            let result = try await Auth.auth().createUser(
                withEmail: fields.email,
                password: password
            )
            do {
                try await reserveUsernameAndCreateProfile(
                    user: result.user,
                    username: fields.username,
                    displayName: displayName?
                        .trimmingCharacters(in: .whitespacesAndNewlines)
                        .nilIfEmpty ?? fields.username,
                    provider: "password"
                )
                await loadProfile(for: result.user)
            } catch {
                try? await result.user.delete()
                throw error
            }
        } catch {
            throw friendly(error)
        }
    }

    func signInWithEmail(email: String, password: String) async throws {
        guard FirebaseApp.app() != nil else { throw AuthServiceError.configurationMissing }
        guard !email.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            throw AuthServiceError.invalidEmail
        }
        guard !password.isEmpty else { throw AuthServiceError.passwordRequired }
        do {
            _ = try await Auth.auth().signIn(
                withEmail: email.trimmingCharacters(in: .whitespacesAndNewlines),
                password: password
            )
        } catch {
            throw friendly(error)
        }
    }

    func signInWithGoogle() async throws {
        guard FirebaseApp.app() != nil,
              let clientID = FirebaseApp.app()?.options.clientID else {
            throw AuthServiceError.configurationMissing
        }
        guard let presenter = Self.presentingViewController() else {
            throw AuthServiceError.googleUnavailable
        }
        GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientID)
        do {
            lastError = nil
            let result = try await GIDSignIn.sharedInstance.signIn(withPresenting: presenter)
            guard let idToken = result.user.idToken?.tokenString else {
                throw AuthServiceError.googleUnavailable
            }
            let credential = GoogleAuthProvider.credential(
                withIDToken: idToken,
                accessToken: result.user.accessToken.tokenString
            )
            _ = try await Auth.auth().signIn(with: credential)
        } catch let error as GIDSignInError where error.code == .canceled {
            throw AuthServiceError.googleCanceled
        } catch {
            throw friendly(error)
        }
    }

    func prepareAppleSignIn(_ request: ASAuthorizationAppleIDRequest) throws -> String {
        let nonce = try Self.randomNonce()
        request.requestedScopes = [.fullName, .email]
        request.nonce = Self.sha256(nonce)
        return nonce
    }

    func signInWithApple(
        credential appleCredential: ASAuthorizationAppleIDCredential,
        nonce: String
    ) async throws {
        guard FirebaseApp.app() != nil else { throw AuthServiceError.configurationMissing }
        guard let tokenData = appleCredential.identityToken,
              let idToken = String(data: tokenData, encoding: .utf8) else {
            throw AuthServiceError.appleUnavailable
        }
        do {
            lastError = nil
            let credential = OAuthProvider.appleCredential(
                withIDToken: idToken,
                rawNonce: nonce,
                fullName: appleCredential.fullName
            )
            _ = try await Auth.auth().signIn(with: credential)
        } catch {
            throw friendly(error)
        }
    }

    func completeFederatedProfile(username: String) async throws {
        guard let user = Auth.auth().currentUser else { throw AuthServiceError.sessionExpired }
        let cleanUsername = try validateUsername(username)
        try await reserveUsernameAndCreateProfile(
            user: user,
            username: cleanUsername,
            displayName: user.displayName?.trimmingCharacters(in: .whitespacesAndNewlines)
                .nilIfEmpty ?? cleanUsername,
            provider: user.providerData.first?.providerID ?? "password"
        )
        await loadProfile(for: user)
    }

    func signOut() throws {
        do {
            GIDSignIn.sharedInstance.signOut()
            try Auth.auth().signOut()
            profile = nil
            currentUser = nil
            route = .signedOut
        } catch {
            throw friendly(error)
        }
    }

    func updateProfile(displayName: String) async throws {
        guard let user = currentUser, let database else {
            throw AuthServiceError.sessionExpired
        }
        let cleanName = displayName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanName.isEmpty else {
            throw AuthServiceError.other("Display name is required.")
        }
        do {
            try await database.collection("users").document(user.uid).updateData([
                "displayName": cleanName,
                "updatedAt": FieldValue.serverTimestamp()
            ])
            if let existing = profile {
                profile = ButteryUserProfile(
                    uid: existing.uid,
                    username: existing.username,
                    usernameLowercase: existing.usernameLowercase,
                    email: existing.email,
                    displayName: cleanName,
                    profilePhotoUrl: existing.profilePhotoUrl,
                    provider: existing.provider
                )
            }
        } catch {
            throw friendly(error)
        }
    }

    func updateProfilePhoto(data: Data) async throws {
        guard let user = currentUser else { throw AuthServiceError.sessionExpired }
        let uploadData = try preparedProfilePhotoData(from: data)
        guard uploadData.count < 5 * 1_024 * 1_024 else {
            throw AuthServiceError.other("Profile picture must be smaller than 5 MB.")
        }
        let reference = Storage.storage().reference()
            .child("users/\(user.uid)/profile/avatar_\(Int(Date().timeIntervalSince1970)).jpg")
        let metadata = StorageMetadata()
        metadata.contentType = "image/jpeg"
        do {
            _ = try await reference.putDataAsync(uploadData, metadata: metadata)
            let url = try await reference.downloadURL()
            try await database?.collection("users").document(user.uid).updateData([
                "profilePhotoUrl": url.absoluteString,
                "updatedAt": FieldValue.serverTimestamp()
            ])
            if let existing = profile {
                profile = ButteryUserProfile(
                    uid: existing.uid,
                    username: existing.username,
                    usernameLowercase: existing.usernameLowercase,
                    email: existing.email,
                    displayName: existing.displayName,
                    profilePhotoUrl: url.absoluteString,
                    provider: existing.provider
                )
                try? await database?.collection("publicProfiles").document(existing.uid).setData([
                    "uid": existing.uid,
                    "username": existing.username,
                    "displayName": existing.displayName,
                    "profilePhotoUrl": url.absoluteString,
                    "updatedAt": FieldValue.serverTimestamp(),
                    "schemaVersion": 1
                ], merge: true)
            }
        } catch {
            throw friendly(error)
        }
    }

    private func preparedProfilePhotoData(from data: Data) throws -> Data {
        guard let image = UIImage(data: data) else {
            throw AuthServiceError.other("That picture could not be opened. Please choose another.")
        }
        let maxSide: CGFloat = 900
        let longestSide = max(image.size.width, image.size.height)
        let scale = longestSide > maxSide ? maxSide / longestSide : 1
        let targetSize = CGSize(width: image.size.width * scale, height: image.size.height * scale)
        let renderer = UIGraphicsImageRenderer(size: targetSize)
        let resized = renderer.image { _ in
            image.draw(in: CGRect(origin: .zero, size: targetSize))
        }
        guard let jpeg = resized.jpegData(compressionQuality: 0.78) else {
            throw AuthServiceError.other("That picture could not be prepared for upload.")
        }
        return jpeg
    }

    func deleteAccount() async throws {
        guard let user = Auth.auth().currentUser, let database else {
            throw AuthServiceError.sessionExpired
        }
        if let lastSignIn = user.metadata.lastSignInDate,
           Date().timeIntervalSince(lastSignIn) > 300 {
            throw AuthServiceError.reauthenticationRequired
        }

        do {
            let username = profile?.usernameLowercase
            let sentShares = try await database.collection("recipeShares")
                .whereField("fromUserId", isEqualTo: user.uid)
                .getDocuments()
            let receivedShares = try await database.collection("recipeShares")
                .whereField("toUserId", isEqualTo: user.uid)
                .getDocuments()

            // Firebase Storage's client folder-list endpoint can return an opaque backend 400
            // for otherwise valid owner folders. Delete every owned object referenced by the
            // profile and sent shares directly instead.
            var ownedMediaURLs = Set<String>()
            if let profilePhotoUrl = profile?.profilePhotoUrl {
                ownedMediaURLs.insert(profilePhotoUrl)
            }
            for document in sentShares.documents {
                guard let snapshot = document.data()["recipeSnapshot"] as? [String: Any] else {
                    continue
                }
                for url in snapshot["photoUrls"] as? [String] ?? [] {
                    ownedMediaURLs.insert(url)
                }
                if let url = snapshot["videoUrl"] as? String {
                    ownedMediaURLs.insert(url)
                }
            }
            for url in ownedMediaURLs {
                try await deleteStorageObjectIfPresent(url)
            }

            let batch = database.batch()
            var shareReferences: [String: DocumentReference] = [:]
            for document in sentShares.documents + receivedShares.documents {
                shareReferences[document.documentID] = document.reference
            }
            for reference in shareReferences.values {
                batch.deleteDocument(reference)
            }
            if let username, !username.isEmpty {
                batch.deleteDocument(database.collection("usernames").document(username))
            }
            batch.deleteDocument(database.collection("users").document(user.uid))
            try await batch.commit()

            try await user.delete()

            GIDSignIn.sharedInstance.signOut()
            profile = nil
            currentUser = nil
            route = .signedOut
        } catch {
            throw friendly(error)
        }
    }

    private func configureFirebase() {
        guard let plistURL = Bundle.main.url(
            forResource: "GoogleService-Info",
            withExtension: "plist"
        ) else {
            route = .configurationMissing(
                "Firebase is not configured yet. Add GoogleService-Info.plist to the Buttery app target."
            )
            return
        }
        guard let options = FirebaseOptions(contentsOfFile: plistURL.path) else {
            route = .configurationMissing("GoogleService-Info.plist could not be read.")
            return
        }
        FirebaseApp.configure(options: options)
        configurationProjectID = options.projectID
        database = Firestore.firestore()
        listenForAuthChanges()
    }

    private func listenForAuthChanges() {
        listener = Auth.auth().addStateDidChangeListener { [weak self] _, user in
            Task { @MainActor [weak self] in
                guard let self else { return }
                self.currentUser = user
                if let user {
                    await self.loadProfile(for: user)
                } else {
                    self.profile = nil
                    self.route = .signedOut
                }
            }
        }
    }

    private func loadProfile(for user: User) async {
        guard let database else {
            route = .configurationMissing("Firestore is unavailable.")
            return
        }
        route = .loading
        lastError = nil
        do {
            let document = try await database.collection("users").document(user.uid).getDocument()
            guard document.exists, let data = document.data(),
                  let username = data["username"] as? String,
                  !username.isEmpty else {
                profile = nil
                route = .needsUsername
                return
            }
            profile = ButteryUserProfile(
                uid: user.uid,
                username: username,
                usernameLowercase: data["usernameLowercase"] as? String ?? username.lowercased(),
                email: data["email"] as? String ?? user.email ?? "",
                displayName: data["displayName"] as? String ?? user.displayName ?? username,
                profilePhotoUrl: data["profilePhotoUrl"] as? String,
                provider: data["provider"] as? String ?? user.providerData.first?.providerID ?? "password"
            )
            route = .authenticated
        } catch {
            lastError = friendly(error).localizedDescription
            // Firebase Auth succeeded, so never present this user as signed out merely because
            // their Firestore profile could not be loaded. New/existing federated users can
            // retry profile creation here, and the underlying Firestore error stays visible.
            profile = nil
            route = .needsUsername
        }
    }

    private func reserveUsernameAndCreateProfile(
        user: User,
        username: String,
        displayName: String,
        provider: String
    ) async throws {
        guard let database else { throw AuthServiceError.configurationMissing }
        let lower = username.lowercased()
        let usernameRef = database.collection("usernames").document(lower)
        let userRef = database.collection("users").document(user.uid)
        let now = FieldValue.serverTimestamp()
        let userData: [String: Any] = [
            "uid": user.uid,
            "userId": user.uid,
            "username": username,
            "usernameLowercase": lower,
            "email": user.email ?? "",
            "displayName": displayName,
            "profilePhotoUrl": user.photoURL?.absoluteString ?? NSNull(),
            "provider": provider,
            "createdAt": now,
            "updatedAt": now,
            "schemaVersion": 1
        ]
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            database.runTransaction({ transaction, errorPointer -> Any? in
                do {
                    let existing = try transaction.getDocument(usernameRef)
                    if existing.exists {
                        errorPointer?.pointee = NSError(
                            domain: "ButteryAuth",
                            code: 409,
                            userInfo: [NSLocalizedDescriptionKey: "That username is already taken."]
                        )
                        return nil
                    }
                    transaction.setData(
                        ["uid": user.uid, "userId": user.uid],
                        forDocument: usernameRef
                    )
                    transaction.setData(userData, forDocument: userRef)
                    return nil
                } catch {
                    errorPointer?.pointee = error as NSError
                    return nil
                }
            }) { _, error in
                if let error { continuation.resume(throwing: self.friendly(error)) }
                else { continuation.resume() }
            }
        }
    }

    private func validatedSignup(
        email: String,
        password: String,
        username: String
    ) throws -> (email: String, username: String) {
        let cleanEmail = email.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !cleanEmail.isEmpty, cleanEmail.contains("@") else {
            throw AuthServiceError.invalidEmail
        }
        guard !password.isEmpty else { throw AuthServiceError.passwordRequired }
        guard password.count >= 6 else { throw AuthServiceError.weakPassword }
        return (cleanEmail, try validateUsername(username))
    }

    private func validateUsername(_ username: String) throws -> String {
        let clean = username.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty else { throw AuthServiceError.usernameRequired }
        guard clean.range(
            of: #"^[A-Za-z0-9_]{3,24}$"#,
            options: .regularExpression
        ) != nil else { throw AuthServiceError.invalidUsername }
        return clean
    }

    private func friendly(_ error: Error) -> Error {
        if let error = error as? AuthServiceError { return error }
        let nsError = error as NSError
        if nsError.domain == "ButteryAuth", nsError.code == 409 {
            return AuthServiceError.usernameTaken
        }
        if nsError.domain == FirestoreErrorDomain,
           nsError.localizedDescription.localizedCaseInsensitiveContains("username") {
            return AuthServiceError.usernameTaken
        }
        guard let code = AuthErrorCode(rawValue: nsError.code) else {
            return nsError.code == NSURLErrorNotConnectedToInternet
                ? AuthServiceError.network
                : AuthServiceError.other(nsError.localizedDescription)
        }
        switch code {
        case .invalidEmail: return AuthServiceError.invalidEmail
        case .weakPassword: return AuthServiceError.weakPassword
        case .emailAlreadyInUse: return AuthServiceError.emailAlreadyExists
        case .wrongPassword, .invalidCredential, .userNotFound:
            return AuthServiceError.incorrectCredentials
        case .networkError: return AuthServiceError.network
        default: return AuthServiceError.other(nsError.localizedDescription)
        }
    }

    private static func presentingViewController(
        from base: UIViewController? = UIApplication.shared.connectedScenes
            .compactMap { ($0 as? UIWindowScene)?.keyWindow }
            .first?.rootViewController
    ) -> UIViewController? {
        if let navigation = base as? UINavigationController {
            return presentingViewController(from: navigation.visibleViewController)
        }
        if let tab = base as? UITabBarController {
            return presentingViewController(from: tab.selectedViewController)
        }
        if let presented = base?.presentedViewController {
            return presentingViewController(from: presented)
        }
        return base
    }

    private static func randomNonce(length: Int = 32) throws -> String {
        precondition(length > 0)
        let characters = Array("0123456789ABCDEFGHIJKLMNOPQRSTUVXYZabcdefghijklmnopqrstuvwxyz-._")
        var result = ""
        var remaining = length
        while remaining > 0 {
            var bytes = [UInt8](repeating: 0, count: 16)
            let status = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
            guard status == errSecSuccess else {
                throw AuthServiceError.appleUnavailable
            }
            for byte in bytes where remaining > 0 {
                guard byte < characters.count else { continue }
                result.append(characters[Int(byte)])
                remaining -= 1
            }
        }
        return result
    }

    private static func sha256(_ input: String) -> String {
        SHA256.hash(data: Data(input.utf8))
            .map { String(format: "%02x", $0) }
            .joined()
    }

    private func deleteStorageObjectIfPresent(_ url: String) async throws {
        do {
            try await Storage.storage().reference(forURL: url).delete()
        } catch {
            let nsError = error as NSError
            guard nsError.code == StorageErrorCode.objectNotFound.rawValue else {
                throw error
            }
        }
    }
}

enum AuthServiceError: LocalizedError {
    case configurationMissing, usernameRequired, invalidUsername, usernameTaken
    case invalidEmail, passwordRequired, weakPassword, emailAlreadyExists
    case incorrectCredentials, network, googleCanceled, googleUnavailable
    case appleCanceled, appleUnavailable, sessionExpired, reauthenticationRequired
    case other(String)

    var errorDescription: String? {
        switch self {
        case .configurationMissing:
            "Firebase setup is missing. Add GoogleService-Info.plist to the Buttery app target."
        case .usernameRequired: "Username is required."
        case .invalidUsername: "Username must be 3–24 letters, numbers, or underscores."
        case .usernameTaken: "That username is already taken."
        case .invalidEmail: "Enter a valid email address."
        case .passwordRequired: "Password is required."
        case .weakPassword: "Password must be at least 6 characters."
        case .emailAlreadyExists: "An account already exists for that email."
        case .incorrectCredentials: "Incorrect email or password."
        case .network: "The network is unavailable. Check your connection and try again."
        case .googleCanceled: "Google Sign-In was canceled."
        case .googleUnavailable: "Google Sign-In could not be started."
        case .appleCanceled: "Sign in with Apple was canceled."
        case .appleUnavailable: "Sign in with Apple could not be completed."
        case .sessionExpired: "Your sign-in session expired. Please sign in again."
        case .reauthenticationRequired:
            "For your security, sign out and sign back in before deleting your account."
        case .other(let message): message
        }
    }
}

private extension String {
    var nilIfEmpty: String? { isEmpty ? nil : self }
}
