package com.example.alert.repo;

import com.example.alert.model.Alert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface AlertRepository extends JpaRepository<Alert, Long> {
  @Query(value = "SELECT * FROM alerts ORDER BY id DESC LIMIT 30", nativeQuery = true)
  List<Alert> recent30();
}
