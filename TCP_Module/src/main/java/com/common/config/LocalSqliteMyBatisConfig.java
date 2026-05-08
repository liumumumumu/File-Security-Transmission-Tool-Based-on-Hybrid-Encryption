package com.common.config;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.sqlite.SQLiteDataSource;
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
    @Bean
    public DataSource sqliteDataSource(LocalStorageProperties localStorageProperties) throws Exception
    {
        Path sqlitePath=Path.of(localStorageProperties.getSqlitePath()).toAbsolutePath();
        Path parent=sqlitePath.getParent();

        if (parent!=null)
        {
            Files.createDirectories(parent);
        }

        SQLiteDataSource dataSource=new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite"+sqlitePath);
        return dataSource;
    }

    @Bean
    public SqlSessionFactory sqlSessionFactory(DataSource sqliteDataSource)throws  Exception
    {
        SqlSessionFactoryBean sqlSessionFactoryBean=new SqlSessionFactoryBean();
        sqlSessionFactoryBean.setDataSource(sqliteDataSource);
        return sqlSessionFactoryBean.getObject();
    }

    @Bean
    public SqlSessionTemplate sqliteSqlSessionTemplate(SqlSessionFactory sqlSessionFactory)
    {
        return new SqlSessionTemplate(sqlSessionFactory);
    }
}
