package com.acme.tenancy.repositories;
import com.acme.tenancy.entities.Membership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import java.util.*;
public interface MembershipRepository extends JpaRepository<Membership, UUID> {
 Optional<Membership> findByUserIdAndTenancyId(UUID userId, UUID tenancyId);
 @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"tenancy"})
 List<Membership> findByUserId(UUID userId);
 boolean existsByUserIdAndTenancyId(UUID userId, UUID tenancyId);
 @EntityGraph(attributePaths = {"user", "tenancy"})
 @Query("select m from Membership m order by m.tenancy.name asc, m.user.email asc")
 List<Membership> findAllForSuperadmin();
}
