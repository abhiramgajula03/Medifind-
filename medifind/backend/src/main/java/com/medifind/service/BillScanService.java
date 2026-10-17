package com.medifind.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medifind.model.*;
import com.medifind.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BillScanService {

    private final BillScanRepository billScanRepository;
    private final AgencyBillRepository agencyBillRepository;
    private final InventoryRepository inventoryRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceItemRepository invoiceItemRepository;
    private final InvoiceAttributeRepository invoiceAttributeRepository;
    private final ShopRepository shopRepository;
    private final ShopAgencyRepository shopAgencyRepository;
    private final BillDetectionService billDetectionService;
    private final InvoiceExtractionService invoiceExtractionService;
    private final DocTrOcrClient docTrOcrClient;
    private final AttributeNormalizationService attributeNormalizationService;
    private final InventoryStockService inventoryStockService;
    // Spring's configured mapper includes Java-time support. Do not replace it
    // with a bare mapper, otherwise InvoiceItem expiry dates fail serialization.
    private final ObjectMapper objectMapper;

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    /**
     * Stores the already-reviewed output of the docTR pipeline.  This intentionally
     * does not run any second OCR/parser: the JSON received here is the exact
     * structured result returned by the Python service, with any user edits applied.
     */
    @Transactional
    @SuppressWarnings("unchecked")
    public Invoice persistStructuredInvoice(Long shopId, Long agencyId, String agencyName,
                                            Map<String, Object> document) throws IOException {
        if (agencyId == null) {
            throw new IllegalArgumentException("Select a linked agency before saving this invoice.");
        }
        if (!shopAgencyRepository.existsByShopIdAndAgencyId(shopId, agencyId)) {
            throw new IllegalArgumentException("The selected agency is not linked to this shop.");
        }

        Map<String, Object> invoiceData = mapValue(document.get("invoice"));
        Map<String, Object> seller = mapValue(document.get("seller"));
        Map<String, Object> buyer = mapValue(document.get("buyer"));
        List<Map<String, Object>> items = listOfMaps(document.get("items"));
        String invoiceNo = stringValue(invoiceData.get("invoice_number"));
        if (invoiceNo.isBlank()) throw new IllegalArgumentException("OCR did not produce an invoice number.");

        // Retrying Confirm must not add the same stock twice.
        Optional<Invoice> existing = invoiceRepository.findByInvoiceNo(invoiceNo);
        if (existing.isPresent()) return existing.get();

        String resolvedAgencyName = agencyName == null || agencyName.isBlank()
                ? stringValue(seller.get("name")) : agencyName;
        LocalDate invoiceDate = parseInvoiceDate(stringValue(invoiceData.get("invoice_date")));
        BigDecimal total = decimalValue(document.get("total"));
        if (total.signum() == 0) {
            total = items.stream().map(item -> decimalValue(item.get("amount")))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        Invoice invoice = Invoice.builder()
                .shopId(shopId).agencyId(agencyId).agencyName(resolvedAgencyName)
                .invoiceType("PURCHASE").invoiceNo(invoiceNo).invoiceDate(invoiceDate)
                .dueDate(parseInvoiceDateOrNull(stringValue(invoiceData.get("due_date"))))
                .orderNo(stringValue(invoiceData.get("order_number")))
                .lrNo(stringValue(invoiceData.get("lr_number")))
                .transporter(stringValue(invoiceData.get("transport")))
                .totalItems(items.size()).totalAmount(total).netAmount(total).grossAmount(total)
                .supplierName(stringValue(seller.get("name"))).dealerName(stringValue(seller.get("name")))
                .customerName(stringValue(buyer.get("name"))).gstNumber(stringValue(seller.get("gstin")))
                .supplierJson(toJsonString(seller)).partyJson(toJsonString(buyer))
                .rawText(toJsonString(document)).isVerified(true).verifiedAt(LocalDateTime.now())
                .confidenceScore(BigDecimal.valueOf(0.95)).build();
        Invoice savedInvoice = invoiceRepository.save(invoice);

        List<InvoiceItem> savedItems = new ArrayList<>();
        int serial = 1;
        for (Map<String, Object> item : items) {
            InvoiceItem savedItem = InvoiceItem.builder()
                    .invoiceId(savedInvoice.getId()).shopId(shopId).sNo(serial++)
                    .productName(stringValue(item.get("product")))
                    .hsnCode(stringValue(item.get("hsn"))).manufacturer(stringValue(item.get("manufacturer")))
                    .pack(stringValue(item.get("pack"))).quantity(integerValue(item.get("quantity"), 1))
                    .unit("strips").batchNo(stringValue(item.get("batch")))
                    .expiryDate(parseExpiryDate(stringValue(item.get("expiry"))))
                    .mrp(decimalValue(item.get("mrp"))).rate(decimalValue(item.get("rate")))
                    .discount(decimalValue(item.get("discount")))
                    .sgstRate(decimalValue(item.get("sgst_percent"))).sgstValue(decimalValue(item.get("sgst_value")))
                    .cgstRate(decimalValue(item.get("cgst_percent"))).cgstValue(decimalValue(item.get("cgst_value")))
                    .amount(decimalValue(item.get("amount"))).build();
            savedItems.add(invoiceItemRepository.save(savedItem));
            updateInventory(shopId, savedItem, resolvedAgencyName);
        }

        createAgencyBillFromScan(shopId, agencyId, resolvedAgencyName, total, savedItems, null,
                savedInvoice.getId(), invoiceDate);
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("ocr_warnings", document.getOrDefault("warnings", List.of()));
        attributes.put("ocr_json", document);
        attributeNormalizationService.normalizeAndSave(savedInvoice.getId(), attributes);
        return savedInvoice;
    }

    private void updateInventory(Long shopId, InvoiceItem item, String agencyName) {
        String medicineName = cleanMedicineName(item.getProductName());
        if (medicineName == null || medicineName.isBlank()) return;
        Inventory inventory = inventoryRepository.findByShopIdAndMedicineNameIgnoreCase(shopId, medicineName)
                .orElseGet(() -> Inventory.builder().shopId(shopId).medicineName(medicineName)
                        .quantity(0).unit("strips").price(BigDecimal.ZERO).build());
        inventoryStockService.receiveStock(inventory, item, agencyName);
        inventoryRepository.save(inventory);
    }

    private Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> source)) return new LinkedHashMap<>();
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, entry) -> result.put(String.valueOf(key), entry));
        return result;
    }

    private List<Map<String, Object>> listOfMaps(Object value) {
        if (!(value instanceof List<?> source)) return List.of();
        return source.stream().map(this::mapValue).toList();
    }

    private String stringValue(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private BigDecimal decimalValue(Object value) {
        try { return new BigDecimal(stringValue(value).replaceAll("[^0-9.-]", "")); }
        catch (NumberFormatException ignored) { return BigDecimal.ZERO; }
    }
    private int integerValue(Object value, int fallback) {
        try { return Integer.parseInt(stringValue(value).replaceAll("[^0-9-]", "")); }
        catch (NumberFormatException ignored) { return fallback; }
    }
    private LocalDate parseInvoiceDate(String value) {
        LocalDate date = parseInvoiceDateOrNull(value);
        if (date == null) throw new IllegalArgumentException("OCR did not produce a valid invoice date.");
        return date;
    }
    private LocalDate parseInvoiceDateOrNull(String value) {
        if (value == null || value.isBlank()) return null;
        for (DateTimeFormatter formatter : List.of(DateTimeFormatter.ofPattern("dd-MM-uuuu"), DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("dd/MM/uuuu"))) {
            try { return LocalDate.parse(value.trim(), formatter); } catch (DateTimeParseException ignored) { }
        }
        return null;
    }
    private LocalDate parseExpiryDate(String value) {
        try {
            String[] parts = value.trim().split("/");
            if (parts.length != 2) return null;
            int year = Integer.parseInt(parts[1]);
            if (year < 100) year += 2000;
            return YearMonth.of(year, Integer.parseInt(parts[0])).atEndOfMonth();
        } catch (Exception ignored) { return null; }
    }

    public BillScan processBillImage(Long shopId, MultipartFile file, Long agencyId, String agencyName) throws IOException {
        Path uploadPath = Paths.get(uploadDir);
        if (!uploadPath.isAbsolute()) {
            uploadPath = Paths.get(System.getProperty("user.dir"), uploadDir);
        }
        uploadPath = uploadPath.resolve("bills");
        Files.createDirectories(uploadPath);

        String originalFileName = file.getOriginalFilename();
        String safeName = originalFileName != null ? originalFileName.replaceAll("[^a-zA-Z0-9._-]", "_") : "bill";
        String fileName = UUID.randomUUID() + "_" + safeName;
        Path filePath = uploadPath.resolve(fileName);

        BufferedImage originalImage = ImageIO.read(file.getInputStream());
        if (originalImage == null) {
            throw new IOException("Unsupported image format. Please upload JPG, PNG, or WebP.");
        }
        String pngName = fileName.replaceAll("\\.[^.]+$", "") + ".png";
        Path pngPath = uploadPath.resolve(pngName);
        ImageIO.write(originalImage, "png", pngPath.toFile());

        BillScan scan = BillScan.builder()
                .shopId(shopId)
                .originalFileName(originalFileName)
                .imageUrl("/uploads/bills/" + pngName)
                .processingStatus(BillScan.ProcessingStatus.PROCESSING)
                .build();
        billScanRepository.save(scan);

        try {
            // Step 1: Detect and crop bill
            BillDetectionService.DetectedBill detected = billDetectionService.detectAndCrop(originalImage);
            if (detected.cropped() != null) {
                String croppedName = UUID.randomUUID() + "_cropped.png";
                Path croppedPath = uploadPath.resolve(croppedName);
                ImageIO.write(detected.cropped(), "png", croppedPath.toFile());
                scan.setCroppedImageUrl("/uploads/bills/" + croppedName);
                scan.setDetectionBounds(toJson(detected.corners()));
                scan.setExtractionConfidence(BigDecimal.valueOf(detected.confidence()));

                List<String> steps = new ArrayList<>();
                steps.add("Bill detected and cropped (confidence: " + String.format("%.0f%%", detected.confidence() * 100) + ")");
                scan.setProcessingSteps(toJson(steps));
            }

            // Step 2: Enhance the corrected bill (or the original when no reliable
            // quadrilateral was found). OCR must receive this image, never the
            // unprocessed camera upload.
            BufferedImage imageForOCR = detected.cropped() != null ? detected.cropped() : originalImage;
            BufferedImage enhanced = billDetectionService.preprocess(imageForOCR);
            if (enhanced != null) {
                String enhancedName = UUID.randomUUID() + "_enhanced.png";
                Path enhancedPath = uploadPath.resolve(enhancedName);
                ImageIO.write(enhanced, "png", enhancedPath.toFile());
                scan.setEnhancedImageUrl("/uploads/bills/" + enhancedName);
                List<String> steps = new ArrayList<>();
                steps.add("Image enhanced (denoised, contrast adjusted, sharpened)");
                scan.setProcessingSteps(toJson(steps));
                imageForOCR = enhanced;
            }

            // Step 3: Extract invoice data using OCR + Document Understanding
            final BufferedImage ocrReadyImage = imageForOCR;
            Optional<DocTrOcrClient.OcrDocument> docTrResult = docTrOcrClient.extract(
                    multipartFileFromBufferedImage(ocrReadyImage, "processed-bill.png")
            );
            InvoiceExtractionService.ExtractionResult extractionResult = docTrResult
                    .map(result -> invoiceExtractionService.extractInvoiceText(result.text(), shopId, agencyId, agencyName))
                    .orElseGet(() -> {
                        try {
                            return invoiceExtractionService.extractInvoice(
                                    multipartFileFromBufferedImage(ocrReadyImage, "processed.png"),
                                    shopId, agencyId, agencyName
                            );
                        } catch (Exception error) {
                            throw new IllegalStateException(error);
                        }
                    });

            scan.setExtractedText(extractionResult.getRawText());
            scan.setParsedItems(toJsonString(extractionResult.getItems()));
            scan.setExtractionConfidence(extractionResult.getConfidence());
            scan.setProcessingStatus(BillScan.ProcessingStatus.COMPLETED);
            billScanRepository.save(scan);

            Invoice savedInvoice = invoiceRepository.save(extractionResult.getInvoice());
            scan.setExtractedInvoice(savedInvoice);

            for (InvoiceItem item : extractionResult.getItems()) {
                item.setInvoiceId(savedInvoice.getId());
                item.setShopId(shopId);
                invoiceItemRepository.save(item);
            }

            // Save dynamic attributes
            Map<String, Object> extraAttrs = new HashMap<>();
            extraAttrs.put("ocr_text_length", extractionResult.getRawText().length());
            if (extractionResult.getMetadata() != null) {
                extraAttrs.putAll(extractionResult.getMetadata());
            }
            attributeNormalizationService.normalizeAndSave(savedInvoice.getId(), extraAttrs);

            // Create agency bill
            createAgencyBillFromScan(shopId, savedInvoice.getAgencyId(), savedInvoice.getAgencyName(),
                    savedInvoice.getTotalAmount(), extractionResult.getItems(), "/uploads/bills/" + pngName,
                    savedInvoice.getId(), savedInvoice.getInvoiceDate());

            // Update inventory
            for (InvoiceItem item : extractionResult.getItems()) {
                updateInventory(shopId, item, savedInvoice.getAgencyName());
            }
        } catch (Exception e) {
            scan.setProcessingStatus(BillScan.ProcessingStatus.FAILED);
            scan.setProcessingError(e.getMessage());
            billScanRepository.save(scan);
            throw new IOException("Scan failed: " + e.getMessage(), e);
        }

        return scan;
    }

    private MultipartFile multipartFileFromBufferedImage(BufferedImage image, String filename) throws IOException {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", baos);
        byte[] bytes = baos.toByteArray();
        return new MultipartFile() {
            @Override public String getName() { return "file"; }
            @Override public String getOriginalFilename() { return filename; }
            @Override public String getContentType() { return "image/png"; }
            @Override public boolean isEmpty() { return bytes.length == 0; }
            @Override public long getSize() { return bytes.length; }
            @Override public byte[] getBytes() { return bytes; }
            @Override public java.io.InputStream getInputStream() { return new java.io.ByteArrayInputStream(bytes); }
            @Override public void transferTo(java.io.File dest) throws IOException { Files.write(dest.toPath(), bytes); }
        };
    }

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

    private void createAgencyBillFromScan(Long shopId, Long agencyId, String agencyName,
                                          BigDecimal invoiceTotal, List<InvoiceItem> items, String billPhoto,
                                          Long invoiceId, LocalDate invoiceDate) {
        BigDecimal total = invoiceTotal != null && invoiceTotal.signum() > 0
                ? invoiceTotal
                : items.stream()
                        .map(InvoiceItem::getAmount)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        LocalDateTime now = LocalDateTime.now();
        AgencyBill bill = AgencyBill.builder()
                .agencyId(agencyId)
                .agencyName(agencyName)
                .shopId(shopId)
                .invoiceId(invoiceId)
                .shopName(shopRepository.findById(shopId).map(Shop::getName).orElse("Unknown"))
                .totalAmount(total)
                .billPhoto(billPhoto)
                .itemsJson(toJsonString(items))
                .paymentStatus(AgencyBill.PaymentStatus.PENDING)
                .billDate((invoiceDate == null ? now.toLocalDate() : invoiceDate).atStartOfDay())
                .createdAt(now)
                .build();
        agencyBillRepository.save(bill);
    }

    private String toJsonString(Object obj) {
        try { return objectMapper.writeValueAsString(obj); }
        catch (Exception e) { throw new IllegalStateException("Could not serialize invoice data", e); }
    }

    private String toJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); } catch (Exception e) { return "{}"; }
    }

    public List<BillScan> getScansByShop(Long shopId) {
        return billScanRepository.findByShopId(shopId);
    }

    public List<Invoice> getInvoicesByShop(Long shopId) {
        return invoiceRepository.findByShopId(shopId);
    }

    public List<AgencyBill> getAgencyBillsByShop(Long shopId) {
        return agencyBillRepository.findByShopId(shopId).stream().map(bill -> {
            // Compatibility for older agency_bills written before itemsJson
            // supported Java time values. This is display-only: canonical
            // items remain the existing invoice_items rows and stock is never
            // re-applied.
            if (isEmptyItemJson(bill.getItemsJson())) {
                invoiceRepository.findTopByShopIdAndAgencyIdAndTotalAmountOrderByCreatedAtDesc(
                                bill.getShopId(), bill.getAgencyId(), bill.getTotalAmount())
                        .ifPresent(invoice -> {
                            List<InvoiceItem> invoiceItems = invoiceItemRepository.findByInvoiceId(invoice.getId());
                            if (!invoiceItems.isEmpty()) {
                                bill.setInvoiceId(invoice.getId());
                                if (invoice.getInvoiceDate() != null) {
                                    bill.setBillDate(invoice.getInvoiceDate().atStartOfDay());
                                }
                                bill.setItemsJson(toJsonString(invoiceItems));
                            }
                        });
            }
            return bill;
        }).toList();
    }

    private boolean isEmptyItemJson(String json) {
        return json == null || json.isBlank() || "[]".equals(json.trim());
    }

    public Invoice getInvoiceById(Long invoiceId) {
        return invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found: " + invoiceId));
    }

    @Transactional
    public List<Inventory> getInventoryByShop(Long shopId) {
        List<Inventory> inventory = inventoryRepository.findByShopId(shopId);
        inventory.forEach(inventoryStockService::hydrateLegacyInventory);
        return inventory;
    }

    public List<Inventory> searchInventory(Long shopId, String query) {
        if (query == null || query.isBlank()) return List.of();
        List<Inventory> inventory = inventoryRepository.findByShopIdAndMedicineNameContainingIgnoreCase(shopId, query);
        inventory.forEach(inventoryStockService::hydrateLegacyInventory);
        return inventory;
    }

    /** Deletes only current physical stock; purchase history and sources remain intact. */
    @Transactional
    public long clearInventoryForShop(Long shopId) {
        return inventoryRepository.deleteByShopId(shopId);
    }

    public Map<String, List<String>> getInventorySources(Long shopId) {
        List<Invoice> invoices = invoiceRepository.findByShopId(shopId);
        Map<String, Set<String>> sourcesMap = new HashMap<>();
        for (Invoice invDoc : invoices) {
            String agency = invDoc.getAgencyName();
            if (agency == null || agency.isBlank()) continue;
            List<InvoiceItem> items = invoiceItemRepository.findByInvoiceId(invDoc.getId());
            for (InvoiceItem item : items) {
                if (item.getProductName() != null && !item.getProductName().isBlank()) {
                    String cleanName = cleanMedicineName(item.getProductName());
                    String key = cleanName != null ? cleanName.toLowerCase() : item.getProductName().trim().toLowerCase();
                    sourcesMap.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(agency);
                }
            }
        }
        Map<String, List<String>> result = new HashMap<>();
        sourcesMap.forEach((k, v) -> result.put(k, new ArrayList<>(v)));
        return result;
    }

    public List<Inventory> getInventoryByShopAndAgency(Long shopId, Long agencyId) {
        List<Invoice> invoices = invoiceRepository.findByShopIdAndAgencyId(shopId, agencyId);
        Set<String> medicineNames = invoices.stream()
                .flatMap(inv -> invoiceItemRepository.findByInvoiceId(inv.getId()).stream())
                .map(InvoiceItem::getProductName)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        return inventoryRepository.findByShopId(shopId).stream()
                .filter(inv -> medicineNames.contains(inv.getMedicineName()))
                .collect(Collectors.toList());
    }

    public Map<String, List<Inventory>> getInventoryGroupedByAgency(Long shopId) {
        List<Inventory> allInventory = inventoryRepository.findByShopId(shopId);
        List<Invoice> invoices = invoiceRepository.findByShopId(shopId);
        Map<Long, String> agencyNames = invoices.stream()
                .collect(Collectors.toMap(Invoice::getAgencyId, Invoice::getAgencyName, (a, b) -> a));
        Map<String, List<Inventory>> grouped = new HashMap<>();
        for (Inventory inv : allInventory) {
            String agencyName = "Other";
            for (Invoice invDoc : invoices) {
                List<InvoiceItem> items = invoiceItemRepository.findByInvoiceId(invDoc.getId());
                if (items.stream().anyMatch(item -> item.getProductName() != null && item.getProductName().equalsIgnoreCase(inv.getMedicineName()))) {
                    agencyName = agencyNames.getOrDefault(invDoc.getAgencyId(), "Other");
                    break;
                }
            }
            grouped.computeIfAbsent(agencyName, k -> new ArrayList<>()).add(inv);
        }
        return grouped;
    }

    public void verifyInvoice(Long invoiceId, Map<String, Object> verifiedData) {
        Invoice invoice = getInvoiceById(invoiceId);
        invoice.setIsVerified(true);
        invoice.setVerifiedAt(LocalDateTime.now());
        invoice.setConfidenceScore(BigDecimal.valueOf(0.95));

        if (verifiedData.containsKey("invoiceNo")) invoice.setInvoiceNo((String) verifiedData.get("invoiceNo"));
        if (verifiedData.containsKey("invoiceDate")) invoice.setInvoiceDate(LocalDate.parse((String) verifiedData.get("invoiceDate")));
        if (verifiedData.containsKey("totalAmount")) invoice.setTotalAmount(new BigDecimal(verifiedData.get("totalAmount").toString()));
        if (verifiedData.containsKey("gstNumber")) invoice.setGstNumber((String) verifiedData.get("gstNumber"));
        if (verifiedData.containsKey("agencyName")) invoice.setAgencyName((String) verifiedData.get("agencyName"));

        invoiceRepository.save(invoice);
    }
}
