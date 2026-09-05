package com.wysjwxm;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 转账系统（行内转账）启动入口。
 */
@SpringBootApplication
@MapperScan("com.wysjwxm.infrastructure.persistence.mybatis")
public class TransferSysApplication {

    public static void main(String[] args) {
        SpringApplication.run(TransferSysApplication.class, args);
    }
}
