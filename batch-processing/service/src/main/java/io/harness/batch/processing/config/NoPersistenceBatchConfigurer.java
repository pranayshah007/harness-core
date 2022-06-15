/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.config;

import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.DefaultBatchConfigurer;
import org.springframework.stereotype.Component;

/**
 * To use Map based JobRepository (In memory)
 */
@Slf4j
@Component
public class NoPersistenceBatchConfigurer extends DefaultBatchConfigurer {
  @Override
  public void setDataSource(DataSource dataSource) {
    log.debug("Using in memory job repository");
  }
}
