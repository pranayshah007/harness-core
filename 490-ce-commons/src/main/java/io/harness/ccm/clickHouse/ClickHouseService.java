/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.clickHouse;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

public interface ClickHouseService {
  Connection getConnection(String url) throws SQLException;
  Connection getConnection(String url, Properties properties) throws SQLException;
  int getCountOfRowsOfQueryResult(String query, String url) throws SQLException;
}
