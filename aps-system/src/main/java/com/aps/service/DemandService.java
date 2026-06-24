package com.aps.service;

import com.aps.entity.Demand;
import com.aps.repository.DemandRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class DemandService {

    @Autowired
    private DemandRepository repository;

    public List<Demand> findAll() { return repository.findAll(); }
    public Demand findById(Long id) { return repository.findById(id).orElseThrow(); }
    public Demand save(Demand e) {
        normalizeDemand(e);
        return repository.save(e);
    }
    public List<Demand> saveAll(List<Demand> list) {
        list.forEach(this::normalizeDemand);
        return repository.saveAll(list);
    }

    public Demand update(Long id, Demand e) {
        e.setId(id);
        normalizeDemand(e);
        return repository.save(e);
    }

    public void delete(Long id) { repository.deleteById(id); }

    private void normalizeDemand(Demand demand) {
        if (demand == null) {
            return;
        }
        double demandQty = demand.getDemandQty() != null ? demand.getDemandQty() : 0.0;
        double endingInventory = demand.getEndingInventory() != null ? demand.getEndingInventory() : 0.0;
        double minSafetyStock = demand.getMinSafetyStock() != null ? demand.getMinSafetyStock() : 0.0;
        demand.setNetDemand(Math.max(0.0, demandQty - endingInventory + minSafetyStock));
    }
}
