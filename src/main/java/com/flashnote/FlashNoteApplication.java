package com.flashnote;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan({
        "com.flashnote.auth.mapper",
        "com.flashnote.collection.mapper",
        "com.flashnote.favorite.mapper",
        "com.flashnote.flashnote.mapper",
        "com.flashnote.message.mapper",
        "com.flashnote.user.mapper"
})
public class FlashNoteApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlashNoteApplication.class, args);
    }
}
