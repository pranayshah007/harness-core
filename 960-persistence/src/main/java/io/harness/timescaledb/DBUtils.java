/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.timescaledb;

import java.sql.ResultSet;
import java.sql.SQLException;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.jooq.exception.DataAccessException;

@UtilityClass
@Slf4j
public class DBUtils {
  public static void close(ResultSet resultSet) {
    try {
      if (resultSet != null && !resultSet.isClosed()) {
        resultSet.close();
      }
    } catch (SQLException e) {
      log.warn("Error while closing result set", e);
    }
  }

  public static boolean isConnectionError(DataAccessException ex) {
    if (ex.getMessage().contains("Error getting connection from data source")) {
      return true;
    }
    return false;
  }
}
