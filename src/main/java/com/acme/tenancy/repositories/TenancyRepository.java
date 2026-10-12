package com.acme.tenancy.repositories;
import com.acme.tenancy.entities.Tenancy;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface TenancyRepository extends JpaRepository<Tenancy, UUID> { Optional<Tenancy> findBySlug(String slug); List<Tenancy> findAllByOrderByNameAsc(); }
