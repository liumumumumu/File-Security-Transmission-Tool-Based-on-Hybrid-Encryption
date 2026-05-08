package com;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FileSecurityTransmissionApplication
{
    public static void main(String[] args)
    {
        SpringApplication.run(FileSecurityTransmissionApplication.class, args);
    }
}
