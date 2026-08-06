package com.xtu.homework;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.xtu.homework.dao")
public class HomeworkApplication {
    public static void main(String[] args) {
        SpringApplication.run(HomeworkApplication.class, args);
    }
}
