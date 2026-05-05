package com.ai_bus_fare_system.aibusfare.repository;

import com.ai_bus_fare_system.aibusfare.model.Trip;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TripRepository extends JpaRepository<Trip, Long> {
    List<Trip> findByUsernameOrderByCreatedAtDesc(String username);
    Optional<Trip> findTopByUsernameOrderByCreatedAtDesc(String username);
}
