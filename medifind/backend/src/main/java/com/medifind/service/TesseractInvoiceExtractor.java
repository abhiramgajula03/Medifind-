package com.medifind.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medifind.model.Invoice;
import com.medifind.model.InvoiceItem;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TesseractInvoiceExtractor implements InvoiceExtractionService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Value("${tesseract.datapath:/usr/share/tesseract-ocr/4.00/tessdata}")
    private String tesseractDataPath;

    @PostConstruct
    public void init() {
        System.out.println("[TesseractInvoiceExtractor] Initialized with datapath=" + tesseractDataPath);
    }

    @Override
    public ExtractionResult extractInvoice(MultipartFile file, Long shopId, Long agencyId, String agencyName) throws Exception {
        BufferedImage image = ImageIO.read(file.getInputStream());
        if (image == null) {
            throw new IllegalArgumentException("Unsupported image format. Please upload JPG, PNG, or WebP.");
        }

        Path tempDir = Files.createTempDirectory("tesseract_");
        String fileName = UUID.randomUUID() + ".png";
        Path pngPath = tempDir.resolve(fileName);
        ImageIO.write(image, "png", pngPath.toFile());

        ITesseract tesseract = new Tesseract();
        tesseract.setDatapath(tesseractDataPath);
        tesseract.setLanguage("eng");
        tesseract.setPageSegMode(6);
        tesseract.setOcrEngineMode(1);
        tesseract.setTessVariable("preserve_interword_spaces", "1");

        String extractedText = "";
        try {
            extractedText = tesseract.doOCR(pngPath.toFile());
        } catch (TesseractException e) {
            throw new RuntimeException("OCR failed: " + e.getMessage(), e);
        } finally {
            Files.deleteIfExists(pngPath);
            Files.deleteIfExists(tempDir);
        }

        return extractInvoiceText(extractedText, shopId, agencyId, agencyName);
    }

    @Override
    public ExtractionResult extractInvoiceText(String extractedText, Long shopId, Long agencyId, String agencyName) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("extractedTextLength", extractedText.length());

        InvoiceData data = parseInvoiceData(extractedText, agencyId, agencyName, shopId);
        BigDecimal confidence = estimateConfidence(extractedText, data);

        Invoice invoice = data.toInvoice(null, extractedText);
        invoice.setConfidenceScore(confidence);

        return new ExtractionResult(invoice, data.items, confidence, extractedText, metadata);
    }

    private static final Set<String> HEADER_KEYWORDS = Set.of(
            "S.NO", "S NO", "SR NO", "SNO", "ITEM", "PRODUCT", "MEDICINE", "NAME",
            "QTY", "QUANTITY", "PACK", "BATCH", "EXP", "EXPIRY", "MRP", "RATE",
            "AMOUNT", "TOTAL", "GRAND", "SUBTOTAL", "DISCOUNT", "GST", "CGST", "SGST",
            "HSN", "CODE", "DESCRIPTION", "DETAILS", "INVOICE", "BILL", "DATE",
            "CUSTOMER", "SUPPLIER", "AGENCY", "ADDRESS", "PHONE", "GSTIN",
            "PARTICULARS", "SERIAL", "NO.", "NO", "UNIT", "VALUE"
    );

    private static final Set<String> FOOTER_KEYWORDS = Set.of(
            "TOTAL", "GRAND TOTAL", "NET AMOUNT", "SUB TOTAL", "ROUND OFF",
            "IN WORDS", "RECEIVED", "BALANCE", "DECLARATION", "TERMS", "NOTE",
            "THANK YOU", "AUTHORISED", "SIGNATURE", "FOR", "COMPANY"
    );

    private String cleanMedicineName(String raw) {
        if (raw == null) return null;
        String name = raw.trim();
        if (name.length() < 2) return null;
        name = name.replaceAll("\\s+", " ");
        name = name.replaceAll("(?i)\\s*(batch\\s*[:#]?\\s*[A-Z0-9\\-]+)\\s*", " ");
        name = name.replaceAll("(?i)\\s*(mfg\\s*[:#]?\\s*[A-Z0-9\\-]+)\\s*", " ");
        name = name.replaceAll("(?i)\\s*(exp\\s*[:.]?\\s*\\d{1,2}[\\-/]\\d{2,4})\\s*", " ");
        name = name.replaceAll("\\s*\\d{1,2}[\\-/]\\d{2,4}\\s*", " ");
        name = name.replaceAll("\\s*\\d{1,2}[\\-/]\\d{2}[\\-/]\\d{4}\\s*", " ");
        name = name.replaceAll("\\s*[A-Z]{2,}\\d{3,}\\b\\s*", " ");
        name = name.replaceAll("\\s+", " ").trim();
        if (name.matches("^[0-9\\s\\-.,/]+$")) return null;
        return name.isEmpty() ? null : name;
    }

    private boolean looksLikeMedicineLine(String line) {
        if (line.length() < 4) return false;
        String upper = line.toUpperCase();
        if (HEADER_KEYWORDS.stream().anyMatch(upper::contains)) return false;
        if (FOOTER_KEYWORDS.stream().anyMatch(upper::contains)) return false;
        if (upper.matches("^\\s*\\d+\\s*$")) return false;
        if (upper.matches("^\\s*[-=]+\\s*$")) return false;
        return true;
    }

    private InvoiceItem parseFlexibleItem(String line) {
        InvoiceItem item = new InvoiceItem();
        Pattern batchPattern = Pattern.compile("(?i)(batch\\s*[:#]?\\s*|BATCH\\s*[:#]?\\s*)([A-Z0-9\\-]+)");
        Matcher batchMatcher = batchPattern.matcher(line);
        if (batchMatcher.find()) item.setBatchNo(batchMatcher.group(2).trim());

        Pattern expiryPattern = Pattern.compile("(?i)(exp(?:\\s*[:.]?\\s*|\\s+iry\\s*[:.]?\\s*)|expiry\\s*[:.]?\\s*)(\\d{1,2}[\\-/]\\d{2,4})");
        Matcher expiryMatcher = expiryPattern.matcher(line);
        if (expiryMatcher.find()) item.setExpiryDate(parseDate(expiryMatcher.group(2)));

        String withoutDates = line.replaceAll("\\d{1,2}[\\-/]\\d{2,4}", " ").replaceAll("\\d{1,2}[\\-/]\\d{2}[\\-/]\\d{4}", " ");
        Pattern numberPattern = Pattern.compile("(\\d+(?:\\.\\d+)?)");
        List<String> allNumbers = new ArrayList<>();
        Matcher numMatcher = numberPattern.matcher(withoutDates);
        while (numMatcher.find()) allNumbers.add(numMatcher.group(1));

        List<Double> decimals = new ArrayList<>();
        for (String n : allNumbers) { if (n.contains(".")) { try { decimals.add(Double.parseDouble(n)); } catch (Exception ignored) {} } }
        Collections.sort(decimals);
        if (decimals.size() >= 2) {
            item.setRate(new BigDecimal(decimals.get(0).toString()));
            item.setAmount(new BigDecimal(decimals.get(decimals.size() - 1).toString()));
        } else if (decimals.size() == 1) {
            item.setRate(new BigDecimal(decimals.get(0).toString()));
            item.setAmount(new BigDecimal(decimals.get(0).toString()));
        }

        List<Integer> integers = new ArrayList<>();
        for (String n : allNumbers) { if (!n.contains(".")) { try { integers.add(Integer.parseInt(n)); } catch (Exception ignored) {} } }
        if (integers.size() >= 1) {
            Integer qty = integers.get(0);
            if (qty <= 1000) item.setQuantity(qty);
        }

        String medName = line.replaceAll("\\d+", " ").replaceAll("[^a-zA-Z\\s/.]", " ").trim();
        medName = medName.replaceAll("\\s+", " ").trim();
        if (medName.length() > 2) medName = cleanMedicineName(medName);
        if (medName == null || medName.length() <= 2) medName = cleanMedicineName(line);
        item.setProductName(medName);
        return item;
    }

    private InvoiceData parseInvoiceData(String text, Long agencyId, String agencyName, Long shopId) {
        InvoiceData data = new InvoiceData();
        data.agencyId = agencyId;
        data.agencyName = agencyName;
        data.shopId = shopId;
        String upper = text.toUpperCase();

        if (upper.contains("GST INVOICE") || upper.contains("TAX INVOICE") || upper.contains("GST BILL")) data.invoiceType = "GST Invoice";
        else if (upper.contains("CREDIT NOTE")) data.invoiceType = "Credit Note";
        else if (upper.contains("DELIVERY CHALLAN") || upper.contains("CHALLAN")) data.invoiceType = "Delivery Challan";
        else data.invoiceType = "Invoice";

        Pattern invNo = Pattern.compile("(?:INVOICE|INV|BILL)\\s*(?:NO|NUMBER)?[^\\n]{0,30}?\\b([A-Z]{1,4}\\d{5,})\\b");
        Matcher m = invNo.matcher(upper);
        if (m.find()) data.invoiceNo = m.group(1).trim();

        Pattern invDate = Pattern.compile("(?:DATE|DT|BILL\\s*DATE|INV\\s*DATE)[.:\\-\\s]*([0-9]{2}[-/][0-9]{2}[-/][0-9]{4})");
        m = invDate.matcher(upper);
        if (m.find()) data.invoiceDate = parseDate(m.group(1));

        data.totalAmount = findInvoiceTotal(text);
        if (data.totalAmount == null) data.totalAmount = BigDecimal.ZERO;

        Pattern gstin = Pattern.compile("([0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1})");
        m = gstin.matcher(upper);
        if (m.find()) data.gstNumber = m.group(1);

        data.items = parseInvoiceItems(text);
        data.totalItems = data.items.size();
        if (data.totalAmount.signum() == 0) {
            data.totalAmount = data.items.stream()
                    .map(InvoiceItem::getAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        return data;
    }

    private BigDecimal findInvoiceTotal(String text) {
        Pattern totalAmount = Pattern.compile(
                "(?im)\\b(?:GRAND\\s+TOTAL|NET\\s+AMOUNT|AMOUNT\\s+PAYABLE|TOTAL)\\b[^\\r\\n0-9]{0,50}(?:RS\\.?|INR|₹)?\\s*([0-9][0-9,]*\\.[0-9]{2})"
        );
        Matcher matcher = totalAmount.matcher(text);
        BigDecimal best = null;
        while (matcher.find()) {
            try {
                BigDecimal candidate = new BigDecimal(matcher.group(1).replace(",", ""));
                if (candidate.signum() > 0 && (best == null || candidate.compareTo(best) > 0)) best = candidate;
            } catch (NumberFormatException ignored) {
                // Continue after a malformed OCR amount.
            }
        }
        return best;
    }

    private List<InvoiceItem> parseInvoiceItems(String text) {
        List<InvoiceItem> items = new ArrayList<>();
        String[] lines = text.split("\\n");
        List<InvoiceItem> strictItems = new ArrayList<>();
        List<InvoiceItem> flexibleItems = new ArrayList<>();

        Pattern tableRowPattern = Pattern.compile("^(\\d{1,2})\\s+(\\d{1,3})\\s+([A-Z]{2,})\\s+(\\d{1,3}[A-Z]{0,5})\\s+(.+?)\\s+([A-Z0-9]{5,})\\s+\\d{1,2}/\\d{2}.*$");
        Pattern strictPattern = Pattern.compile("(\\d+)\\s+(\\d+)\\s+([A-Z0-9]+)\\s+(.+?)\\s+(\\d{2,}[A-Z0-9]*)\\s+([0-9]+\\.[0-9]+)\\s+([0-9]+\\.[0-9]+)");
        Pattern mediumPattern = Pattern.compile("(\\d+)\\s+(\\d+)\\s+(.+?)\\s+(\\d{2,}[A-Z0-9]*)\\s+([0-9]+\\.[0-9]+)\\s+([0-9]+\\.[0-9]+)");

        for (String line : lines) {
            line = line.replace('|', ' ').replace(']', ' ').trim().replaceAll("\\s+", " ");
            if (line.length() < 6 || !looksLikeMedicineLine(line)) continue;

            Matcher tableRowMatcher = tableRowPattern.matcher(line.toUpperCase());
            if (tableRowMatcher.matches()) {
                InvoiceItem item = parseFlexibleItem(line);
                item.setSNo(Integer.parseInt(tableRowMatcher.group(1)));
                item.setQuantity(Integer.parseInt(tableRowMatcher.group(2)));
                item.setManufacturer(tableRowMatcher.group(3));
                item.setProductName(cleanMedicineName(tableRowMatcher.group(5)));
                item.setBatchNo(tableRowMatcher.group(6));
                if (item.getProductName() != null && item.getProductName().length() > 2) {
                    flexibleItems.add(item);
                }
                continue;
            }

            Matcher strictMatcher = strictPattern.matcher(line);
            if (strictMatcher.find()) {
                InvoiceItem item = new InvoiceItem();
                try {
                    item.setSNo(Integer.parseInt(strictMatcher.group(1).trim()));
                    item.setQuantity(Integer.parseInt(strictMatcher.group(2).trim()));
                    item.setManufacturer(strictMatcher.group(3).trim());
                    item.setProductName(cleanMedicineName(strictMatcher.group(4).trim()));
                    item.setBatchNo(strictMatcher.group(5).trim());
                    item.setRate(new BigDecimal(strictMatcher.group(6).trim()));
                    item.setAmount(new BigDecimal(strictMatcher.group(7).trim()));
                } catch (Exception ignored) {}
                if (item.getProductName() != null && item.getProductName().length() > 2) { strictItems.add(item); }
                continue;
            }

            Matcher mediumMatcher = mediumPattern.matcher(line);
            if (mediumMatcher.find()) {
                InvoiceItem item = new InvoiceItem();
                try {
                    item.setSNo(null);
                    item.setQuantity(Integer.parseInt(mediumMatcher.group(1).trim()));
                    item.setProductName(cleanMedicineName(mediumMatcher.group(2).trim()));
                    item.setBatchNo(mediumMatcher.group(3).trim());
                    item.setRate(new BigDecimal(mediumMatcher.group(4).trim()));
                    item.setAmount(new BigDecimal(mediumMatcher.group(5).trim()));
                } catch (Exception ignored) {}
                if (item.getProductName() != null && item.getProductName().length() > 2) { strictItems.add(item); }
                continue;
            }

            if (line.matches(".*\\d+.*\\.[0-9]+.*")) flexibleItems.add(parseFlexibleItem(line));
        }

        if (!strictItems.isEmpty()) items.addAll(strictItems);
        if (items.isEmpty() && !flexibleItems.isEmpty()) {
            for (InvoiceItem fi : flexibleItems) { if (fi.getProductName() != null && fi.getProductName().length() > 2) items.add(fi); }
        }
        if (items.isEmpty()) {
            for (String line : lines) {
                line = line.trim();
                if (line.length() > 4 && looksLikeMedicineLine(line)) {
                    String medName = cleanMedicineName(line);
                    if (medName != null && medName.length() > 2) {
                        InvoiceItem item = new InvoiceItem();
                        item.setProductName(medName);
                        item.setQuantity(1);
                        item.setAmount(BigDecimal.ZERO);
                        items.add(item);
                    }
                }
            }
        }
        return items;
    }

    private LocalDate parseDate(String text) {
        if (text == null) return null;
        try {
            String[] parts = text.split("[-/]");
            if (parts.length == 3) {
                int day = Integer.parseInt(parts[0]);
                int month = Integer.parseInt(parts[1]);
                int year = Integer.parseInt(parts[2]);
                if (year < 100) year += 2000;
                if (year > 1900 && month >= 1 && month <= 12 && day >= 1 && day <= 31) return LocalDate.of(year, month, day);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private BigDecimal estimateConfidence(String text, InvoiceData data) {
        int score = 0;
        if (data.invoiceNo != null && !data.invoiceNo.isBlank()) score += 20;
        if (data.invoiceDate != null) score += 15;
        if (data.totalAmount != null && data.totalAmount.compareTo(BigDecimal.ZERO) > 0) score += 20;
        if (data.gstNumber != null && !data.gstNumber.isBlank()) score += 15;
        if (!data.items.isEmpty()) score += 30;
        if (text.length() > 200) score += 10;
        if (text.length() > 500) score += 10;
        return new BigDecimal(Math.min(score, 100));
    }

    private static class InvoiceData {
        Long agencyId;
        String agencyName;
        Long shopId;
        String invoiceType;
        String invoiceNo;
        LocalDate invoiceDate;
        LocalDate dueDate;
        String orderNo;
        LocalDate orderDate;
        Integer cases;
        String lrNo;
        LocalDate lrDate;
        String transport;
        Integer totalItems;
        BigDecimal discountAmount = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalTaxableAmount = BigDecimal.ZERO;
        BigDecimal taxAmount = BigDecimal.ZERO;
        BigDecimal grossAmount = BigDecimal.ZERO;
        BigDecimal netAmount = BigDecimal.ZERO;
        BigDecimal otherCharges = BigDecimal.ZERO;
        String supplierName;
        String dealerName;
        String companyName;
        String customerName;
        String gstNumber;
        String panTin;
        String paymentMode;
        String bankInfo;
        String ewayBillNo;
        String vehicleNo;
        String transporter;
        List<InvoiceItem> items = new ArrayList<>();

        Invoice toInvoice(String billPhoto, String rawText) {
            Map<String, Object> supplier = new HashMap<>();
            supplier.put("supplier_name", agencyName);
            supplier.put("business_type", "Pharmaceutical / Medical Distributor");

            return Invoice.builder()
                    .shopId(shopId)
                    .agencyId(agencyId)
                    .agencyName(agencyName)
                    .invoiceType(invoiceType != null ? invoiceType : "Invoice")
                    .invoiceNo(invoiceNo != null ? invoiceNo : "SCAN-" + System.currentTimeMillis())
                    .invoiceDate(invoiceDate != null ? invoiceDate : LocalDate.now())
                    .dueDate(dueDate)
                    .orderNo(orderNo)
                    .orderDate(orderDate)
                    .cases(cases)
                    .lrNo(lrNo)
                    .lrDate(lrDate)
                    .transporter(transporter)
                    .totalItems(totalItems != null ? totalItems : items.size())
                    .discountAmount(discountAmount)
                    .totalAmount(totalAmount != null ? totalAmount : BigDecimal.ZERO)
                    .totalTaxableAmount(totalTaxableAmount)
                    .taxAmount(taxAmount)
                    .grossAmount(grossAmount)
                    .netAmount(netAmount)
                    .otherCharges(otherCharges)
                    .supplierName(supplierName)
                    .dealerName(dealerName)
                    .companyName(companyName)
                    .customerName(customerName)
                    .gstNumber(gstNumber)
                    .panTin(panTin)
                    .paymentMode(paymentMode)
                    .bankInfo(bankInfo)
                    .ewayBillNo(ewayBillNo)
                    .vehicleNo(vehicleNo)
                    .transporter(transporter)
                    .supplierJson(toJson(supplier))
                    .partyJson(toJson(Map.of("party_name", "CARE PHARMACY", "party_location", "BHOOPALPALLY", "party_state", "Telangana")))
                    .billPhoto(billPhoto)
                    .rawText(rawText)
                    .isVerified(false)
                    .build();
        }

        private String toJson(Object obj) {
            try { return OBJECT_MAPPER.writeValueAsString(obj); } catch (Exception e) { return "{}"; }
        }
    }
}
