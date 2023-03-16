/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.executor.config;

import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class ConfigurationProvider {
  private static io.harness.delegate.executor.config.Configuration configuration;

  public static io.harness.delegate.executor.config.Configuration getExecutorConfiguration() {
    if (!Objects.isNull(configuration)) {
      return configuration;
    }
    var configurationBuilder = Configuration.builder();
    if (StringUtils.isNotBlank(System.getenv(Env.TASK_DATA_PATH.name()))) {
      configurationBuilder.taskInputPath(System.getenv(Env.TASK_DATA_PATH.name()));
    }
    if (Boolean.parseBoolean(System.getenv(Env.SHOULD_SEND_RESPONSE.name()))) {
      configurationBuilder.shouldSendResponse(true);
    }
    return configurationBuilder.build();
  }
}
