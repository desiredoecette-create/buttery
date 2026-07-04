import Foundation
import UIKit
import Vision

enum RecipeImporter {
    static func recognizeText(in data: Data) async throws -> String {
        guard let image = UIImage(data: data)?.cgImage else { throw ImportError.invalidImage }
        return try await withCheckedThrowingContinuation { continuation in
            let request = VNRecognizeTextRequest { request, error in
                if let error { continuation.resume(throwing: error); return }
                let text = (request.results as? [VNRecognizedTextObservation] ?? [])
                    .compactMap { $0.topCandidates(1).first?.string }
                    .joined(separator: "\n")
                continuation.resume(returning: text)
            }
            request.recognitionLevel = .accurate
            request.usesLanguageCorrection = true
            DispatchQueue.global(qos: .userInitiated).async {
                do { try VNImageRequestHandler(cgImage: image).perform([request]) }
                catch { continuation.resume(throwing: error) }
            }
        }
    }

    static func importURL(_ enteredURL: String) async throws -> RecipeDraft {
        let value = enteredURL.trimmingCharacters(in: .whitespacesAndNewlines)
        let normalized = value.lowercased().hasPrefix("http") ? value : "https://\(value)"
        guard let url = URL(string: normalized), ["http", "https"].contains(url.scheme?.lowercased())
        else { throw ImportError.invalidURL }

        var request = URLRequest(url: url)
        request.timeoutInterval = 15
        request.setValue("Mozilla/5.0 (iPhone) Buttery/1.0", forHTTPHeaderField: "User-Agent")
        request.setValue("text/html,application/xhtml+xml", forHTTPHeaderField: "Accept")
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode),
              http.mimeType?.contains("html") != false,
              data.count <= 2_000_000,
              let html = String(data: data, encoding: .utf8) else { throw ImportError.noRecipe }

        if var draft = structuredRecipe(in: html) {
            guard !draft.title.isEmpty, !draft.ingredients.isEmpty || !draft.instructions.isEmpty
            else { throw ImportError.noRecipe }
            draft.sourceUrl = url
            draft.originalRawText = String(html.prefix(100_000))
            return draft
        }

