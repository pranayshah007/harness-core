package io.harness.clickhouse;

import com.clickhouse.jdbc.ClickHouseDataSource;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class ClickHouseServiceImpl implements ClickHouseService {
  private ClickHouseConfig clickHouseConfig;
  String url = "jdbc:ch://127.0.0.1:8123";
  Properties properties = new Properties();

  public ClickHouseServiceImpl(@Named("ClickHouseConfig") ClickHouseConfig clickHouseConfig) {
    this.clickHouseConfig = clickHouseConfig;
  }

  @Override
  public Connection getConnection() throws SQLException {
    final Connection conn;
    ClickHouseDataSource dataSource = new ClickHouseDataSource(url, properties);
    log.info("Connected to: {}", url);
    return dataSource.getConnection();
  }

  @Override
  public int getCountOfRowsOfQueryResult(String query, String url) throws SQLException {
    //    try (Statement stmt = getConnection(url).createStatement()) {
    //      int count = 0;
    //      try (ResultSet rs = stmt.executeQuery(query)) {
    //        while (rs.next()) {
    //          count++;
    //        }
    //      }
    //      return count;
    //    }
    return 0;
  }

  @Override
  public List<String> executeClickHouseQuery(String query, boolean returnResult) throws Exception {
    log.info(query);
    Properties properties = new Properties();
    ClickHouseDataSource dataSource = new ClickHouseDataSource(url, properties);
    try (Connection connection = dataSource.getConnection("default", "");
         Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(query)) {
      if (returnResult) {
        List<String> output = new ArrayList<>();
        while (resultSet.next()) {
          output.add(resultSet.getString(1));
        }
        return output;
      }
    }
    return null;
  }

  public ClickHouseConfig getClickHouseConfig() {
    return clickHouseConfig;
  }
}