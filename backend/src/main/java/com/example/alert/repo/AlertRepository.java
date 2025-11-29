package com.example.alert.repo;

import com.example.alert.model.Alert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface AlertRepository extends JpaRepository<Alert, Long> {
  @Query(value = "SELECT * FROM alerts ORDER BY id DESC LIMIT 30", nativeQuery = true)
  List<Alert> recent30();
  List<Alert> findByStatusAndExpiryAtLessThanEqual(String status, Long now);
  List<Alert> findByStatusAndConfirmStartsAtLessThanEqual(String status, Long now);
  Optional<Alert> findTopByStatusOrderByIdDesc(String status);
}
