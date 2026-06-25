package com.medifind.service;

import com.medifind.model.ShortageList;
import com.medifind.model.ShortageList.ShortageStatus;
import com.medifind.model.ShortageList.PaymentStatus;
import com.medifind.repository.ShortageListRepository;
import com.medifind.repository.ShopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ShortageListService {

    private final ShortageListRepository shortageListRepository;
    private final ShopRepository shopRepository;

    // Shopkeeper: Add medicine to shortage list (offline/walk-in customer)
    public ShortageList addShortageEntry(Long shopId, String medicineName,
                                          String customerName, String customerPhone,
                                          int qty, String notes) {
        ShortageList entry = ShortageList.builder()
                .shop(shopRepository.findById(shopId).orElseThrow())
                .medicineName(medicineName)
                .customerName(customerName)
                .customerPhone(customerPhone)
                .quantityNeeded(qty)
                .notes(notes)
                .status(ShortageStatus.PENDING)
                .paymentStatus(PaymentStatus.NONE)
                .build();
        return shortageListRepository.save(entry);
    }

    // Customer: Request medicine online (with advance payment intent)
    public ShortageList requestMedicineOnline(Long shopId, Long customerId,
                                               String customerName, String customerPhone,
                                               String medicineName, int qty,
                                               BigDecimal advanceAmount, String txnId) {
        ShortageList entry = ShortageList.builder()
                .shop(shopRepository.findById(shopId).orElseThrow())
                .medicineName(medicineName)
                .customerName(customerName)
                .customerPhone(customerPhone)
                .quantityNeeded(qty)
                .advancePaid(advanceAmount)
                .paymentStatus(advanceAmount.compareTo(BigDecimal.ZERO) > 0
                        ? PaymentStatus.PAID : PaymentStatus.NONE)
                .paymentTransactionId(txnId)
                .status(ShortageStatus.PENDING)
                .build();
        return shortageListRepository.save(entry);
    }

    // Shopkeeper: Get all shortage entries for their shop
    public List<ShortageList> getShortageList(Long shopId) {
        return shortageListRepository.findByShopId(shopId);
    }

    // Shopkeeper: Get pending entries only
    public List<ShortageList> getPendingShortages(Long shopId) {
        return shortageListRepository.findByShopIdAndStatus(shopId, ShortageStatus.PENDING);
    }

    // Shopkeeper: Update status (ORDERED → ARRIVED → DELIVERED / CANCELLED)
    public ShortageList updateStatus(Long entryId, ShortageStatus newStatus) {
        ShortageList entry = shortageListRepository.findById(entryId)
                .orElseThrow(() -> new RuntimeException("Shortage entry not found"));
        entry.setStatus(newStatus);

        // If cancelled and advance was paid, mark for refund
        if (newStatus == ShortageStatus.CANCELLED
                && entry.getPaymentStatus() == PaymentStatus.PAID) {
            entry.setPaymentStatus(PaymentStatus.REFUNDED);
        }

        return shortageListRepository.save(entry);
    }

    // Customer: View their own requests
    public List<ShortageList> getCustomerRequests(Long customerId) {
        return shortageListRepository.findByCustomerId(customerId);
    }

    // Shopkeeper: Export shortage list as CSV string (to send to distributor)
    public String exportShortageListAsCsv(Long shopId) {
        List<ShortageList> list = shortageListRepository
                .findByShopIdAndStatus(shopId, ShortageStatus.PENDING);

        StringBuilder csv = new StringBuilder();
        csv.append("Medicine Name,Quantity Needed,Customer Name,Customer Phone,Advance Paid,Notes\n");

        for (ShortageList entry : list) {
            csv.append(String.format("%s,%d,%s,%s,%.2f,%s\n",
                    entry.getMedicineName(),
                    entry.getQuantityNeeded(),
                    entry.getCustomerName() != null ? entry.getCustomerName() : "-",
                    entry.getCustomerPhone() != null ? entry.getCustomerPhone() : "-",
                    entry.getAdvancePaid(),
                    entry.getNotes() != null ? entry.getNotes() : ""
            ));
        }
        return csv.toString();
    }
}
