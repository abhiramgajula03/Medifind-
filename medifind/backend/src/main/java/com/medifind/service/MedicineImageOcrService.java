package com.medifind.service;

import com.medifind.model.Inventory;
import com.medifind.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Package-photo OCR is intentionally separate from invoice extraction and never parses invoice rows. */
@Service
@RequiredArgsConstructor
public class MedicineImageOcrService {
    private final InventoryRepository inventoryRepository;
    @Value("${tesseract.datapath:/usr/share/tesseract-ocr/4.00/tessdata}")
    private String tesseractDataPath;

    public List<Inventory> identify(MultipartFile upload, Long shopId) throws Exception {
        BufferedImage image = ImageIO.read(upload.getInputStream());
        if (image == null || image.getWidth() < 80 || image.getHeight() < 50 || image.getWidth() > 6000 || image.getHeight() > 6000)
            throw new IllegalArgumentException("Invalid image dimensions");
        ITesseract engine = new Tesseract();
        engine.setDatapath(tesseractDataPath);
        engine.setLanguage("eng");
        engine.setPageSegMode(11);
        String text = engine.doOCR(image);
        List<String> terms = Arrays.stream(text.split("\\R"))
                .map(line -> line.replaceAll("[^\\p{L}\\p{N} -]", " ").replaceAll("\\s+", " ").trim())
                .filter(line -> line.length() >= 3 && line.length() <= 80)
                .distinct().limit(30).toList();
        List<Inventory> inventory = inventoryRepository.findByShopId(shopId);
        Map<Long, Integer> scores = new HashMap<>();
        for (Inventory item : inventory) {
            String name = normalize(item.getMedicineName());
            for (String term : terms) {
                String candidate = normalize(term);
                if (candidate.length() >= 3 && (name.contains(candidate) || candidate.contains(name)))
                    scores.merge(item.getId(), candidate.length(), Math::max);
                else {
                    Set<String> tokens = Arrays.stream(candidate.split(" ")).filter(t -> t.length() > 2).collect(Collectors.toSet());
                    long overlap = Arrays.stream(name.split(" ")).filter(tokens::contains).count();
                    if (overlap > 0) scores.merge(item.getId(), (int) overlap * 3, Math::max);
                }
            }
        }
        return inventory.stream().filter(item -> scores.containsKey(item.getId()))
                .sorted(Comparator.comparingInt((Inventory item) -> scores.get(item.getId())).reversed())
                .limit(5).toList();
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }
}
