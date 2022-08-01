/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngsettings.services.impl;

import static io.harness.rule.OwnerRule.NISHANT;

import static java.util.Optional.ofNullable;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.ngsettings.SettingCategory;
import io.harness.ngsettings.SettingUpdateType;
import io.harness.ngsettings.SettingValueType;
import io.harness.ngsettings.dto.SettingDTO;
import io.harness.ngsettings.dto.SettingRequestDTO;
import io.harness.ngsettings.dto.SettingResponseDTO;
import io.harness.ngsettings.dto.SettingUpdateResponseDTO;
import io.harness.ngsettings.dto.SettingValueResponseDTO;
import io.harness.ngsettings.entities.Setting;
import io.harness.ngsettings.entities.SettingConfiguration;
import io.harness.ngsettings.events.SettingRestoreEvent;
import io.harness.ngsettings.events.SettingUpdateEvent;
import io.harness.ngsettings.mapper.SettingsMapper;
import io.harness.outbox.api.OutboxService;
import io.harness.repositories.ngsettings.spring.SettingConfigurationRepository;
import io.harness.repositories.ngsettings.spring.SettingRepository;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.ws.rs.NotFoundException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

public class SettingsServiceImplTest extends CategoryTest {
  @Mock private SettingConfigurationRepository settingConfigurationRepository;
  @Mock private SettingRepository settingRepository;
  @Mock private SettingsMapper settingsMapper;
  @Mock private TransactionTemplate transactionTemplate;
  @Mock private OutboxService outboxService;
  private SettingsServiceImpl settingsService;

