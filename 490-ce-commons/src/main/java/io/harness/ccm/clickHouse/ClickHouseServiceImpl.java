/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.clickHouse;

import java.sql.*;
import java.util.Properties;

public class ClickHouseServiceImpl implements ClickHouseService {
  private static final String TABLE_NAME = "ccm.unifiedTable";

  public Connection getConnection(String url) throws SQLException {
    return getConnection(url, new Properties());
  }

  public Connection getConnection(String url, Properties properties) throws SQLException {
    final Connection conn;
    conn = DriverManager.getConnection(url, properties);
    System.out.println("Connected to: " + conn.getMetaData().getURL());
    return conn;
  }

  @Override
  public int getCountOfRowsOfQueryResult(String query, String url) throws SQLException {
    try (Statement stmt = getConnection(url).createStatement()) {
      int count = 0;
      try (ResultSet rs = stmt.executeQuery(query)) {
        while (rs.next()) {
          count++;
        }
      }
      return count;
    }
  }
}
