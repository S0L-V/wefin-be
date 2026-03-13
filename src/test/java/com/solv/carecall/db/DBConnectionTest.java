package com.solv.carecall.db;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;

@SpringBootTest
class DbConnectionTest {

    @Autowired
    DataSource dataSource;

    @Test
    void dbConnectionTest() throws Exception {
        var connection = dataSource.getConnection();
        System.out.println("DB 연결 성공: " + connection.getMetaData().getURL());
    }
}