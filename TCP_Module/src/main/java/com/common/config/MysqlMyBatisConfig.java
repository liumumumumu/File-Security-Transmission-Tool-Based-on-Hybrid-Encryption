package com.common.config;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;

/**
 * Author: LQH
 * Date: 2026-05-08
 * Purpose: 负责给MySQL传输记录Mapper配置MyBatis运行环境
 */
@Configuration
@Profile("server")
@MapperScan(
        basePackages = "com.persistence.local.mapper.transmissionRecord",
        sqlSessionFactoryRef = "mysqlSqlSessionFactory"
)
public class MysqlMyBatisConfig
{
    @Primary
    @Bean(name = "mysqlSqlSessionFactory")
    public SqlSessionFactory mysqlSqlSessionFactory(DataSource dataSource) throws Exception
    {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        return factoryBean.getObject();
    }

    @Primary
    @Bean(name = "mysqlSqlSessionTemplate")
    public SqlSessionTemplate mysqlSqlSessionTemplate(
            @Qualifier("mysqlSqlSessionFactory") SqlSessionFactory mysqlSqlSessionFactory
    )
    {
        return new SqlSessionTemplate(mysqlSqlSessionFactory);
    }
}
