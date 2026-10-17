package com.medifind.repository;

import com.medifind.model.AttributeRegistry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AttributeRegistryRepository extends JpaRepository<AttributeRegistry, Long> {
    Optional<AttributeRegistry> findByAttributeNameIgnoreCase(String attributeName);
    Optional<AttributeRegistry> findByCanonicalNameIgnoreCase(String canonicalName);
    boolean existsByAttributeNameIgnoreCase(String attributeName);
}