        let isolated = isolateRecipeText(html)
        var draft = parseText(isolated)
        guard !draft.title.isEmpty, !draft.ingredients.isEmpty || !draft.instructions.isEmpty
        else { throw ImportError.noRecipe }
        draft.sourceUrl = url
        draft.originalRawText = isolated
        return draft
    }

    static func parseText(_ raw: String) -> RecipeDraft {
        let lines = raw.replacingOccurrences(of: "\r", with: "")
            .components(separatedBy: "\n")
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty && !isClutter($0) }
        var draft = RecipeDraft()
        guard !lines.isEmpty else { return draft }
        draft.title = lines.first(where: isProbableTitle) ?? lines.first ?? ""
        var section = Section.unknown
        var ingredients: [String] = [], instructions: [String] = [], notes: [String] = []

        for line in lines where line != draft.title {
            let lower = line.lowercased().trimmingCharacters(in: .punctuationCharacters)
            if ["ingredient", "ingredients"].contains(lower) { section = .ingredients; continue }
            if ["direction", "directions", "instruction", "instructions", "method", "steps"].contains(lower) {
                section = .instructions; continue
            }
            if ["note", "notes", "tip", "tips", "recipe notes"].contains(lower) {
                section = .notes; continue
            }
            if let metadata = metadata(line) {
                switch metadata.key {
                case "prep": draft.prepTime = metadata.value
                case "cook": draft.cookTime = metadata.value
                case "total": draft.totalTime = metadata.value
                case "servings": draft.servings = metadata.value
                default: break
                }
                continue
            }
            let clean = cleanListMarker(line)
            switch section {
            case .ingredients: if line.count <= 500 { ingredients.append(clean) }
            case .instructions: if line.count <= 500 { instructions.append(clean) }
            case .notes: if line.count <= 500 { notes.append(line) }
            case .unknown:
                if ingredientPattern.firstMatch(in: line, range: NSRange(line.startIndex..., in: line)) != nil {
                    ingredients.append(clean)
                } else if instructionPattern.firstMatch(in: line, range: NSRange(line.startIndex..., in: line)) != nil {
                    instructions.append(clean)
                } else if notes.joined(separator: "\n").count < 1_500 {
                    notes.append(line)
                }
            }
        }
        draft.ingredients = ingredients.joined(separator: "\n")
        draft.instructions = instructions.joined(separator: "\n")
        draft.notes = String(notes.joined(separator: "\n").prefix(1_500))
        draft.originalRawText = raw
        return draft
    }

    private static func structuredRecipe(in html: String) -> RecipeDraft? {
        let pattern = #"<script\b(?=[^>]*\btype\s*=\s*["']application/ld\+json["'])[^>]*>([\s\S]*?)</script>"#
        guard let regex = try? NSRegularExpression(pattern: pattern, options: .caseInsensitive) else { return nil }
        for match in regex.matches(in: html, range: NSRange(html.startIndex..., in: html)) {
            guard let range = Range(match.range(at: 1), in: html) else { continue }
            let jsonText = String(html[range]).trimmingCharacters(in: .whitespacesAndNewlines)
                .replacingOccurrences(of: "<!--", with: "")
                .replacingOccurrences(of: "-->", with: "")
            guard let data = jsonText.data(using: .utf8),
                  let json = try? JSONSerialization.jsonObject(with: data),
                  let recipe = findRecipe(json) else { continue }
            var draft = RecipeDraft()
            draft.title = cleanText(string(recipe["name"]))
            draft.notes = String(cleanText(string(recipe["description"])).prefix(600))
            draft.prepTime = formatDuration(string(recipe["prepTime"]))
            draft.cookTime = formatDuration(string(recipe["cookTime"]))
            draft.totalTime = formatDuration(string(recipe["totalTime"]))
            draft.servings = yieldString(recipe["recipeYield"])
            draft.ingredients = stringArray(recipe["recipeIngredient"]).map(cleanText).filter { !$0.isEmpty }.joined(separator: "\n")
            draft.instructions = instructionStrings(recipe["recipeInstructions"]).map(cleanText)
                .filter { !$0.isEmpty }.enumerated().map { "\($0.offset + 1). \($0.element)" }.joined(separator: "\n")
            if !draft.title.isEmpty { return draft }
        }
        return nil
    }

    private static func isolateRecipeText(_ html: String) -> String {
        let title = metaContent("og:title", in: html) ?? capture(#"<title\b[^>]*>([\s\S]*?)</title>"#, in: html) ?? ""
        let description = String((metaContent("og:description", in: html) ?? "").prefix(600))
        let stripped = html
            .replacingOccurrences(of: #"<(script|style|nav|footer|aside|form|iframe|noscript)\b[^>]*>[\s\S]*?</\1>"#, with: " ", options: [.regularExpression, .caseInsensitive])
            .replacingOccurrences(of: #"<(br|p|div|li|h[1-6]|section|article)\b[^>]*>"#, with: "\n", options: [.regularExpression, .caseInsensitive])
        let visible = cleanHTML(stripped)
        let lines = visible.components(separatedBy: .newlines).map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty && $0.count <= 500 && !isWebClutter($0) }
            .reduce(into: [String]()) { if !$0.contains($1) { $0.append($1) } }
        guard let ingredientStart = lines.firstIndex(where: isIngredientHeading),
              let instructionStart = lines.firstIndex(where: isInstructionHeading) else { return "" }
        let notesStart = lines.firstIndex(where: isNotesHeading)
        let ingredientEnd = [instructionStart, notesStart].compactMap { $0 }.filter { $0 > ingredientStart }.min() ?? lines.count
        let instructionEnd = [notesStart].compactMap { $0 }.filter { $0 > instructionStart }.min() ?? lines.count
        var result = [cleanText(title)]
        if !description.isEmpty { result += ["Notes", cleanText(description)] }
        result += Array(lines[ingredientStart..<min(ingredientEnd, ingredientStart + 81)])
        result += Array(lines[instructionStart..<min(instructionEnd, instructionStart + 81)])
        if let notesStart { result += Array(lines[notesStart..<min(lines.count, notesStart + 21)]) }
        return result.filter { !$0.isEmpty }.joined(separator: "\n")
    }

    private static func findRecipe(_ value: Any) -> [String: Any]? {
        if let object = value as? [String: Any] {
            let types = stringArray(object["@type"])
            if types.contains(where: { $0.lowercased() == "recipe" || $0.lowercased().hasSuffix("/recipe") }) { return object }
            for child in object.values { if let found = findRecipe(child) { return found } }
        } else if let array = value as? [Any] {
            for child in array { if let found = findRecipe(child) { return found } }
        }
        return nil
    }

    private static func instructionStrings(_ value: Any?) -> [String] {
        if let text = value as? String { return text.components(separatedBy: .newlines) }
        if let array = value as? [Any] { return array.flatMap(instructionStrings) }
        if let object = value as? [String: Any] {
            if let items = object["itemListElement"] { return instructionStrings(items) }
            if let text = object["text"] { return instructionStrings(text) }
            if let name = object["name"] { return instructionStrings(name) }
        }
        return []
    }

    private static func string(_ value: Any?) -> String { value as? String ?? "" }
    private static func stringArray(_ value: Any?) -> [String] {
        if let value = value as? String { return [value] }
        return (value as? [Any] ?? []).compactMap { $0 as? String }
    }
    private static func yieldString(_ value: Any?) -> String {
        if let number = value as? NSNumber { return number.stringValue }
        return stringArray(value).map(cleanText).joined(separator: ", ")
    }
    private static func cleanHTML(_ html: String) -> String {
        guard let data = html.data(using: .utf8),
              let attributed = try? NSAttributedString(
                data: data,
                options: [.documentType: NSAttributedString.DocumentType.html, .characterEncoding: String.Encoding.utf8.rawValue],
                documentAttributes: nil
              ) else { return html.replacingOccurrences(of: "<[^>]+>", with: " ", options: .regularExpression) }
        return attributed.string
    }
    private static func cleanText(_ value: String) -> String {
        cleanHTML(value).replacingOccurrences(of: #"\s+"#, with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }
    private static func capture(_ pattern: String, in text: String) -> String? {
        guard let regex = try? NSRegularExpression(pattern: pattern, options: .caseInsensitive),
              let match = regex.firstMatch(in: text, range: NSRange(text.startIndex..., in: text)),
              let range = Range(match.range(at: 1), in: text) else { return nil }
        return String(text[range])
    }
    private static func metaContent(_ property: String, in html: String) -> String? {
        guard let regex = try? NSRegularExpression(pattern: #"<meta\b[^>]*>"#, options: .caseInsensitive) else { return nil }
        for match in regex.matches(in: html, range: NSRange(html.startIndex..., in: html)) {
            guard let range = Range(match.range, in: html) else { continue }
            let tag = String(html[range])
            let key = capture(#"(?:property|name)\s*=\s*["']([^"']+)["']"#, in: tag)
            if key?.caseInsensitiveCompare(property) == .orderedSame {
                return capture(#"content\s*=\s*["']([^"']*)["']"#, in: tag)
            }
        }
        return nil
    }
    private static func metadata(_ line: String) -> (key: String, value: String)? {
        let pattern = #"^\s*(prep(?:\s*time)?|cook(?:\s*time)?|total(?:\s*time)?|servings?|serves|yield)\s*:?\s*(.+)$"#
        guard let regex = try? NSRegularExpression(pattern: pattern, options: .caseInsensitive),
              let match = regex.firstMatch(in: line, range: NSRange(line.startIndex..., in: line)),
              let keyRange = Range(match.range(at: 1), in: line), let valueRange = Range(match.range(at: 2), in: line) else { return nil }
        let rawKey = line[keyRange].lowercased()
        let key = rawKey.hasPrefix("prep") ? "prep" : rawKey.hasPrefix("cook") ? "cook" : rawKey.hasPrefix("total") ? "total" : "servings"
        return (key, String(line[valueRange]).trimmingCharacters(in: .whitespaces))
    }
    private static func formatDuration(_ value: String) -> String {
        guard value.uppercased().hasPrefix("P"),
              let regex = try? NSRegularExpression(pattern: #"P(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?)?"#, options: .caseInsensitive),
              let match = regex.firstMatch(in: value, range: NSRange(value.startIndex..., in: value)) else { return cleanText(value) }
        func number(_ index: Int) -> Int? {
            guard match.range(at: index).location != NSNotFound, let range = Range(match.range(at: index), in: value) else { return nil }
            return Int(value[range])
        }
        return [(number(1).map { "\($0) days" }), (number(2).map { "\($0) hr" }), (number(3).map { "\($0) min" })]
            .compactMap { $0 }.joined(separator: " ")
    }
    private static func cleanListMarker(_ line: String) -> String {
        line.replacingOccurrences(of: #"^\s*((step\s*)?\d+[.):]|[-*•])\s*"#, with: "", options: [.regularExpression, .caseInsensitive])
    }
    private static func isProbableTitle(_ line: String) -> Bool {
        line.count >= 2 && line.count <= 90 && !isIngredientHeading(line) && !isInstructionHeading(line) && metadata(line) == nil
    }
    private static func isIngredientHeading(_ line: String) -> Bool { line.range(of: #"^\s*ingredients?\s*:?\s*$"#, options: [.regularExpression, .caseInsensitive]) != nil }
    private static func isInstructionHeading(_ line: String) -> Bool { line.range(of: #"^\s*(instructions?|directions?|method|steps?)\s*:?\s*$"#, options: [.regularExpression, .caseInsensitive]) != nil }
    private static func isNotesHeading(_ line: String) -> Bool { line.range(of: #"^\s*(recipe\s+)?notes?\s*:?\s*$"#, options: [.regularExpression, .caseInsensitive]) != nil }
    private static func isClutter(_ line: String) -> Bool {
        line.range(of: #"^\s*(like|share|follow|comment|save this recipe|full recipe below|join my group)(\s+.*)?$"#, options: [.regularExpression, .caseInsensitive]) != nil
    }
    private static func isWebClutter(_ line: String) -> Bool {
        line.range(of: #"\b(jump to recipe|print recipe|newsletter|subscribe|sign up|advertisement|related posts?|comments?|leave a reply|share on|author bio|frequently asked questions?|privacy policy|cookie policy)\b"#, options: [.regularExpression, .caseInsensitive]) != nil
    }
    private static let ingredientPattern = try! NSRegularExpression(pattern: #"(\d|[½¼¾⅓⅔⅛]|\b(one|two|three)\b).*\b(cups?|tbsp|tablespoons?|tsp|teaspoons?|oz|ounces?|lbs?|pounds?|grams?|g|kg|ml|liters?|cloves?|cans?|packages?|pinch)\b"#, options: .caseInsensitive)
    private static let instructionPattern = try! NSRegularExpression(pattern: #"^\s*((step\s*)?\d+[.):]|[-*•])?\s*(add|mix|stir|bake|cook|heat|simmer|whisk|combine|pour|chop|slice|season|preheat|serve|place|bring|remove|fold|blend|roast)\b"#, options: .caseInsensitive)
    private enum Section { case unknown, notes, ingredients, instructions }
    enum ImportError: LocalizedError {
        case invalidImage, invalidURL, noRecipe
        var errorDescription: String? {
            switch self {
            case .invalidImage: "That image could not be read."
            case .invalidURL: "Enter a valid recipe URL."
            case .noRecipe: "This page could not be imported cleanly. Try a different recipe page or a clear recipe photo."
            }
        }
    }
}
