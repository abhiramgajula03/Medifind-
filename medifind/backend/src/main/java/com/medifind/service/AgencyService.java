package com.medifind.service;

import com.medifind.model.Agency;
import com.medifind.model.AgencyBill;
import com.medifind.repository.AgencyBillRepository;
import com.medifind.repository.AgencyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AgencyService {

    private final AgencyRepository agencyRepository;
    private final AgencyBillRepository agencyBillRepository;

    public Agency createAgency(Agency agency) {
        return agencyRepository.save(agency);
    }

    public List<Agency> getAllAgencies() {
        return agencyRepository.findAll();
    }

    public Agency getAgency(Long id) {
        return agencyRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Agency not found: " + id));
    }

    public Agency updateAgency(Long id, Agency updated) {
        Agency agency = getAgency(id);
        agency.setName(updated.getName());
        agency.setEmail(updated.getEmail());
        agency.setPhone(updated.getPhone());
        agency.setAddress(updated.getAddress());
        agency.setLicenseNumber(updated.getLicenseNumber());
        agency.setLicensePhoto(updated.getLicensePhoto());
        agency.setIsActive(updated.getIsActive() != null ? updated.getIsActive() : agency.getIsActive());
        return agencyRepository.save(agency);
    }

    public void deleteAgency(Long id) {
        agencyRepository.deleteById(id);
    }

    public List<AgencyBill> getAgencyBillsByShop(Long shopId) {
        return agencyBillRepository.findByShopId(shopId);
    }
}
