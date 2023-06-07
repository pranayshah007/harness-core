/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngsettings.settings;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ngsettings.SettingCategory.CI;
import static io.harness.rule.OwnerRule.TEJAS;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static junit.framework.TestCase.fail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.licensing.Edition;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.ngsettings.NgSettingsTestBase;
import io.harness.ngsettings.SettingPlanConfig;
import io.harness.ngsettings.SettingValueType;
import io.harness.ngsettings.entities.SettingConfiguration;
import io.harness.ngsettings.entities.SettingsConfigurationState;
import io.harness.ngsettings.services.SettingsService;
import io.harness.reflection.ReflectionUtils;
import io.harness.repositories.ngsettings.custom.ConfigurationStateRepository;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import io.serializer.HObjectMapper;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class SettingsCreationJobTest extends NgSettingsTestBase {
  private static final String NEW_SETTING = "new_setting_identifier";
  private static final String SETTING_NAME = "new_setting_name";
  @Inject private SettingsService settingsService;
  @Inject private ConfigurationStateRepository configurationStateRepository;
  @Inject private SettingsCreationJob settingsCreationJob;
  @Inject PersistentLocker persistentLocker;
  private static final String SETTINGS_CONFIG_FIELD = "settingsConfig";
  private static final String VERSION_FIELD = "version";
  private static final String lockName =
      String.format("%s_settingConfigurationsLock", SettingsCreationJob.class.getName());

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testSettingsValidation() {
    SettingsCreationJob settingsCreationJob1 =
        new SettingsCreationJob(settingsService, configurationStateRepository, persistentLocker);
    SettingsConfig settingsConfig =
        (SettingsConfig) ReflectionUtils.getFieldValue(settingsCreationJob1, SETTINGS_CONFIG_FIELD);
    assertNotNull(settingsConfig);
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testSave() {
    SettingsConfig settingsConfig =
        (SettingsConfig) ReflectionUtils.getFieldValue(settingsCreationJob, SETTINGS_CONFIG_FIELD);
    when(persistentLocker.waitToAcquireLockOptional(eq(lockName), notNull(), notNull()))
        .thenReturn(mock(AcquiredLock.class));
    settingsCreationJob.run();
    validate(settingsConfig);
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testUpdate() throws NoSuchFieldException, IllegalAccessException {
    Field f = settingsCreationJob.getClass().getDeclaredField(SETTINGS_CONFIG_FIELD);
    SettingsConfig settingsConfig =
        (SettingsConfig) ReflectionUtils.getFieldValue(settingsCreationJob, SETTINGS_CONFIG_FIELD);
    ReflectionUtils.setObjectField(settingsConfig.getClass().getDeclaredField(VERSION_FIELD), settingsConfig, 2);
    SettingsConfig latestSettingsConfig = (SettingsConfig) HObjectMapper.clone(settingsConfig);
    SettingsConfig currentSettingsConfig =
        SettingsConfig.builder().version(1).name(latestSettingsConfig.getName()).settings(new HashSet<>()).build();
    ReflectionUtils.setObjectField(f, settingsCreationJob, currentSettingsConfig);
    when(persistentLocker.waitToAcquireLockOptional(eq(lockName), notNull(), notNull()))
        .thenReturn(mock(AcquiredLock.class));
    settingsCreationJob.run();
    validate(currentSettingsConfig);
    ReflectionUtils.setObjectField(f, settingsCreationJob, latestSettingsConfig);
    settingsCreationJob.run();
    validate(latestSettingsConfig);
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testAddNewSetting() throws NoSuchFieldException, IllegalAccessException {
    settingsCreationJob.run();
    SettingsConfig settingsConfig =
        (SettingsConfig) ReflectionUtils.getFieldValue(settingsCreationJob, SETTINGS_CONFIG_FIELD);
    Set<SettingConfiguration> currentSettings = settingsConfig.getSettings();
    Set<ScopeLevel> allowedScopes = new HashSet<>();
    allowedScopes.add(ScopeLevel.ACCOUNT);
    currentSettings.add(SettingConfiguration.builder()
                            .identifier(NEW_SETTING)
                            .name(SETTING_NAME)
                            .allowedScopes(allowedScopes)
                            .valueType(SettingValueType.STRING)
                            .category(CI)
                            .build());
    int currentVersion = settingsConfig.getVersion();
    ReflectionUtils.setObjectField(
        settingsConfig.getClass().getDeclaredField(VERSION_FIELD), settingsConfig, currentVersion + 1);
    when(persistentLocker.waitToAcquireLockOptional(eq(lockName), notNull(), notNull()))
        .thenReturn(mock(AcquiredLock.class));
    settingsCreationJob.run();
    validate(settingsConfig);
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testRemoveSettingTest() throws NoSuchFieldException, IllegalAccessException {
    Field f = settingsCreationJob.getClass().getDeclaredField(SETTINGS_CONFIG_FIELD);
    SettingsConfig currentSettingsConfig =
        (SettingsConfig) ReflectionUtils.getFieldValue(settingsCreationJob, SETTINGS_CONFIG_FIELD);
    SettingsConfig latestSettingsConfig = (SettingsConfig) HObjectMapper.clone(currentSettingsConfig);
    ReflectionUtils.setObjectField(latestSettingsConfig.getClass().getDeclaredField(VERSION_FIELD),
        latestSettingsConfig, currentSettingsConfig.getVersion() + 1);
    Set<ScopeLevel> allowedScopes = new HashSet<>();
    allowedScopes.add(ScopeLevel.ACCOUNT);
    currentSettingsConfig.getSettings().add(SettingConfiguration.builder()
                                                .identifier(NEW_SETTING)
                                                .name(SETTING_NAME)
                                                .allowedScopes(allowedScopes)
                                                .valueType(SettingValueType.STRING)
                                                .category(CI)
                                                .build());
    when(persistentLocker.waitToAcquireLockOptional(eq(lockName), notNull(), notNull()))
        .thenReturn(mock(AcquiredLock.class));
    settingsCreationJob.run();
    validate(currentSettingsConfig);

    ReflectionUtils.setObjectField(f, settingsCreationJob, latestSettingsConfig);
    settingsCreationJob.run();
    validate(latestSettingsConfig);
  }

  private void validate(SettingsConfig settingsConfig) {
    Optional<SettingsConfigurationState> optional =
        configurationStateRepository.getByIdentifier(settingsConfig.getName());

    assertTrue(optional.isPresent());
    assertEquals(settingsConfig.getVersion(), optional.get().getConfigVersion());

    List<SettingConfiguration> currentSettingConfigurations = settingsService.listDefaultSettings();

    if (currentSettingConfigurations.size() != settingsConfig.getSettings().size()) {
      fail("The count of setting configurations does not match");
    }

    assertThat(currentSettingConfigurations)
        .usingElementComparatorIgnoringFields("allowedPlans")
        .containsAll(settingsConfig.getSettings());

    Map<String, SettingConfiguration> settingConfigurationMap = new HashMap<>();
    settingsConfig.getSettings().forEach(settingConfiguration
        -> settingConfigurationMap.put(settingConfiguration.getIdentifier(), settingConfiguration));

    currentSettingConfigurations.forEach(settingConfiguration -> {
      if (isNotEmpty(settingConfiguration.getAllowedPlans())) {
        Map<Edition, SettingPlanConfig> allowedPlansFromConfig =
            settingConfigurationMap.get(settingConfiguration.getIdentifier()).getAllowedPlans();
        Map<Edition, SettingPlanConfig> allowedPlansFromDB = settingConfiguration.getAllowedPlans();
        allowedPlansFromConfig.forEach((edition, settingPlanConfig) -> {
          assertEquals(settingPlanConfig.getEditable(), allowedPlansFromDB.get(edition).getEditable());
          assertEquals(settingPlanConfig.getDefaultValue(), allowedPlansFromDB.get(edition).getDefaultValue());
        });
      }
    });
  }
}
