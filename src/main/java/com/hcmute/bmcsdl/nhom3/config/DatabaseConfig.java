package com.hcmute.bmcsdl.nhom3.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
public class DatabaseConfig {

    private static final Map<String, DataSource> dataSources = new ConcurrentHashMap<>();

    @Bean
    public DataSource dataSource() {
        SingleConnectionDataSource ds = new SingleConnectionDataSource();
        ds.setDriverClassName("oracle.jdbc.OracleDriver");
        ds.setUrl("jdbc:oracle:thin:@localhost:1521/FREEPDB1");
        ds.setSuppressClose(true);
        return ds;
    }

    public static DataSource createDataSource(String username, String password) {
        if (username == null) return null;
        String key = username.toUpperCase();
        
        return dataSources.computeIfAbsent(key, k -> {
            SingleConnectionDataSource ds = new SingleConnectionDataSource();
            ds.setDriverClassName("oracle.jdbc.OracleDriver");
            ds.setUrl("jdbc:oracle:thin:@localhost:1521/FREEPDB1");
            ds.setUsername(username);
            ds.setPassword(password);
            // Quan trọng: suppressClose = true ngăn ứng dụng tự động đóng connection sau mỗi query,
            // giúp Oracle không bị spam LOGOFF/LOGON.
            ds.setSuppressClose(true);
            return ds;
        });
    }
}