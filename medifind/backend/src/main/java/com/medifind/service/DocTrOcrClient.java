package com.medifind.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.Optional;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class DocTrOcrClient {
    private final RestClient restClient = RestClient.create();

    @Value("${doctr.ocr.enabled:true}")
    private boolean enabled;

    @Value("${doctr.ocr.url:http://localhost:8002}")
    private String serviceUrl;

    public Optional<OcrDocument> extract(MultipartFile file) {
        if (!enabled) return Optional.empty();
        try {
            ByteArrayResource image = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", image);
            Map<?, ?> response = restClient.post()
                    .uri(serviceUrl + "/ocr/invoice")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            if (response == null || !(response.get("text") instanceof String text) || text.isBlank()) {
                return Optional.empty();
            }
            Object documentJson = response.get("documentJson");
            return Optional.of(new OcrDocument(text, documentJson == null ? "{}" : String.valueOf(documentJson)));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    /** Returns the lossless final_invoice.json response from the Python service. */
    public Map<String, Object> extractFinalInvoice(MultipartFile file) {
        return extractFinalInvoice(file, null, null, null);
    }

    /** Forwards the actual browser crop plus optional debug artifacts, not preview URLs. */
    public Map<String, Object> extractFinalInvoice(MultipartFile file, MultipartFile originalImage,
                                                    MultipartFile rotatedImage, String clientImageDiagnostics) {
        if (!enabled) throw new IllegalStateException("Python OCR service is disabled");
        try {
            String boundary = "MediFind-" + java.util.UUID.randomUUID();
            ByteArrayOutputStream payload = new ByteArrayOutputStream();
            appendFilePart(payload, boundary, "file", file);
            if (originalImage != null && !originalImage.isEmpty()) appendFilePart(payload, boundary, "original_image", originalImage);
            if (rotatedImage != null && !rotatedImage.isEmpty()) appendFilePart(payload, boundary, "rotated_image", rotatedImage);
            if (clientImageDiagnostics != null && !clientImageDiagnostics.isBlank()) {
                payload.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"client_metadata\"\r\nContent-Type: application/json\r\n\r\n" + clientImageDiagnostics + "\r\n").getBytes(StandardCharsets.UTF_8));
            }
            payload.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            HttpResponse<String> response = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build().send(HttpRequest.newBuilder(URI.create(serviceUrl + "/ocr/invoice"))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(payload.toByteArray())).build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IllegalStateException("Python OCR returned " + response.statusCode() + ": " + response.body());
            return new ObjectMapper().readValue(response.body(), Map.class);
        } catch (Exception error) { throw new IllegalStateException("Could not call Python OCR service", error); }
    }

    private byte[] getBytes(MultipartFile file) {
        try { return file.getBytes(); }
        catch (java.io.IOException error) { throw new IllegalStateException("Could not read uploaded bill", error); }
    }

    private void appendFilePart(ByteArrayOutputStream payload, String boundary, String name, MultipartFile file) throws java.io.IOException {
        String filename = file.getOriginalFilename() == null ? name + ".jpeg" : file.getOriginalFilename().replace("\"", "");
        String contentType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
        payload.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"; filename=\"" + filename + "\"\r\nContent-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        payload.write(getBytes(file));
        payload.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    public record OcrDocument(String text, String documentJson) {}
}
