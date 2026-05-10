package com.aps.service;

import com.aps.entity.OperatingDays;
import com.aps.repository.OperatingDaysRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class OperatingDaysService {

    @Autowired
    private OperatingDaysRepository repository;

    public List<OperatingDays> findAll() {
        return repository.findAll();
    }

    public OperatingDays findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("OperatingDays not found: " + id));
    }

    public OperatingDays save(OperatingDays entity) {
        return repository.save(entity);
    }

    public OperatingDays update(Long id, OperatingDays entity) {
        OperatingDays existing = findById(id);
        existing.setYearMonth(entity.getYearMonth());
        existing.setDays(entity.getDays());
        return repository.save(existing);
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }

    public List<OperatingDays> saveAll(List<OperatingDays> list) {
        return repository.saveAll(list);
    }
}
