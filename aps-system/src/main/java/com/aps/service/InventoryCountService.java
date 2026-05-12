package com.aps.service;

import com.aps.entity.InventoryCount;
import com.aps.repository.InventoryCountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class InventoryCountService {

    @Autowired
    private InventoryCountRepository repository;

    public List<InventoryCount> findAll() {
        return repository.findAll();
    }

    public InventoryCount findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("InventoryCount not found: " + id));
    }

    public InventoryCount save(InventoryCount entity) {
        return repository.save(entity);
    }

    public InventoryCount update(Long id, InventoryCount entity) {
        InventoryCount existing = findById(id);
        existing.setItemCode(entity.getItemCode());
        existing.setYearMonth(entity.getYearMonth());
        existing.setAvailableQty(entity.getAvailableQty());
        existing.setVersion(entity.getVersion());
        return repository.save(existing);
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }

    public List<InventoryCount> saveAll(List<InventoryCount> list) {
        return repository.saveAll(list);
    }
}
