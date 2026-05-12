package com.aps.service;

import com.aps.entity.SafetyStock;
import com.aps.repository.SafetyStockRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class SafetyStockService {

    @Autowired
    private SafetyStockRepository repository;

    public List<SafetyStock> findAll() { return repository.findAll(); }
    public SafetyStock findById(Long id) { return repository.findById(id).orElseThrow(); }
    public SafetyStock save(SafetyStock e) { return repository.save(e); }
    public List<SafetyStock> saveAll(List<SafetyStock> list) { return repository.saveAll(list); }

    public SafetyStock update(Long id, SafetyStock e) {
        e.setId(id);
        return repository.save(e);
    }

    public void delete(Long id) { repository.deleteById(id); }
}
