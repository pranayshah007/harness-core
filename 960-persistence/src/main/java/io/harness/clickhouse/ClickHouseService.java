package io.harness.clickhouse;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Properties;

public interface ClickHouseService {
  Connection getConnection() throws SQLException;
  int getCountOfRowsOfQueryResult(String query, String url) throws SQLException;
  public List<String> executeClickHouseQuery(String query, boolean returnResult) throws Exception;
}
