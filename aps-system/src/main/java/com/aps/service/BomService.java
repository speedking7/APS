package com.aps.service;

import com.aps.entity.Bom;
import com.aps.repository.BomRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class BomService {

    @Autowired
    private BomRepository repository;

    public List<Bom> findAll() {
        return repository.findAll();
    }

    public Bom findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Bom not found: " + id));
    }

    public Bom save(Bom entity) {
        return repository.save(entity);
    }

    public Bom update(Long id, Bom entity) {
        Bom existing = findById(id);
        existing.setRootProductCode(entity.getRootProductCode());
        existing.setParentCode(entity.getParentCode());
        existing.setChildCode(entity.getChildCode());
        existing.setUsageQty(entity.getUsageQty());
        existing.setProcess(entity.getProcess());
        existing.setEquipment(entity.getEquipment());
        existing.setManufacturingDepartment(entity.getManufacturingDepartment());
        existing.setManufacturingUnit(entity.getManufacturingUnit());
        existing.setMoldCavity(entity.getMoldCavity());
        existing.setCycleTime(entity.getCycleTime());
        existing.setStaffCount(entity.getStaffCount());
        existing.setTaktTime(entity.getTaktTime());
        existing.setScrapRate(entity.getScrapRate());
        existing.setPartAttribute(entity.getPartAttribute());
        existing.setVersion(entity.getVersion());
        return repository.save(existing);
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }

    public List<Bom> saveAll(List<Bom> list) {
        return repository.saveAll(list);
    }
}
