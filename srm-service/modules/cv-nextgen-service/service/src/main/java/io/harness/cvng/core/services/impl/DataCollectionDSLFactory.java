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

public class DataCollectionDSLFactory {
  public static String readLogDSL(DataSourceType dataSourceType) {
    // TODO dont read repeatedly and also move it from here
    if (dataSourceType == DataSourceType.SUMOLOGIC_LOG) {
      try {
        return Resources.toString(
            NextGenLogCVConfig.class.getResource("sumologic-log.datacollection"), StandardCharsets.UTF_8);
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
    } else {
      throw new NotImplementedForHealthSourceException("Not Implemented.");
    }
  }
}
