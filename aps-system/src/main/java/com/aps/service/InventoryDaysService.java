package com.aps.service;

import com.aps.entity.InventoryDays;
import com.aps.repository.InventoryDaysRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class InventoryDaysService {

    @Autowired
    private InventoryDaysRepository repository;

    public List<InventoryDays> findAll() {
        return repository.findAll();
    }

    public InventoryDays findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("InventoryDays not found: " + id));
    }

    public InventoryDays save(InventoryDays entity) {
        return repository.save(entity);
    }

    public InventoryDays update(Long id, InventoryDays entity) {
        InventoryDays existing = findById(id);
        existing.setItemCode(entity.getItemCode());
        existing.setSafetyDays(entity.getSafetyDays());
        existing.setMaxDays(entity.getMaxDays());
        return repository.save(existing);
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }

    public List<InventoryDays> saveAll(List<InventoryDays> list) {
        return repository.saveAll(list);
    }
}
