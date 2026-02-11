package com.fasttest;

import jakarta.persistence.*;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter // ← Important!
@Setter
@Table(name = "software_engineer")
public class SoftwareEngineer {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  private String name;
  private String techStack;

  public SoftwareEngineer() {}

  public SoftwareEngineer(Integer id, String name, String techStack) {
    this.id = id;
    this.name = name;
    this.techStack = techStack;
  }

  public Integer getId() {
    return id;
  }

  public String getTechStack() {
    return techStack.toString();
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public void setTechStack(String techStack) {
    this.techStack = techStack;
  }

  public void setId(Integer id) {
    this.id = id;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    SoftwareEngineer that = (SoftwareEngineer) o;
    return Objects.equals(id, that.id)
        && Objects.equals(name, that.name)
        && Objects.equals(techStack, that.techStack);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, name, techStack);
  }
}
