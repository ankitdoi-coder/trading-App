package com.navrasa.binanceBackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import io.github.cdimascio.dotenv.Dotenv;

@SpringBootApplication
public class BinanceBackendApplication {

	public static void main(String[] args) {
		// Load the .env file and push variables into System Properties so Spring can
		// read them
		Dotenv.configure()
				.systemProperties()
				.ignoreIfMissing() // CRITICAL: Prevents crashing on Render where .env doesn't exist
				.load();

		SpringApplication.run(BinanceBackendApplication.class, args);
	}

}
