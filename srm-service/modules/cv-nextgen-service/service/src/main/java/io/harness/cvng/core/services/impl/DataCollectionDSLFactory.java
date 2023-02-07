/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.core.entities.NextGenLogCVConfig;
import io.harness.cvng.exception.NotImplementedForHealthSourceException;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DataCollectionDSLFactory {
  private static final String SUMOLOGIC_LOG_DATACOLLECTION_FILE = "sumologic-log.datacollection";
  private static final String ELK_LOG_DATACOLLECTION_FILE = "elk-log-fetch-data.datacollection";

  public static String readLogDSL(DataSourceType dataSourceType) {
    // TODO dont read repeatedly and also move it from here
    if (dataSourceType == DataSourceType.SUMOLOGIC_LOG) {
      return readFile(SUMOLOGIC_LOG_DATACOLLECTION_FILE);
    } else if (dataSourceType == DataSourceType.ELASTICSEARCH) {
      return readFile(ELK_LOG_DATACOLLECTION_FILE);
    } else {
      throw new NotImplementedForHealthSourceException("Not Implemented.");
    }
  }

  private static String readFile(String fileName) {
    try {
      return Resources.toString(
          Objects.requireNonNull(NextGenLogCVConfig.class.getResource(fileName)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      log.error("Cannot read DSL {}", fileName);
      throw new RuntimeException(e);
    }
  }
}
