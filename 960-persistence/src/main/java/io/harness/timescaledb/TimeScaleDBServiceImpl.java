/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.timescaledb;

import static java.lang.String.format;
import static java.time.Duration.ofSeconds;

import io.harness.annotations.retry.RetryOnException;
import io.harness.health.HealthException;
import io.harness.timescaledb.TimeScaleDBConfig.TimeScaleDBConfigFields;

import com.google.common.base.Strings;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.jayway.jsonpath.internal.Utils;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.dbcp.BasicDataSource;

@Singleton
@Slf4j
public class TimeScaleDBServiceImpl implements TimeScaleDBService {
  private TimeScaleDBConfig timeScaleDBConfig;
  private boolean validDB;
  private BasicDataSource ds = new BasicDataSource();

  public TimeScaleDBServiceImpl(@Named("TimeScaleDBConfig") TimeScaleDBConfig timeScaleDBConfig) {
    this.timeScaleDBConfig = timeScaleDBConfig;
    initializeDB(); // Testing aop annotation
  }

  private void initializeDB() {
    log.info("Initializing TimeScaleDB");
    if (!isValid(timeScaleDBConfig)) {
      validDB = false;
      return;
    }

    Properties dbProperties = new Properties();
    dbProperties.put(TimeScaleDBConfigFields.connectTimeout, String.valueOf(timeScaleDBConfig.getConnectTimeout()));
    dbProperties.put(TimeScaleDBConfigFields.socketTimeout, String.valueOf(timeScaleDBConfig.getSocketTimeout()));
    dbProperties.put(
        TimeScaleDBConfigFields.logUnclosedConnections, String.valueOf(timeScaleDBConfig.isLogUnclosedConnections()));
    if (!Utils.isEmpty(timeScaleDBConfig.getTimescaledbUsername())) {
      dbProperties.put("user", timeScaleDBConfig.getTimescaledbUsername());
    }
    if (!Utils.isEmpty(timeScaleDBConfig.getTimescaledbPassword())) {
      dbProperties.put("password", timeScaleDBConfig.getTimescaledbPassword());
    }

    try {
      createConnection(dbProperties);
    } catch (SQLException ex) {
      log.error("No Valid TimeScaleDB found", ex);
    }
  }

  @RetryOnException(retryCount = 4, sleepDurationInMilliseconds = 200, retryOn = IOException.class)
  public void createConnection(Properties dbProperties) throws SQLException {
    try (Connection connection = DriverManager.getConnection(timeScaleDBConfig.getTimescaledbUrl(), dbProperties);
         Statement st = connection.createStatement(); ResultSet rs = st.executeQuery("SELECT VERSION()")) {
      if (rs.next()) {
        log.info(rs.getString(1));
        initializeTimeScaleDB(timeScaleDBConfig);
        validDB = true;
      }
    }
  }

  @Override
  public TimeScaleDBConfig getTimeScaleDBConfig() {
    return timeScaleDBConfig;
  }

  private boolean isValid(TimeScaleDBConfig timeScaleDBConfig) {
    if (timeScaleDBConfig == null || Strings.isNullOrEmpty(timeScaleDBConfig.getTimescaledbUrl())) {
      return false;
    }
    return true;
  }

  private void initializeTimeScaleDB(TimeScaleDBConfig config) throws SQLException {
    log.info("Initializing TimeScaleDB extension");
    ds.setUrl(config.getTimescaledbUrl());
    ds.setUsername(config.getTimescaledbUsername());
    ds.setPassword(config.getTimescaledbPassword());
    ds.setMinIdle(0);
    ds.setMaxIdle(10);

    ds.addConnectionProperty(
        TimeScaleDBConfigFields.connectTimeout, String.valueOf(timeScaleDBConfig.getConnectTimeout()));
    ds.addConnectionProperty(
        TimeScaleDBConfigFields.socketTimeout, String.valueOf(timeScaleDBConfig.getSocketTimeout()));
    ds.addConnectionProperty(
        TimeScaleDBConfigFields.logUnclosedConnections, String.valueOf(timeScaleDBConfig.isLogUnclosedConnections()));
    if (!Utils.isEmpty(timeScaleDBConfig.getTimescaledbUsername())) {
      ds.addConnectionProperty("user", timeScaleDBConfig.getTimescaledbUsername());
    }
    if (!Utils.isEmpty(timeScaleDBConfig.getTimescaledbPassword())) {
      ds.addConnectionProperty("password", timeScaleDBConfig.getTimescaledbPassword());
    }

    try (Connection connection = ds.getConnection(); Statement statement = connection.createStatement()) {
      statement.execute("CREATE EXTENSION IF NOT EXISTS TIMESCALEDB CASCADE;");
      log.info("Completed initializing TimeScaleDB extension");
      statement.execute("CREATE EXTENSION IF NOT EXISTS hstore;");
      log.info("Completed initializing hstore extension");
    }
  }

  @Override
  public boolean isValid() {
    return validDB;
  }

  @Override
  public Connection getDBConnection() throws SQLException {
    if (!validDB) {
      throw new SQLException("Invalid timescale db.");
    }
    log.debug("Active connections : [{}],Idle connections : [{}]", ds.getNumActive(), ds.getNumIdle());
    return ds.getConnection();
  }

  @Override
  public Duration healthExpectedResponseTimeout() {
    return ofSeconds(20);
  }

  @Override
  public Duration healthValidFor() {
    return ofSeconds(5);
  }

  @Override
  public void isHealthy() {
    try (Connection connection = ds.getConnection(); Statement statement = connection.createStatement();
         ResultSet rs = statement.executeQuery("SELECT VERSION()");) {
      if (rs.next()) {
        log.info(rs.getString(1));
      }
      log.debug("Timescaledb Health Check - Passed,  Active connections : [{}],Idle connections : [{}]",
          ds.getNumActive(), ds.getNumIdle());
    } catch (Exception exception) {
      log.debug("Timescaledb Health Check - Failed,  Active connections : [{}],Idle connections : [{}]",
          ds.getNumActive(), ds.getNumIdle());
      throw new HealthException(
          format("Monitor %s did not respond on time. %s", this.getClass().getName(), exception.getMessage()),
          exception);
    }
  }
}
