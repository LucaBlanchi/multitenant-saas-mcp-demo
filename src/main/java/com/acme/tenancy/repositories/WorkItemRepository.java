package com.acme.tenancy.repositories;
import com.acme.tenancy.entities.WorkItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface WorkItemRepository extends JpaRepository<WorkItem, UUID> { List<WorkItem> findAllByTenancyIdOrderByCreatedAtDesc(UUID tenancyId); Optional<WorkItem> findByIdAndTenancyId(UUID id, UUID tenancyId); long countByTenancyId(UUID tenancyId); }
