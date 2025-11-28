package com.example.alert.repo;

import com.example.alert.model.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface EventRepository extends JpaRepository<Event, Long> {
  @Query(value = "SELECT * FROM events ORDER BY id DESC LIMIT 30", nativeQuery = true)
  List<Event> recent30();
}
