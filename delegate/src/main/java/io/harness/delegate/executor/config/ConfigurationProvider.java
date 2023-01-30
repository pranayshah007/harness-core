/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.executor.config;
import static java.nio.charset.StandardCharsets.UTF_8;

import io.harness.exception.WingsException;
import io.harness.serializer.YamlUtils;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

import javax.annotation.Nullable;

@Slf4j
public class ConfigurationProvider {
  private static io.harness.delegate.executor.config.Configuration configuration;

  private static final String CONFIG_PATH = "executor_config.yaml";

  public static io.harness.delegate.executor.config.Configuration getExecutorConfiguration(@Nullable String path) {
    log.info("Working Directory = " + System.getProperty("user.dir"));
    if (!Objects.isNull(configuration)) {
      return configuration;
    }
    final File configFile = new File(Objects.isNull(path) ? CONFIG_PATH : path);
    if (!configFile.exists()) {
      log.info("Runner config not exist, using default config settings.");
      return io.harness.delegate.executor.config.Configuration.builder().build();
    }
    try {
      return new YamlUtils().read(FileUtils.readFileToString(configFile, UTF_8), Configuration.class);
    } catch (final IOException e) {
      final String error = String.format("Unable to read the delegate executor config file %s", CONFIG_PATH, e);
      log.error(error);
      // TODO: define exceptions
      throw new WingsException(error);
    }
  }
}
