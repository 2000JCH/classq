package org.classq;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ClassqApplication {

	public static void main(String[] args) {
		SpringApplication.run(ClassqApplication.class, args);
	}

}
