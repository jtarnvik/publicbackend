package com.tarnvik.publicbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PublicbackendApplication {
	public static void main(String[] args) {
		SpringApplication.run(PublicbackendApplication.class, args);
	}
}
