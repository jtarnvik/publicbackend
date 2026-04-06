package com.tarnvik.publicbackend.commuter.model.domain.repository;

import com.tarnvik.publicbackend.commuter.model.domain.entity.Statistic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface StatisticRepository extends JpaRepository<Statistic, Long> {
  Optional<Statistic> findByName(String name);

  @Modifying
  @Query("UPDATE Statistic s SET s.counter = s.counter + 1 WHERE s.name = :name")
  void increment(@Param("name") String name);
}
