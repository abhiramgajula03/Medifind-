package com.medifind.service;

import com.medifind.model.Invoice;
import com.medifind.model.InvoiceItem;
import org.springframework.web.multipart.MultipartFile;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface InvoiceExtractionService {

    ExtractionResult extractInvoice(MultipartFile file, Long shopId, Long agencyId, String agencyName) throws Exception;

    ExtractionResult extractInvoiceText(String text, Long shopId, Long agencyId, String agencyName);

    class ExtractionResult {
        private final Invoice invoice;
        private final List<InvoiceItem> items;
        private final BigDecimal confidence;
        private final String rawText;
        private final Map<String, Object> metadata;

        public ExtractionResult(Invoice invoice, List<InvoiceItem> items, BigDecimal confidence, String rawText, Map<String, Object> metadata) {
            this.invoice = invoice;
            this.items = items;
            this.confidence = confidence;
            this.rawText = rawText;
            this.metadata = metadata;
        }

        public Invoice getInvoice() { return invoice; }
        public List<InvoiceItem> getItems() { return items; }
        public BigDecimal getConfidence() { return confidence; }
        public String getRawText() { return rawText; }
        public Map<String, Object> getMetadata() { return metadata; }
    }
}
