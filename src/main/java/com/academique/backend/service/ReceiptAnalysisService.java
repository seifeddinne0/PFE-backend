package com.academique.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReceiptAnalysisService {

    private static final String MODEL = "openai/gpt-4o-mini";
    private static final String PROMPT = "Analyse this payment receipt image and extract the following information in JSON format only, no explanation:\n" +
        "{\n" +
        "  \"rib_destinataire\": \"the exact RIB or account number of the receiver/beneficiary (count the exact number of zeros carefully)\",\n" +
        "  \"code_paiement\": \"the unique payment reference/transaction code (often labeled as 'OPÉRATION N°', 'REF', or 'Transaction ID')\",\n" +
        "  \"montant\": \"the amount as a number with 2 decimals (ex: 650.00)\",\n" +
        "  \"date_paiement\": \"the payment date in YYYY-MM-DD format\",\n" +
        "  \"banque_emettrice\": \"the name of the sender bank if visible\"\n" +
        "}\n" +
        "If a field cannot be found in the receipt, set its value to null.\n" +
        "For the montant, normalize Tunisian format (650,000 or 650.000 should become 650.00).\n" +
        "Return only valid JSON, nothing else.";

    @Value("${openrouter.api.key:}")
    private String openRouterApiKey;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public ReceiptData analyze(MultipartFile file) {
        if (openRouterApiKey == null || openRouterApiKey.isBlank()) {
            throw new ReceiptAnalysisException("Clé API OpenRouter manquante. Veuillez configurer openrouter.api.key.");
        }

        try {
            byte[] bytes = file.getBytes();
            String base64 = Base64.getEncoder().encodeToString(bytes);
            String mediaType = file.getContentType() != null ? file.getContentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE;

            Map<String, Object> imageUrl = Map.of("url", "data:" + mediaType + ";base64," + base64);
            Map<String, Object> textContent = Map.of("type", "text", "text", PROMPT);
            Map<String, Object> imageContent = Map.of("type", "image_url", "image_url", imageUrl);
            
            Map<String, Object> message = Map.of(
                "role", "user",
                "content", List.of(textContent, imageContent)
            );

            Map<String, Object> payload = Map.of(
                "model", MODEL,
                "max_tokens", 1024,
                "messages", List.of(message)
            );

            String requestBody = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://openrouter.ai/api/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + openRouterApiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                System.err.println("OpenRouter Error Response: " + response.statusCode() + " - " + response.body());
                throw new ReceiptAnalysisException("Impossible d'analyser le reçu. Vérifiez que l'image est lisible.");
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                System.err.println("OpenRouter Invalid JSON Response: " + response.body());
                throw new ReceiptAnalysisException("Réponse invalide d'OpenRouter.");
            }

            String text = choices.get(0).path("message").path("content").asText();
            String jsonText = extractJson(text);
            JsonNode json = objectMapper.readTree(jsonText);

            return ReceiptData.builder()
                .ribDestinataire(readNullableText(json, "rib_destinataire"))
                .codePaiement(readNullableText(json, "code_paiement"))
                .montant(readNullableDouble(json, "montant"))
                .datePaiement(readNullableDate(json, "date_paiement"))
                .banqueEmettrice(readNullableText(json, "banque_emettrice"))
                .build();
        } catch (ReceiptAnalysisException ex) {
            throw ex;
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new ReceiptAnalysisException("Impossible d'analyser le reçu. Vérifiez que l'image est lisible.");
        }
    }

    private String extractJson(String text) {
        int start = text.indexOf("{");
        int end = text.lastIndexOf("}");
        if (start == -1 || end == -1 || end <= start) {
            throw new ReceiptAnalysisException("Impossible d'analyser le reçu. Vérifiez que l'image est lisible.");
        }
        return text.substring(start, end + 1).trim();
    }

    private String readNullableText(JsonNode json, String field) {
        JsonNode node = json.get(field);
        if (node == null || node.isNull()) return null;
        String value = node.asText();
        return value != null && !value.isBlank() ? value : null;
    }

    private Double readNullableDouble(JsonNode json, String field) {
        JsonNode node = json.get(field);
        if (node == null || node.isNull()) return null;
        String raw = node.asText();
        if (raw == null || raw.isBlank()) return null;
        String normalized = raw.replace(" ", "").replace(",", ".");
        try {
            return Double.parseDouble(normalized);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private LocalDate readNullableDate(JsonNode json, String field) {
        String raw = readNullableText(json, field);
        if (raw == null) return null;
        try {
            return LocalDate.parse(raw);
        } catch (Exception ex) {
            return null;
        }
    }
}
