package com.coresolution.mediplat.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;

import com.zaxxer.hikari.HikariDataSource;

@Configuration
public class CounselManDatasourceConfig {

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties mediplatDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "dataSource", destroyMethod = "close")
    @Primary
    public DataSource mediplatDataSource(
            @Qualifier("mediplatDataSourceProperties") DataSourceProperties properties) {
        HikariDataSource dataSource = properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
        dataSource.setPoolName("mediplat-store");
        dataSource.setMaximumPoolSize(5);
        dataSource.setMinimumIdle(1);
        return dataSource;
    }

    @Bean
    @Primary
    public JdbcTemplate jdbcTemplate(@Qualifier("dataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean(name = "counselManDataSource", destroyMethod = "close")
    public DataSource counselManDataSource(
            @Value("${platform.counselman.datasource.url}") String url,
            @Value("${platform.counselman.datasource.username}") String username,
            @Value("${platform.counselman.datasource.password}") String password,
            @Value("${platform.counselman.datasource.driver-class-name:}") String driverClassName) {
        DataSourceBuilder<?> builder = DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .url(url)
                .username(username)
                .password(password);
        if (StringUtils.hasText(driverClassName)) {
            builder.driverClassName(driverClassName);
        }
        HikariDataSource dataSource = (HikariDataSource) builder.build();
        dataSource.setPoolName("mediplat-counselman");
        dataSource.setMaximumPoolSize(3);
        dataSource.setMinimumIdle(0);
        return dataSource;
    }

    @Bean(name = "counselManJdbcTemplate")
    public JdbcTemplate counselManJdbcTemplate(@Qualifier("counselManDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
