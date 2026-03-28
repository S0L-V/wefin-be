package com.solv.wefin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class WefinApplication {

	public static void main(String[] args) {
		SpringApplication.run(WefinApplication.class, args);
	}

}
