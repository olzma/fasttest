package com.fasttest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
// @EntityScan("com.fasttest")
// Force rebuild comment - v2
public class SpringBootFasttestApplication {

  public static void main(String[] args) {

    SpringApplication.run(SpringBootFasttestApplication.class, args);
  }

  @GetMapping
  public String helloWorld() {
    return "Hello World Spring Boot!";
  }
}
