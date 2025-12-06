package com.neyma.userService.repository;

import com.neyma.userService.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);

    java.util.List<User> findTop20ByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(String name, String email);
}
