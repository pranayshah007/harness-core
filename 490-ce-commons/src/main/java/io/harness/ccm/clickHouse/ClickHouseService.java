/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.clickHouse;

import io.harness.ccm.commons.beans.config.ClickHouseConfig;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Properties;

public interface ClickHouseService {
  Connection getConnection(ClickHouseConfig clickHouseConfig) throws SQLException;
  Connection getConnection(ClickHouseConfig clickHouseConfig, Properties properties) throws SQLException;
  ResultSet getQueryResult(ClickHouseConfig clickHouseConfig, String query) throws SQLException;
  List<String> executeClickHouseQuery(ClickHouseConfig clickHouseConfig, String query, boolean returnResult)
      throws SQLException;
}