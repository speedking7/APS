package com.aps.service;

import com.aps.entity.ScrapRate;
import com.aps.repository.ScrapRateRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class ScrapRateService {

    @Autowired
    private ScrapRateRepository repository;

    public List<ScrapRate> findAll() {
        return repository.findAll();
    }

    public ScrapRate findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("ScrapRate not found: " + id));
    }

    public ScrapRate save(ScrapRate entity) {
        return repository.save(entity);
    }

    public ScrapRate update(Long id, ScrapRate entity) {
        ScrapRate existing = findById(id);
        existing.setItemCode(entity.getItemCode());
        existing.setScrapRate(entity.getScrapRate());
        return repository.save(existing);
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }

    public List<ScrapRate> saveAll(List<ScrapRate> list) {
        return repository.saveAll(list);
    }
}
