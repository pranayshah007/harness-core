/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngsettings.settings;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeLevel;
import io.harness.exception.InvalidRequestException;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.ngsettings.entities.SettingConfiguration;
import io.harness.ngsettings.entities.SettingsConfigurationState;
import io.harness.ngsettings.services.SettingsService;
import io.harness.repositories.ngsettings.custom.ConfigurationStateRepository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.collect.Sets;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.net.URL;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PL)
@Slf4j
@Singleton
public class SettingsCreationJob {
  private final SettingsConfig settingsConfig;
  private final SettingsService settingsService;
  private final ConfigurationStateRepository configurationStateRepository;
  private static final String SETTINGS_YAML_PATH = "io/harness/ngsettings/settings.yml";
  private PersistentLocker persistentLocker;
  private static final Duration LOCK_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(20);

  @Inject
  public SettingsCreationJob(SettingsService settingsService, ConfigurationStateRepository configurationStateRepository,
      PersistentLocker persistentLocker) {
    this.configurationStateRepository = configurationStateRepository;
    this.persistentLocker = persistentLocker;
    ObjectMapper om = new ObjectMapper(new YAMLFactory());
    URL url = getClass().getClassLoader().getResource(SETTINGS_YAML_PATH);
    try {
      byte[] bytes = Resources.toByteArray(url);
      this.settingsConfig = om.readValue(bytes, SettingsConfig.class);
      populateDefaultConfigs(settingsConfig);
      validateConfig(settingsConfig);
    } catch (IOException e) {
      throw new InvalidRequestException("Settings file path is invalid or the syntax is incorrect", e);
    }
    this.settingsService = settingsService;
  }

  private void populateDefaultConfigs(SettingsConfig settingsConfig) {
    if (settingsConfig.getSettings() != null) {
      settingsConfig.getSettings().forEach(settingConfiguration -> {
        if (settingConfiguration.getAllowOverrides() == null) {
          settingConfiguration.setAllowOverrides(true);
        }
      });
    }
  }

  public void validateConfig(SettingsConfig settingsConfig) {
    if (null == settingsConfig) {
      throw new InvalidRequestException("Missing settings config. Check settings.yml file");
    }
    ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    Validator validator = factory.getValidator();

    final Set<ConstraintViolation<SettingsConfig>> violations = validator.validate(settingsConfig);
    StringBuilder builder = new StringBuilder("Validation violations:\n");
    violations.forEach(violation -> {
      builder.append(String.format("%s: %s%n", violation.getPropertyPath(), violation.getMessage()));
    });
    if (isNotEmpty(violations)) {
      throw new InvalidRequestException(builder.toString());
    }

    if (isNotEmpty(settingsConfig.getSettings())) {
      Set<String> identifiers = new HashSet<>();
      Set<String> duplicateIdentifiers = settingsConfig.getSettings()
                                             .stream()
                                             .map(SettingConfiguration::getIdentifier)
                                             .filter(identifier -> !identifiers.add(identifier))
                                             .collect(Collectors.toSet());
      if (isNotEmpty(duplicateIdentifiers)) {
        throw new InvalidRequestException(String.format(
            "Identifiers must be uniques in %s. Duplicate identifiers: %s", SETTINGS_YAML_PATH, duplicateIdentifiers));
      }
    }
  }

  public void run() {
    String lockName = String.format("%s_settingConfigurationsLock", SettingsCreationJob.class.getName());
    try (AcquiredLock<?> lock = persistentLocker.waitToAcquireLockOptional(lockName, LOCK_TIMEOUT, WAIT_TIMEOUT)) {
      if (lock == null) {
        log.warn("Count not acquire the lock- {}", lockName);
        return;
      }
      Optional<SettingsConfigurationState> optional =
          configurationStateRepository.getByIdentifier(settingsConfig.getName());
      if (optional.isPresent() && optional.get().getConfigVersion() >= settingsConfig.getVersion()) {
        log.info("Settings are already updated in the database");
        return;
      }
      log.info("Updating settings in the database");
      Set<SettingConfiguration> latestSettings =
          isNotEmpty(settingsConfig.getSettings()) ? settingsConfig.getSettings() : new HashSet<>();
      Set<SettingConfiguration> currentSettings = new HashSet<>(settingsService.listDefaultSettings());

      Set<String> latestIdentifiers =
          latestSettings.stream().map(SettingConfiguration::getIdentifier).collect(Collectors.toSet());
      Set<String> currentIdentifiers =
          currentSettings.stream().map(SettingConfiguration::getIdentifier).collect(Collectors.toSet());
      Set<String> removedIdentifiers = Sets.difference(currentIdentifiers, latestIdentifiers);

      Map<String, String> settingIdMap = new HashMap<>();
      currentSettings.forEach(settingConfiguration -> {
        settingIdMap.put(settingConfiguration.getIdentifier(), settingConfiguration.getId());
        settingConfiguration.setId(null);
      });
      Set<SettingConfiguration> upsertedSettings = new HashSet<>(latestSettings);
      upsertedSettings.removeAll(currentSettings);
      deleteSettingsAtDisallowedScopes(currentSettings, upsertedSettings);
      upsertedSettings.forEach(setting -> {
        setting.setId(settingIdMap.get(setting.getIdentifier()));
        try {
          settingsService.upsertSettingConfiguration(setting);
        } catch (Exception exception) {
          log.error(String.format("Error while updating setting [%s]", setting.getIdentifier()), exception);
          throw exception;
        }
      });
      removedIdentifiers.forEach(settingsService::removeSetting);
      log.info("Updated the settings in the database");

      SettingsConfigurationState configurationState =
          optional.orElseGet(() -> SettingsConfigurationState.builder().identifier(settingsConfig.getName()).build());
      configurationState.setConfigVersion(settingsConfig.getVersion());
      configurationStateRepository.upsert(configurationState);
    }
  }

  private void deleteSettingsAtDisallowedScopes(
      Set<SettingConfiguration> currentSettings, Set<SettingConfiguration> upsertedSettings) {
    Map<String, SettingConfiguration> settingConfigurationMap =
        currentSettings.stream().collect(Collectors.toMap(SettingConfiguration::getIdentifier, setting -> setting));
    upsertedSettings.forEach(setting -> {
      if (settingConfigurationMap.containsKey(setting.getIdentifier())) {
        Set<ScopeLevel> existingScopes = settingConfigurationMap.get(setting.getIdentifier()).getAllowedScopes();
        Set<ScopeLevel> updatedScopes = setting.getAllowedScopes();
        Set<ScopeLevel> removedScopes = Sets.difference(existingScopes, updatedScopes);
        removedScopes.forEach(scopeLevel -> settingsService.deleteByScopeLevel(scopeLevel, setting.getIdentifier()));
      }
    });
  }
}
