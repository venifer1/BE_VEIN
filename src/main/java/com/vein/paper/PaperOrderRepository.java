package com.vein.paper;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PaperOrderRepository extends JpaRepository<PaperOrder, Long> {

    List<PaperOrder> findTop20ByAccountIdOrderByCreatedAtDesc(Long accountId);
}
