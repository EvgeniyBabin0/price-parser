package com.example.priceparser.repository;

import com.example.priceparser.model.PriceRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceRecordRepository extends JpaRepository<PriceRecord, Long> {
}
