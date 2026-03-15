package com.flashnote;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.flashnote")
public class FlashNoteApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlashNoteApplication.class, args);
    }
}
