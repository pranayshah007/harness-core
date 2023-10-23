/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.artifact;

import static io.harness.azure.model.AzureConstants.DOCKER_REGISTRY_SERVER_SECRET_PROPERTY_NAME;
import static io.harness.azure.model.AzureConstants.DOCKER_REGISTRY_SERVER_URL_PROPERTY_NAME;
import static io.harness.azure.model.AzureConstants.DOCKER_REGISTRY_SERVER_USERNAME_PROPERTY_NAME;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.model.AzureAppServiceApplicationSetting;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.NestedExceptionUtils;

import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(HarnessTeam.CDP)
public abstract class AbstractAzureRegistrySettingsProvider implements AzureRegistrySettingsProvider {
  protected Map<String, AzureAppServiceApplicationSetting> populateDockerSettingMap(String dockerRegistryUrl) {
    return populateDockerSettingMap(dockerRegistryUrl, null, null);
  }

  protected Map<String, AzureAppServiceApplicationSetting> populateDockerSettingMap(
      String dockerRegistryUrl, String userName, String decryptedSecret) {
    Map<String, AzureAppServiceApplicationSetting> dockerSettings = new HashMap<>();

    dockerSettings.put(DOCKER_REGISTRY_SERVER_URL_PROPERTY_NAME,
        AzureAppServiceApplicationSetting.builder()
            .name(DOCKER_REGISTRY_SERVER_URL_PROPERTY_NAME)
            .sticky(false)
            .value(dockerRegistryUrl)
            .build());

    if (userName != null && decryptedSecret != null) {
      dockerSettings.put(DOCKER_REGISTRY_SERVER_USERNAME_PROPERTY_NAME,
          AzureAppServiceApplicationSetting.builder()
              .name(DOCKER_REGISTRY_SERVER_USERNAME_PROPERTY_NAME)
              .sticky(false)
              .value(userName)
              .build());

      dockerSettings.put(DOCKER_REGISTRY_SERVER_SECRET_PROPERTY_NAME,
          AzureAppServiceApplicationSetting.builder()
              .name(DOCKER_REGISTRY_SERVER_SECRET_PROPERTY_NAME)
              .sticky(false)
              .value(decryptedSecret)
              .build());
    }

    return dockerSettings;
  }

  protected void validateSettings(
      AzureContainerArtifactConfig config, String registryUrl, String username, String password) {
    validateSettings(config, registryUrl);
    if (isBlank(username)) {
      throw NestedExceptionUtils.hintWithExplanationException(
          format("Configure username for %s container registry ConnectorDisconnectHandler", config.getRegistryType().getValue()),
          format("Username is blank for '%s' container registry but is required", config.getRegistryType().getValue()),
          new InvalidArgumentsException(Pair.of("username", "Null or blank value")));
    }

    if (isBlank(password)) {
      throw NestedExceptionUtils.hintWithExplanationException(
          format("Configure password for %s container registry ConnectorDisconnectHandler", config.getRegistryType().getValue()),
          format("Password is blank for '%s' container registry but is required", config.getRegistryType().getValue()),
          new InvalidArgumentsException(Pair.of("password", "Null or blank value")));
    }
  }

  protected void validateSettings(AzureContainerArtifactConfig config, String registryUrl) {
    if (isBlank(registryUrl)) {
      throw NestedExceptionUtils.hintWithExplanationException(
          format("Check if ConnectorDisconnectHandler provided %s is properly configured", config.getRegistryType().getValue()),
          format("Registry url is '%s' which is an invalid value for '%s' container registry", registryUrl,
              config.getRegistryType().getValue()),
          new InvalidArgumentsException(Pair.of("registry", "Null or blank value")));
    }
  }
}