  @Rule public ExpectedException exceptionRule = ExpectedException.none();

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    settingsService = new SettingsServiceImpl(
        settingConfigurationRepository, settingRepository, settingsMapper, transactionTemplate, outboxService);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testListWhenDefaultOnlyPresent() {
    String identifier = randomAlphabetic(10);
    String accountIdentifier = randomAlphabetic(10);
    Map<String, SettingConfiguration> settingConfigurations = new HashMap<>();
    SettingConfiguration settingConfiguration = SettingConfiguration.builder()
                                                    .identifier(identifier)
                                                    .allowedScopes(Collections.singleton(ScopeLevel.ACCOUNT))
                                                    .build();
    settingConfigurations.put(identifier, settingConfiguration);
    when(settingRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndCategory(
             anyString(), any(), any(), any()))
        .thenReturn(new ArrayList<>());
    when(settingConfigurationRepository.findByCategoryAndAllowedScopesIn(any(), any()))
        .thenReturn(List.of(settingConfigurations.get(identifier)));
    when(settingsMapper.writeSettingResponseDTO(settingConfiguration, true))
        .thenReturn(SettingResponseDTO.builder().setting(SettingDTO.builder().identifier(identifier).build()).build());
    List<SettingResponseDTO> dtoList = settingsService.list(accountIdentifier, null, null, SettingCategory.CORE, null);
    verify(settingRepository, times(1)).findAll(any(Criteria.class));
    verify(settingConfigurationRepository, times(1))
        .findByCategoryAndAllowedScopesIn(SettingCategory.CORE, List.of(ScopeLevel.ACCOUNT));
    verify(settingsMapper, times(settingConfigurations.size())).writeSettingResponseDTO(any(), any());
    assertThat(dtoList.size()).isEqualTo(settingConfigurations.size());
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testListWhenSettingAlsoPresent() {
    String identifier = randomAlphabetic(10);
    String accountIdentifier = randomAlphabetic(10);
    Map<String, SettingConfiguration> settingConfigurations = new HashMap<>();
    SettingConfiguration settingConfiguration = SettingConfiguration.builder()
                                                    .identifier(identifier)
                                                    .allowedScopes(Collections.singleton(ScopeLevel.ACCOUNT))
                                                    .build();
    Map<String, Setting> settings = new HashMap<>();
    Setting setting = Setting.builder().identifier(identifier).build();
    settings.put(identifier, setting);
    settingConfigurations.put(identifier, settingConfiguration);
    when(settingRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndCategory(
             anyString(), any(), any(), any()))
        .thenReturn(List.of(settings.get(identifier)));
    when(settingConfigurationRepository.findByCategoryAndAllowedScopesIn(any(), any()))
        .thenReturn(List.of(settingConfigurations.get(identifier)));
    when(settingsMapper.writeSettingResponseDTO(setting, settingConfiguration, true))
        .thenReturn(SettingResponseDTO.builder().setting(SettingDTO.builder().identifier(identifier).build()).build());
    List<SettingResponseDTO> dtoList = settingsService.list(accountIdentifier, null, null, SettingCategory.CORE, null);
    verify(settingRepository, times(1)).findAll(any(Criteria.class));
    verify(settingConfigurationRepository, times(1))
        .findByCategoryAndAllowedScopesIn(SettingCategory.CORE, List.of(ScopeLevel.ACCOUNT));
    verify(settingsMapper, times(settings.size())).writeSettingResponseDTO(any(), any());
    assertThat(dtoList.size()).isEqualTo(settingConfigurations.size());
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testUpdateRestore() {
    String identifier = randomAlphabetic(10);
    String accountIdentifier = randomAlphabetic(10);
    SettingRequestDTO settingRequestDTO =
        SettingRequestDTO.builder().identifier(identifier).updateType(SettingUpdateType.RESTORE).build();
    Setting setting = Setting.builder().identifier(identifier).build();
    SettingConfiguration settingConfiguration = SettingConfiguration.builder().identifier(identifier).build();
    SettingResponseDTO settingResponseDTO =
        SettingResponseDTO.builder().setting(SettingDTO.builder().identifier(identifier).build()).build();
    SettingUpdateResponseDTO settingBatchResponseDTO = SettingUpdateResponseDTO.builder()
                                                           .updateStatus(true)
                                                           .identifier(identifier)
                                                           .setting(settingResponseDTO.getSetting())
                                                           .build();
    when(settingRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             accountIdentifier, null, null, identifier))
        .thenReturn(ofNullable(setting));
    Setting updatedSetting = Setting.builder().identifier(identifier).build();
    when(settingRepository.upsert(updatedSetting)).thenReturn(updatedSetting);
    when(settingConfigurationRepository.findByIdentifierAndAllowedScopesIn(anyString(), any()))
        .thenReturn(ofNullable(settingConfiguration));
    when(settingsMapper.writeSettingResponseDTO(settingConfiguration, true)).thenReturn(settingResponseDTO);
    when(settingsMapper.writeBatchResponseDTO(settingResponseDTO)).thenReturn(settingBatchResponseDTO);
    SettingDTO settingDTO = SettingDTO.builder().identifier(identifier).build();
    when(settingsMapper.writeNewDTO(setting, settingRequestDTO, settingConfiguration, true)).thenReturn(settingDTO);
    when(settingsMapper.toSetting(accountIdentifier, settingDTO)).thenReturn(updatedSetting);
    when(settingsMapper.writeSettingResponseDTO(updatedSetting, settingConfiguration, true))
        .thenReturn(settingResponseDTO);
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    List<SettingUpdateResponseDTO> batchResponse =
        settingsService.update(accountIdentifier, null, null, List.of(settingRequestDTO));
    verify(settingRepository, times(1))
        .findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
            accountIdentifier, null, null, identifier);
    verify(settingRepository, times(1)).upsert(Setting.builder().identifier(identifier).build());
    verify(settingConfigurationRepository, times(1))
        .findByIdentifierAndAllowedScopesIn(identifier, List.of(ScopeLevel.ACCOUNT));
    verify(outboxService, times(1)).save(any(SettingRestoreEvent.class));
    assertThat(batchResponse).contains(settingBatchResponseDTO);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testUpdateExistingSetting() {
    String identifier = randomAlphabetic(10);
    String accountIdentifier = randomAlphabetic(10);
    String value = randomAlphabetic(10);
    String newValue = randomAlphabetic(10);
    SettingRequestDTO settingRequestDTO = SettingRequestDTO.builder()
                                              .identifier(identifier)
                                              .value(newValue)
                                              .allowOverrides(true)
                                              .updateType(SettingUpdateType.UPDATE)
                                              .build();
    Setting setting = Setting.builder().identifier(identifier).value(value).valueType(SettingValueType.STRING).build();
    Setting newSetting =
        Setting.builder().identifier(identifier).value(newValue).valueType(SettingValueType.STRING).build();
    SettingConfiguration settingConfiguration = SettingConfiguration.builder().identifier(identifier).build();
    SettingDTO settingDTO =
        SettingDTO.builder().identifier(identifier).valueType(SettingValueType.STRING).value(value).build();
    SettingResponseDTO settingResponseDTO = SettingResponseDTO.builder().setting(settingDTO).build();
    SettingUpdateResponseDTO settingBatchResponseDTO = SettingUpdateResponseDTO.builder()
                                                           .updateStatus(true)
                                                           .identifier(identifier)
                                                           .setting(settingResponseDTO.getSetting())
                                                           .build();
    when(settingRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             accountIdentifier, null, null, identifier))
        .thenReturn(ofNullable(setting));
    when(settingConfigurationRepository.findByIdentifierAndAllowedScopesIn(anyString(), any()))
        .thenReturn(ofNullable(settingConfiguration));
    when(settingsMapper.writeNewDTO(setting, settingRequestDTO, settingConfiguration, true)).thenReturn(settingDTO);
    when(settingRepository.upsert(newSetting)).thenReturn(newSetting);
    when(settingsMapper.toSetting(accountIdentifier, settingDTO)).thenReturn(newSetting);
    when(settingsMapper.writeSettingResponseDTO(newSetting, settingConfiguration, true)).thenReturn(settingResponseDTO);
    when(settingsMapper.writeBatchResponseDTO(settingResponseDTO)).thenReturn(settingBatchResponseDTO);
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    List<SettingUpdateResponseDTO> batchResponse =
        settingsService.update(accountIdentifier, null, null, List.of(settingRequestDTO));
    verify(settingRepository, times(1))
        .findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
            accountIdentifier, null, null, identifier);
    verify(settingConfigurationRepository, times(1))
        .findByIdentifierAndAllowedScopesIn(identifier, List.of(ScopeLevel.ACCOUNT));
    verify(settingsMapper, times(0)).writeNewDTO(any(), any(), any(), any(), any());
    verify(settingRepository, times(1)).upsert(newSetting);
    verify(outboxService, times(1)).save(any(SettingUpdateEvent.class));
    assertThat(batchResponse).contains(settingBatchResponseDTO);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testUpdateUnknownSetting() {
    String identifier = randomAlphabetic(10);
    String accountIdentifier = randomAlphabetic(10);
    String value = randomAlphabetic(10);
    String newValue = randomAlphabetic(10);
    SettingRequestDTO settingRequestDTO =
        SettingRequestDTO.builder().identifier(identifier).value(newValue).updateType(SettingUpdateType.UPDATE).build();
    Setting newSetting =
        Setting.builder().identifier(identifier).value(newValue).valueType(SettingValueType.STRING).build();
    SettingUpdateResponseDTO settingBatchResponseDTO = SettingUpdateResponseDTO.builder()
                                                           .updateStatus(false)
                                                           .identifier(identifier)
                                                           .errorMessage(randomAlphabetic(50))
                                                           .build();
    when(settingConfigurationRepository.findByIdentifierAndAllowedScopesIn(anyString(), any()))
        .thenReturn(Optional.empty());
    when(settingsMapper.writeBatchResponseDTO(anyString(), any())).thenReturn(settingBatchResponseDTO);
    List<SettingUpdateResponseDTO> batchResponse =
        settingsService.update(accountIdentifier, null, null, List.of(settingRequestDTO));
    verify(settingRepository, times(0))
        .findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
            accountIdentifier, null, null, identifier);
    verify(settingConfigurationRepository, times(1))
        .findByIdentifierAndAllowedScopesIn(identifier, List.of(ScopeLevel.ACCOUNT));
    verify(settingsMapper, times(0)).writeNewDTO(any(), any(), any(), any());
    verify(settingRepository, times(0)).upsert(newSetting);
    assertThat(batchResponse).contains(settingBatchResponseDTO);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testGetWhenSettingPresent() {
    String identifier = randomAlphabetic(10);
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String value = randomAlphabetic(10);
    Setting setting = Setting.builder()
                          .identifier(identifier)
                          .accountIdentifier(accountIdentifier)
                          .orgIdentifier(orgIdentifier)
                          .projectIdentifier(projectIdentifier)
                          .valueType(SettingValueType.STRING)
                          .value(value)
                          .build();
    SettingConfiguration settingConfiguration =
        SettingConfiguration.builder().identifier(identifier).valueType(SettingValueType.STRING).build();
    when(settingRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             accountIdentifier, orgIdentifier, projectIdentifier, identifier))
        .thenReturn(ofNullable(setting));
    when(settingConfigurationRepository.findByIdentifierAndAllowedScopesIn(identifier, List.of(ScopeLevel.PROJECT)))
        .thenReturn(ofNullable(settingConfiguration));
    SettingValueResponseDTO response =
        settingsService.get(identifier, accountIdentifier, orgIdentifier, projectIdentifier);
    verify(settingRepository, times(1))
        .findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
            accountIdentifier, orgIdentifier, projectIdentifier, identifier);
    assertThat(response.getValue()).isEqualTo(value);
    assertThat(response.getValueType()).isEqualTo(SettingValueType.STRING);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testGetWhenOnlySettingConfigurationPresent() {
    String identifier = randomAlphabetic(10);
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    String defaultValue = randomAlphabetic(10);
    SettingConfiguration settingConfiguration = SettingConfiguration.builder()
                                                    .identifier(identifier)
                                                    .defaultValue(defaultValue)
                                                    .valueType(SettingValueType.STRING)
                                                    .build();
    when(settingRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             accountIdentifier, orgIdentifier, projectIdentifier, identifier))
        .thenReturn(Optional.empty());
    when(settingConfigurationRepository.findByIdentifierAndAllowedScopesIn(identifier, List.of(ScopeLevel.PROJECT)))
        .thenReturn(ofNullable(settingConfiguration));
    SettingValueResponseDTO response =
        settingsService.get(identifier, accountIdentifier, orgIdentifier, projectIdentifier);
    verify(settingRepository, times(1))
        .findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
            accountIdentifier, orgIdentifier, projectIdentifier, identifier);
    assertThat(response.getValue()).isEqualTo(defaultValue);
    assertThat(response.getValueType()).isEqualTo(SettingValueType.STRING);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testGetForUnknownSetting() {
    String identifier = randomAlphabetic(10);
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);
    exceptionRule.expect(NotFoundException.class);
    exceptionRule.expectMessage(String.format(
        "Setting [%s] is either invalid or is not applicable in scope [%s]", identifier, ScopeLevel.PROJECT));
    when(settingConfigurationRepository.findByIdentifierAndAllowedScopesIn(identifier, List.of(ScopeLevel.PROJECT)))
        .thenReturn(Optional.empty());
    settingsService.get(identifier, accountIdentifier, orgIdentifier, projectIdentifier);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testListDefaultSettings() {
    String identifier = randomAlphabetic(10);
    SettingConfiguration settingConfiguration = SettingConfiguration.builder().identifier(identifier).build();
    when(settingConfigurationRepository.findAll()).thenReturn(List.of(settingConfiguration));
    List<SettingConfiguration> settingConfigurationList = settingsService.listDefaultSettings();
    verify(settingConfigurationRepository, times(1)).findAll();
    assertThat(settingConfigurationList).containsExactly(settingConfiguration);
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testListDeleteConfig() {
    String identifier = randomAlphabetic(10);
    SettingConfiguration settingConfiguration = SettingConfiguration.builder().identifier(identifier).build();
    Setting setting = Setting.builder().identifier(identifier).build();
    when(settingConfigurationRepository.findByIdentifier(identifier))
        .thenReturn(Optional.ofNullable(settingConfiguration));
    doNothing().when(settingConfigurationRepository).delete(settingConfiguration);
    when(settingRepository.findByIdentifier(identifier)).thenReturn(List.of(setting));
    doNothing().when(settingRepository).deleteAll(List.of(setting));
    settingsService.removeSettingFromConfiguration(identifier);
    verify(settingConfigurationRepository, times(1)).findByIdentifier(identifier);
    verify(settingConfigurationRepository, times(1)).delete(settingConfiguration);
    verify(settingRepository, times(1)).findByIdentifier(identifier);
    verify(settingRepository, times(1)).deleteAll(List.of(setting));
  }

  @Test
  @Owner(developers = NISHANT)
  @Category(UnitTests.class)
  public void testUpsertConfig() {
    String identifier = randomAlphabetic(10);
    String defaultValue = randomAlphabetic(10);
    SettingConfiguration settingConfiguration = SettingConfiguration.builder()
                                                    .identifier(identifier)
                                                    .defaultValue(defaultValue)
                                                    .valueType(SettingValueType.STRING)
                                                    .build();
    when(settingConfigurationRepository.save(settingConfiguration)).thenReturn(settingConfiguration);
    SettingConfiguration response = settingsService.upsertSettingConfiguration(settingConfiguration);
    verify(settingConfigurationRepository, times(1)).save(settingConfiguration);
    assertThat(response).isNotNull().isEqualTo(settingConfiguration);
  }
}
