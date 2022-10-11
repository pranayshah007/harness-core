/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.runner.config;
import static java.nio.charset.StandardCharsets.UTF_8;

import io.harness.exception.WingsException;
import io.harness.serializer.YamlUtils;

import com.google.inject.Provider;
import java.io.File;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

@Slf4j
@RequiredArgsConstructor
public class ConfigurationProvider implements Provider<Configuration> {
  private final String configFileName;

  private Configuration getRunnerConfiguration(final String configFilePath) {
    log.info("Working Directory = " + System.getProperty("user.dir"));
    final File configFile = new File(configFilePath);
    if (!configFile.exists()) {
      log.info("Runner config not exist, using default config settings.");
      return Configuration.builder()
          .taskFilePath("/etc/config/config.yaml")
          .delegateConfigurationPath("/etc/delegate-config/config.yaml")
          .build();
    }
    try {
      return new YamlUtils().read(FileUtils.readFileToString(configFile, UTF_8), Configuration.class);
    } catch (final IOException e) {
      final String error = String.format("Unable to read the delegate runner config file %s", configFileName, e);
      log.error(error);
      // TODO: define exceptions
      throw new WingsException(error);
    }
  }
  @Override
  public Configuration get() {
    return getRunnerConfiguration(configFileName);
  }
}
