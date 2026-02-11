package com.fasttest;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class SoftwareEngineerService {

  private final SoftwareEngineerRepository softwareEngineerRepository;

  public SoftwareEngineerService(SoftwareEngineerRepository softwareEngineerRepository) {

    this.softwareEngineerRepository = softwareEngineerRepository;
  }

  public List<SoftwareEngineerDTO> getAllSoftwareEngineer() {
    List<SoftwareEngineerDTO> myList =
        softwareEngineerRepository.findAll().stream()
            .map(SoftwareEngineerDTO::new)
            .collect(Collectors.toList());
    if (myList.isEmpty()) {
      throw new EngineerNotFoundException("No engineers found!");
    }
    return myList;
  }

  public SoftwareEngineerDTO getSoftwareEngineerById(Integer id) {
    SoftwareEngineer engineer =
        softwareEngineerRepository
            .findById(id)
            .orElseThrow(() -> new EngineerNotFoundException(id));
    return new SoftwareEngineerDTO(engineer);
  }

  public SoftwareEngineerDTO createSoftwareEngineer(SoftwareEngineerDTO engineerDTO) {
    // Convert DTO to Entity
    SoftwareEngineer engineer = new SoftwareEngineer();
    engineer.setName(engineerDTO.getName());
    engineer.setTechStack(engineerDTO.getTechStack());
    // Set other fields as needed

    // Save to database
    SoftwareEngineer saved = softwareEngineerRepository.save(engineer);

    // Convert Entity back to DTO and return
    return new SoftwareEngineerDTO(saved);
  }

  public void deleteSoftwareEngineerById(Integer id) {
    // Check if engineer exists before deleting
    if (!softwareEngineerRepository.existsById(id)) {
      throw new EngineerNotFoundException(id);
    }
    softwareEngineerRepository.deleteById(id);
  }

  public void deleteAllSoftwareEngineers() {
    softwareEngineerRepository.deleteAll();
  }
}
