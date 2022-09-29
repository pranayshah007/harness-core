/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.runner.config;

import com.google.inject.Inject;
import com.google.inject.Provider;
import io.harness.delegate.configuration.DelegateConfiguration;
import io.harness.exception.WingsException;
import io.harness.serializer.YamlUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;

import static java.nio.charset.StandardCharsets.UTF_8;

@Slf4j
public class DelegateConfigurationProvider implements Provider<DelegateConfiguration> {
    @Inject private Configuration configuration;

    private DelegateConfiguration getDelegateConfiguration(final String configFileName) {
        log.info("Working Directory = " + System.getProperty("user.dir"));
        final File configFile = new File(configFileName);
        try {
            return new YamlUtils().read(FileUtils.readFileToString(configFile, UTF_8), DelegateConfiguration.class);
        } catch (final IOException e) {
            final String error = String.format("Unable to read the delegate config file %s", configFileName, e);
            log.error(error);
            // TODO: define exceptions
            throw new WingsException(error);
        }
    }

    @Override
    public DelegateConfiguration get() {
        final String filePath = configuration.getDelegateConfigurationPath();
        return getDelegateConfiguration(filePath);
    }
}
