package com.medifind.service;

import com.medifind.model.AttributeRegistry;
import com.medifind.model.InvoiceAttribute;
import com.medifind.repository.AttributeRegistryRepository;
import com.medifind.repository.InvoiceAttributeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AttributeNormalizationService {

    private final AttributeRegistryRepository registryRepository;
    private final InvoiceAttributeRepository invoiceAttributeRepository;

    private static final Map<String, String> DEFAULT_MAPPINGS = Map.ofEntries(
            Map.entry("QTY", "quantity"),
            Map.entry("QUANTITY", "quantity"),
            Map.entry("UNITS", "quantity"),
            Map.entry("NO.OF UNITS", "quantity"),
            Map.entry("NO OF UNITS", "quantity"),
            Map.entry("UNIT", "quantity"),
            Map.entry("QTY.", "quantity"),
            Map.entry("RATE", "unit_price"),
            Map.entry("UNIT RATE", "unit_price"),
            Map.entry("UNIT PRICE", "unit_price"),
            Map.entry("PRICE/UNIT", "unit_price"),
            Map.entry("PRICE PER UNIT", "unit_price"),
            Map.entry("MRP", "mrp"),
            Map.entry("AMOUNT", "amount"),
            Map.entry("TOTAL", "amount"),
            Map.entry("DISCOUNT", "discount"),
            Map.entry("GST", "gst_percent"),
            Map.entry("GSTIN", "gst_number"),
            Map.entry("GST NUMBER", "gst_number"),
            Map.entry("GST NO", "gst_number"),
            Map.entry("CGST", "cgst_rate"),
            Map.entry("SGST", "sgst_rate"),
            Map.entry("IGST", "igst_rate"),
            Map.entry("HSN", "hsn_code"),
            Map.entry("HSN CODE", "hsn_code"),
            Map.entry("BATCH", "batch_no"),
            Map.entry("BATCH NO", "batch_no"),
            Map.entry("EXPIRY", "expiry_date"),
            Map.entry("EXP", "expiry_date"),
            Map.entry("EXPIRY DATE", "expiry_date"),
            Map.entry("MFG", "manufacturer"),
            Map.entry("MANUFACTURER", "manufacturer"),
            Map.entry("MFR", "manufacturer"),
            Map.entry("PRODUCT", "product_name"),
            Map.entry("PRODUCT NAME", "product_name"),
            Map.entry("MEDICINE", "product_name"),
            Map.entry("MEDICINE NAME", "product_name"),
            Map.entry("S.NO", "s_no"),
            Map.entry("S NO", "s_no"),
            Map.entry("SR NO", "s_no"),
            Map.entry("SNO", "s_no"),
            Map.entry("PACK", "pack"),
            Map.entry("INVOICE NO", "invoice_no"),
            Map.entry("INVOICE NUMBER", "invoice_no"),
            Map.entry("INV NO", "invoice_no"),
            Map.entry("BILL NO", "invoice_no"),
            Map.entry("INVOICE DATE", "invoice_date"),
            Map.entry("BILL DATE", "invoice_date"),
            Map.entry("DATE", "invoice_date"),
            Map.entry("VEHICLE NO", "vehicle_no"),
            Map.entry("VEHICLE NUMBER", "vehicle_no"),
            Map.entry("E-WAY BILL", "eway_bill_no"),
            Map.entry("EWAY BILL", "eway_bill_no"),
            Map.entry("TRANSPORTER", "transporter"),
            Map.entry("PAYMENT MODE", "payment_mode"),
            Map.entry("MODE OF PAYMENT", "payment_mode"),
            Map.entry("BANK", "bank_info"),
            Map.entry("BANK DETAILS", "bank_info"),
            Map.entry("SUPPLIER", "supplier_name"),
            Map.entry("SUPPLIER NAME", "supplier_name"),
            Map.entry("DEALER", "dealer_name"),
            Map.entry("DEALER NAME", "dealer_name"),
            Map.entry("COMPANY", "company_name"),
            Map.entry("COMPANY NAME", "company_name"),
            Map.entry("CUSTOMER", "customer_name"),
            Map.entry("CUSTOMER NAME", "customer_name"),
            Map.entry("ORDER NO", "order_no"),
            Map.entry("ORDER NUMBER", "order_no"),
            Map.entry("ORDER DATE", "order_date"),
            Map.entry("DUE DATE", "due_date"),
            Map.entry("LR NO", "lr_no"),
            Map.entry("LR NUMBER", "lr_no"),
            Map.entry("PAN", "pan_tin"),
            Map.entry("TIN", "pan_tin")
    );

    private final Map<String, String> cache = new HashMap<>();

    public String normalize(String rawName) {
        if (rawName == null || rawName.isBlank()) return rawName;
        String key = rawName.trim().toUpperCase();
        if (cache.containsKey(key)) return cache.get(key);

        String normalized = DEFAULT_MAPPINGS.getOrDefault(key, findInRegistryOrCreate(key, rawName));
        cache.put(key, normalized);
        return normalized;
    }

    private String findInRegistryOrCreate(String key, String original) {
        Optional<AttributeRegistry> found = registryRepository.findByAttributeNameIgnoreCase(key);
        if (found.isPresent()) return found.get().getCanonicalName();

        AttributeRegistry registry = AttributeRegistry.builder()
                .attributeName(key)
                .canonicalName(key.toLowerCase().replaceAll("[^a-z0-9]+", "_"))
                .dataType("TEXT")
                .description("Auto-discovered attribute: " + original)
                .build();
        registryRepository.save(registry);
        return registry.getCanonicalName();
    }

    public List<InvoiceAttribute> normalizeAndSave(Long invoiceId, Map<String, Object> extractedAttributes) {
        List<InvoiceAttribute> result = new ArrayList<>();
        for (Map.Entry<String, Object> entry : extractedAttributes.entrySet()) {
            String rawKey = entry.getKey();
            String canonical = normalize(rawKey);
            String value = entry.getValue() != null ? String.valueOf(entry.getValue()) : null;
            BigDecimal confidence = entry.getValue() instanceof Map<?, ?> && ((Map<?, ?>) entry.getValue()).containsKey("confidence")
                    ? new BigDecimal(((Map<?, ?>) entry.getValue()).get("confidence").toString())
                    : BigDecimal.valueOf(0.8);

            InvoiceAttribute attr = InvoiceAttribute.builder()
                    .invoiceId(invoiceId)
                    .attributeName(canonical)
                    .attributeValue(value)
                    .confidence(confidence)
                    .build();
            result.add(attr);
        }
        return invoiceAttributeRepository.saveAll(result);
    }

    public List<AttributeRegistry> getKnownAttributes() {
        return registryRepository.findAll();
    }

    @Transactional
    public void registerAttribute(String attributeName, String canonicalName, String dataType, String description) {
        registryRepository.findByAttributeNameIgnoreCase(attributeName).orElseGet(() -> {
            AttributeRegistry reg = AttributeRegistry.builder()
                    .attributeName(attributeName)
                    .canonicalName(canonicalName)
                    .dataType(dataType != null ? dataType : "TEXT")
                    .description(description)
                    .build();
            return registryRepository.save(reg);
        });
    }
}
