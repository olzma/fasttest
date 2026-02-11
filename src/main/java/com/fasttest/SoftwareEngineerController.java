package com.fasttest;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/v1/engineers")
public class SoftwareEngineerController {

  @Autowired private final SoftwareEngineerService service;

  public SoftwareEngineerController(SoftwareEngineerService service) {
    this.service = service;
  }

  @GetMapping("dummy")
  public List<SoftwareEngineer> getEngineers() {
    return List.of(
        new SoftwareEngineer(1, "James", "Java, Spring"),
        new SoftwareEngineer(2, "Jamila", "Python, Java"),
        new SoftwareEngineer(3, "Charlie", "JavaScript"));
  }

  @GetMapping
  public List<SoftwareEngineerDTO> getAll() {
    return service.getAllSoftwareEngineer(); // Returns DTOs, not entities
  }

  @GetMapping("/{id}")
  public SoftwareEngineerDTO getById(@PathVariable Integer id) {
    return service.getSoftwareEngineerById(id);
  }

  @PostMapping
  public ResponseEntity<SoftwareEngineerDTO> createEngineer(
      @RequestBody SoftwareEngineerDTO engineerDTO) {
    SoftwareEngineerDTO created = service.createSoftwareEngineer(engineerDTO);
    return new ResponseEntity<>(created, HttpStatus.CREATED);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteEngineer(@PathVariable Integer id) {
    service.deleteSoftwareEngineerById(id);
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping
  public ResponseEntity<Void> deleteAllEngineers() {
    service.deleteAllSoftwareEngineers();
    return ResponseEntity.noContent().build();
  }
}
