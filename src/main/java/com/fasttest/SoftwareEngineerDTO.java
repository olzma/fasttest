package com.fasttest;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SoftwareEngineerDTO {
  private Integer id;
  private String name;
  private String techStack;

  public SoftwareEngineerDTO() {}

  // Constructor, getters, setters
  public SoftwareEngineerDTO(SoftwareEngineer engineer) {
    this.id = engineer.getId();
    this.name = engineer.getName();
    this.techStack = engineer.getTechStack();
    // Only fields you want to expose
  }

  public Integer getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getTechStack() {
    return techStack;
  }

  public void setId(Integer id) {
    this.id = id;
  }

  public void setName(String name) {
    this.name = name;
  }

  public void setTechStack(String techStack) {
    this.techStack = techStack;
  }
}
