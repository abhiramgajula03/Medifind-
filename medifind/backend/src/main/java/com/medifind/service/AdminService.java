package com.medifind.service;

import com.medifind.model.AttributeRegistry;
import com.medifind.repository.AttributeRegistryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final AttributeRegistryRepository attributeRegistryRepository;

    public List<AttributeRegistry> getAllAttributes() {
        return attributeRegistryRepository.findAll();
    }

    public AttributeRegistry registerAttribute(AttributeRegistry registry) {
        return attributeRegistryRepository.save(registry);
    }
}
