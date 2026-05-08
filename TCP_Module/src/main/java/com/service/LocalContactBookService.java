package com.service;

import com.common.config.LocalStorageProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

@Service
public class LocalContactBookService
{
    private final Path sqlitePath;
    private final String jdbcUrl;

    public LocalContactBookService(LocalStorageProperties localStorageProperties)
    {
        this.sqlitePath = Paths.get(localStorageProperties.getSqlitePath()).toAbsolutePath();
        this.jdbcUrl="jdbc:sqlite:"+sqlitePath;
    }

    @PostConstruct
    public void init() throws Exception
    {

    }






}
