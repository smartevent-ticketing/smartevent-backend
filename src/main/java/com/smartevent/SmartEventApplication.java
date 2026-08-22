package com.smartevent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@SpringBootApplication
@EnableScheduling // 👈 BẮT BUỘC: Kích hoạt toàn bộ các tiến trình
public class SmartEventApplication {

	public static void main(String[] args) {
		SpringApplication.run(SmartEventApplication.class, args);
	}

}
