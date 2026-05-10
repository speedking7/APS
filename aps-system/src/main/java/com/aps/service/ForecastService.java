package com.aps.service;

import com.aps.entity.Forecast;
import com.aps.repository.ForecastRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class ForecastService {

    @Autowired
    private ForecastRepository repository;

    public List<Forecast> findAll() {
        return repository.findAll();
    }

    public Forecast findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Forecast not found: " + id));
    }

    public Forecast save(Forecast entity) {
        return repository.save(entity);
    }

    public Forecast update(Long id, Forecast entity) {
        Forecast existing = findById(id);
        existing.setItemCode(entity.getItemCode());
        existing.setYearMonth(entity.getYearMonth());
        existing.setQuantity(entity.getQuantity());
        existing.setDeleteFlag(entity.getDeleteFlag());
        return repository.save(existing);
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }

    public List<Forecast> saveAll(List<Forecast> list) {
        return repository.saveAll(list);
    }
}
