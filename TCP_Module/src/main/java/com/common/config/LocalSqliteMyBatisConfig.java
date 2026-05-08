package com.common.config;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Author: LQH
 * Date: 2026-05-08
 * Purpose: 负责给本地的SQLite单独配置一套MyBatis运行环境
 * 1.创建SQLite DataSource
 * 2.创建SQLite专用的SqlSessionFactory
 * 3.扫描SQLite Mapper
 *
 * */

@Configuration
@MapperScan(basePackages = "com.persistence.local.mapper.contactsRecord", sqlSessionFactoryRef = "sqliteSqlSessionFactory")
public class LocalSqliteMyBatisConfig
{
    private DataSource createSqliteDataSource(LocalStorageProperties localStorageProperties) throws Exception
    {
        Path sqlitePath=Path.of(localStorageProperties.getSqlitePath()).toAbsolutePath();
        Path parent=sqlitePath.getParent();

        if (parent!=null)
        {
            Files.createDirectories(parent);
        }

        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.sqlite.JDBC");
        dataSource.setUrl("jdbc:sqlite:"+sqlitePath);
        return dataSource;
    }

    @Bean(name = "sqliteSqlSessionFactory")
    public SqlSessionFactory sqlSessionFactory(LocalStorageProperties localStorageProperties)throws  Exception
    {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(createSqliteDataSource(localStorageProperties));
        return factoryBean.getObject();
    }

    @Bean(name = "sqliteSqlSessionTemplate")
    public SqlSessionTemplate sqliteSqlSessionTemplate(
            @Qualifier("sqliteSqlSessionFactory") SqlSessionFactory sqliteSqlSessionFactory
    )
    {
        return new SqlSessionTemplate(sqliteSqlSessionFactory);
    }
}
