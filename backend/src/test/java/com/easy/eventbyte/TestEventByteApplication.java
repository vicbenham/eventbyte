package com.easy.eventbyte;

import org.springframework.boot.SpringApplication;

public class TestEventByteApplication {

    public static void main(String[] args) {
        SpringApplication.from(EventByteApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
