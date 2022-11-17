package io.harness.ccm.clickHouse;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class ClickHouseTest {

    private static final String TABLE_NAME = "ccm.unifiedTable";
    private static final String URL = "jdbc:ch://localhost:9000";

    private static Connection getConnection(String url) throws SQLException {
        return getConnection(url, new Properties());
    }

    private static Connection getConnection(String url, Properties properties) throws SQLException {
        final Connection conn;
        conn = DriverManager.getConnection(url, properties);
        System.out.println("Connected to: " + conn.getMetaData().getURL());
        return conn;
    }

    public static void main(String[] args) {
        // jdbc:ch:https://explorer@play.clickhouse.com:443
        // jdbc:ch:https://demo:demo@github.demo.trial.altinity.cloud
//        String url = System.getProperty("chUrl", "jdbc:ch://localhost");

        try (Connection conn = getConnection(URL)) {
            System.out.println("Connected" );
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
