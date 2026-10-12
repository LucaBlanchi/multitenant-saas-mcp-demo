package com.acme.tenancy.repositories;
import com.acme.tenancy.entities.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface UserRepository extends JpaRepository<UserAccount, UUID> { Optional<UserAccount> findByEmail(String email); boolean existsByEmail(String email); }
