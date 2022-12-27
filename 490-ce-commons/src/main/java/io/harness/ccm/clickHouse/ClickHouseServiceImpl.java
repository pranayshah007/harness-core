/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.clickHouse;

import com.clickhouse.jdbc.ClickHouseDataSource;
import lombok.extern.slf4j.Slf4j;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

@Slf4j
public class ClickHouseServiceImpl implements ClickHouseService {
  @Override
  public List<String> executeClickHouseQuery(String query, boolean returnResult) throws Exception {
    log.info(query);
    String url = "jdbc:ch://clickhouse-shard0-0.clickhouse-shard0.harness.svc.cluster.local:8123,clickhouse-shard0-1.clickhouse-shard0.harness.svc.cluster.local:8123";
    Properties properties = new Properties();
    ClickHouseDataSource dataSource = new ClickHouseDataSource(url, properties);
    try (Connection connection = dataSource.getConnection("default", "ItmTozDqu0");
         Statement statement = connection.createStatement();
         ResultSet resultSet = statement.executeQuery(query)) {
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
}
