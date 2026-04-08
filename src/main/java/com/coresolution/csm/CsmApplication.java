package com.coresolution.csm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CsmApplication {

	public static void main(String[] args) {
		SpringApplication.run(CsmApplication.class, args);
	}

}
