/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngsettings.mapper;

import static io.harness.rule.OwnerRule.NISHANT;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ngsettings.SettingCategory;
import io.harness.ngsettings.SettingSource;
import io.harness.ngsettings.SettingUpdateType;
import io.harness.ngsettings.SettingValueType;
import io.harness.ngsettings.dto.SettingDTO;
import io.harness.ngsettings.dto.SettingRequestDTO;
import io.harness.ngsettings.dto.SettingResponseDTO;
import io.harness.ngsettings.dto.SettingUpdateResponseDTO;
import io.harness.ngsettings.entities.Setting;
import io.harness.ngsettings.entities.SettingConfiguration;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

public class SettingsMapperTest extends CategoryTest {
  @Spy private SettingsMapper settingsMapper;
  private String defaultValue = randomAlphabetic(10);

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
  }
  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testWriteSettingDTOFromSettingAndSettingConfiguration() {
    String identifier = randomAlphabetic(10);
    String name = randomAlphabetic(10);
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    SettingCategory category = SettingCategory.CORE;
    String value = randomAlphabetic(10);
    String defaultValue = randomAlphabetic(10);
    SettingValueType valueType = SettingValueType.STRING;
    Set<String> allowedValues = new HashSet<>(Arrays.asList("a", "b"));
    String groupIdentifier = randomAlphabetic(10);
    Setting setting = Setting.builder()
                          .identifier(identifier)
                          .accountIdentifier(accountIdentifier)
                          .orgIdentifier(orgIdentifier)
                          .projectIdentifier(projectIdentifier)
                          .category(category)
                          .value(value)
                          .valueType(SettingValueType.NUMBER)
                          .allowOverrides(true)
                          .groupIdentifier(groupIdentifier)
                          .build();
    SettingConfiguration settingConfiguration = SettingConfiguration.builder()
                                                    .identifier(randomAlphabetic(10))
                                                    .name(name)
                                                    .category(category)
                                                    .allowedValues(allowedValues)
                                                    .defaultValue(defaultValue)
                                                    .valueType(valueType)
                                                    .groupIdentifier(groupIdentifier)
                                                    .build();
    SettingDTO settingDTO = settingsMapper.writeSettingDTO(setting, settingConfiguration, true, defaultValue);
    assertSettingDTOPropertiesAndValue(identifier, name, orgIdentifier, projectIdentifier, category, value,
        defaultValue, valueType, allowedValues, true, SettingSource.PROJECT, true, settingDTO);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testWriteSettingDTOFromSettingConfiguration() {
    String identifier = randomAlphabetic(10);
    String name = randomAlphabetic(10);
    SettingCategory category = SettingCategory.CORE;
    SettingValueType valueType = SettingValueType.STRING;
    Set<String> allowedValues = new HashSet<>(Arrays.asList("a", "b"));
    String defaultValue = randomAlphabetic(10);
    String groupIdentifier = randomAlphabetic(10);
    SettingConfiguration settingConfiguration = SettingConfiguration.builder()
                                                    .name(name)
                                                    .identifier(identifier)
                                                    .category(category)
                                                    .allowedValues(allowedValues)
                                                    .allowOverrides(true)
                                                    .defaultValue(defaultValue)
                                                    .valueType(valueType)
                                                    .groupIdentifier(groupIdentifier)
                                                    .build();
    SettingDTO settingDTO = settingsMapper.writeSettingDTO(settingConfiguration, true);
    assertSettingDTOPropertiesAndValue(identifier, name, null, null, category, defaultValue, defaultValue, valueType,
        allowedValues, true, SettingSource.DEFAULT, true, settingDTO);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void writeSettingResponseDTOFromSettingAndSettingConfiguration() {
    String identifier = randomAlphabetic(10);
    Long timestamp = RandomUtils.nextLong();
    Setting setting = Setting.builder().identifier(identifier).lastModifiedAt(timestamp).build();
    SettingConfiguration settingConfiguration = SettingConfiguration.builder().identifier(identifier).build();
    SettingDTO settingDTO = SettingDTO.builder().identifier(identifier).build();
    when(settingsMapper.writeSettingDTO(setting, settingConfiguration, true, defaultValue)).thenReturn(settingDTO);
    SettingResponseDTO settingResponseDTO =
        settingsMapper.writeSettingResponseDTO(setting, settingConfiguration, true, defaultValue);
    assertThat(settingResponseDTO)
        .hasFieldOrPropertyWithValue("setting", settingDTO)
        .hasFieldOrPropertyWithValue("lastModifiedAt", timestamp);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void writeSettingResponseDTOFromSettingConfiguration() {
    String identifier = randomAlphabetic(10);
    SettingConfiguration settingConfiguration = SettingConfiguration.builder().identifier(identifier).build();
    SettingDTO settingDTO = SettingDTO.builder().identifier(identifier).build();
    when(settingsMapper.writeSettingDTO(settingConfiguration, true)).thenReturn(settingDTO);
    SettingResponseDTO settingResponseDTO = settingsMapper.writeSettingResponseDTO(settingConfiguration, true);
    assertThat(settingResponseDTO)
        .hasFieldOrPropertyWithValue("setting", settingDTO)
        .hasFieldOrPropertyWithValue("lastModifiedAt", null);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void writeNewDTOUsingExistingSetting() {
    String identifier = randomAlphabetic(10);
    String name = randomAlphabetic(10);
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    SettingCategory category = SettingCategory.CORE;
    String value = randomAlphabetic(10);
    String newValue = randomAlphabetic(10);
    String defaultValue = randomAlphabetic(10);
    SettingValueType valueType = SettingValueType.STRING;
    Set<String> allowedValues = new HashSet<>(Arrays.asList("a", "b"));
    String groupIdentifier = randomAlphabetic(10);
    Setting setting = Setting.builder()
                          .identifier(identifier)
                          .accountIdentifier(accountIdentifier)
                          .orgIdentifier(orgIdentifier)
                          .projectIdentifier(projectIdentifier)
                          .category(category)
                          .value(value)
                          .valueType(SettingValueType.NUMBER)
                          .allowOverrides(true)
                          .groupIdentifier(groupIdentifier)
                          .build();
    SettingConfiguration settingConfiguration = SettingConfiguration.builder()
                                                    .identifier(randomAlphabetic(10))
                                                    .name(name)
                                                    .category(category)
                                                    .allowedValues(allowedValues)
                                                    .defaultValue(defaultValue)
                                                    .valueType(valueType)
                                                    .groupIdentifier(groupIdentifier)
                                                    .build();
    SettingRequestDTO settingRequestDTO = SettingRequestDTO.builder()
                                              .identifier(identifier)
                                              .allowOverrides(false)
                                              .value(newValue)
                                              .updateType(SettingUpdateType.UPDATE)
                                              .build();
    SettingDTO newSettingDTO = settingsMapper.writeNewDTO(setting, settingRequestDTO, settingConfiguration, true);
    assertSettingDTOPropertiesAndValue(identifier, name, orgIdentifier, projectIdentifier, category, newValue,
        defaultValue, valueType, allowedValues, false, SettingSource.PROJECT, true, newSettingDTO);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void writeNewDTOUsingSettingConfiguration() {
    String identifier = randomAlphabetic(10);
    String name = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    SettingCategory category = SettingCategory.CORE;
    String newValue = randomAlphabetic(10);
    String defaultValue = randomAlphabetic(10);
    SettingValueType valueType = SettingValueType.STRING;
    Set<String> allowedValues = new HashSet<>(Arrays.asList("a", "b"));
    String groupIdentifier = randomAlphabetic(10);
    SettingConfiguration settingConfiguration = SettingConfiguration.builder()
                                                    .identifier(identifier)
                                                    .name(name)
                                                    .category(category)
                                                    .allowedValues(allowedValues)
                                                    .defaultValue(defaultValue)
                                                    .valueType(valueType)
                                                    .groupIdentifier(groupIdentifier)
                                                    .build();
    SettingRequestDTO settingRequestDTO = SettingRequestDTO.builder()
                                              .identifier(identifier)
                                              .allowOverrides(false)
                                              .value(newValue)
                                              .updateType(SettingUpdateType.UPDATE)
                                              .build();
    SettingDTO newSettingDTO =
        settingsMapper.writeNewDTO(orgIdentifier, projectIdentifier, settingRequestDTO, settingConfiguration, true);
    assertSettingDTOPropertiesAndValue(identifier, name, orgIdentifier, projectIdentifier, category, newValue,
        defaultValue, valueType, allowedValues, false, SettingSource.PROJECT, true, newSettingDTO);
  }

  private void assertSettingDTOPropertiesAndValue(String identifier, String name, String orgIdentifier,
      String projectIdentifier, SettingCategory category, String value, String defaultValue, SettingValueType valueType,
      Set<String> allowedValues, boolean allowOverrides, SettingSource settingSource, Boolean isSettingEditable,
      SettingDTO settingDTO) {
    assertThat(settingDTO)
        .hasFieldOrPropertyWithValue("identifier", identifier)
        .hasFieldOrPropertyWithValue("name", name)
        .hasFieldOrPropertyWithValue("orgIdentifier", orgIdentifier)
        .hasFieldOrPropertyWithValue("projectIdentifier", projectIdentifier)
        .hasFieldOrPropertyWithValue("category", category)
        .hasFieldOrPropertyWithValue("value", value)
        .hasFieldOrPropertyWithValue("defaultValue", defaultValue)
        .hasFieldOrPropertyWithValue("valueType", valueType)
        .hasFieldOrPropertyWithValue("allowedValues", allowedValues)
        .hasFieldOrPropertyWithValue("allowOverrides", allowOverrides)
        .hasFieldOrPropertyWithValue("settingSource", settingSource)
        .hasFieldOrPropertyWithValue("isSettingEditable", isSettingEditable);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testWriteBatchResponseDTO() {
    String identifier = randomAlphabetic(10);
    long timestamp = RandomUtils.nextLong();
    SettingResponseDTO responseDTO = SettingResponseDTO.builder()
                                         .setting(SettingDTO.builder().identifier(identifier).build())
                                         .lastModifiedAt(timestamp)
                                         .build();
    SettingUpdateResponseDTO settingBatchResponseDTO = settingsMapper.writeBatchResponseDTO(responseDTO);
    assertThat(settingBatchResponseDTO)
        .hasFieldOrPropertyWithValue("identifier", identifier)
        .hasFieldOrPropertyWithValue("setting", responseDTO.getSetting())
        .hasFieldOrPropertyWithValue("lastModifiedAt", responseDTO.getLastModifiedAt())
        .hasFieldOrPropertyWithValue("updateStatus", true)
        .hasFieldOrPropertyWithValue("errorMessage", null);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testWriteBatchResponseDTOForException() {
    String identifier = randomAlphabetic(10);
    String exceptionMessage = randomAlphabetic(50);
    SettingUpdateResponseDTO settingBatchResponseDTO =
        settingsMapper.writeBatchResponseDTO(identifier, new InvalidRequestException(exceptionMessage));
    assertThat(settingBatchResponseDTO)
        .hasFieldOrPropertyWithValue("identifier", identifier)
        .hasFieldOrPropertyWithValue("setting", null)
        .hasFieldOrPropertyWithValue("lastModifiedAt", null)
        .hasFieldOrPropertyWithValue("updateStatus", false)
        .hasFieldOrPropertyWithValue("errorMessage", exceptionMessage);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void toSetting() {
    String identifier = randomAlphabetic(10);
    String name = randomAlphabetic(10);
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    SettingCategory category = SettingCategory.CORE;
    String value = randomAlphabetic(10);
    String defaultValue = randomAlphabetic(10);
    SettingValueType valueType = SettingValueType.STRING;
    Set<String> allowedValues = new HashSet<>(Arrays.asList("a", "b"));
    SettingDTO settingDTO = SettingDTO.builder()
                                .identifier(identifier)
                                .name(name)
                                .orgIdentifier(orgIdentifier)
                                .projectIdentifier(projectIdentifier)
                                .value(value)
                                .defaultValue(defaultValue)
                                .valueType(valueType)
                                .category(category)
                                .allowedValues(allowedValues)
                                .allowOverrides(false)
                                .build();
    Setting setting = settingsMapper.toSetting(accountIdentifier, settingDTO);
    assertThat(setting)
        .hasFieldOrPropertyWithValue("identifier", identifier)
        .hasFieldOrPropertyWithValue("accountIdentifier", accountIdentifier)
        .hasFieldOrPropertyWithValue("orgIdentifier", orgIdentifier)
        .hasFieldOrPropertyWithValue("projectIdentifier", projectIdentifier)
        .hasFieldOrPropertyWithValue("category", category)
        .hasFieldOrPropertyWithValue("value", value)
        .hasFieldOrPropertyWithValue("valueType", valueType)
        .hasFieldOrPropertyWithValue("allowOverrides", false);
  }
}
