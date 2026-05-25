package com.aps.service;

import com.aps.entity.PartMaster;
import com.aps.repository.PartMasterRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class PartMasterService {

    @Autowired
    private PartMasterRepository repository;

    public List<PartMaster> findAll() {
        return repository.findAll();
    }

    public PartMaster findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("PartMaster not found: " + id));
    }

    public PartMaster findByPartNo(String partNo) {
        return repository.findByPartNo(partNo)
                .orElseThrow(() -> new IllegalArgumentException("PartMaster not found: " + partNo));
    }

    public PartMaster save(PartMaster entity) {
        if (repository.existsByPartNo(entity.getPartNo())) {
            throw new IllegalArgumentException("partNo already exists: " + entity.getPartNo());
        }
        return repository.save(entity);
    }

    public PartMaster update(Long id, PartMaster entity) {
        PartMaster existing = findById(id);
        if (!existing.getPartNo().equals(entity.getPartNo()) && repository.existsByPartNo(entity.getPartNo())) {
            throw new IllegalArgumentException("partNo already exists: " + entity.getPartNo());
        }
        existing.setPartNo(entity.getPartNo());
        existing.setProductName(entity.getProductName());
        existing.setProductNo(entity.getProductNo());
        existing.setProjectName(entity.getProjectName());
        return repository.save(existing);
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }

    public List<PartMaster> saveAllUpsert(List<PartMaster> list) {
        if (list == null || list.isEmpty()) {
            return List.of();
        }

        List<String> partNos = list.stream()
                .map(PartMaster::getPartNo)
                .collect(Collectors.toList());

        List<PartMaster> existingList = repository.findByPartNoIn(partNos);
        var existingMap = existingList.stream()
                .collect(Collectors.toMap(PartMaster::getPartNo, partMaster -> partMaster));

        List<PartMaster> merged = list.stream().map(item -> {
            PartMaster existing = existingMap.get(item.getPartNo());
            if (existing == null) {
                return item;
            }
            existing.setProductName(item.getProductName());
            existing.setProductNo(item.getProductNo());
            existing.setProjectName(item.getProjectName());
            return existing;
        }).collect(Collectors.toList());

        return repository.saveAll(merged);
    }
}
