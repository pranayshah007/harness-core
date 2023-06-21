/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngsettings.services.impl;

import static io.harness.rule.OwnerRule.NISHANT;
import static io.harness.rule.OwnerRule.TEJAS;

import static java.util.Optional.ofNullable;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.enforcement.constants.FeatureRestrictionName;
import io.harness.exception.EntityNotFoundException;
import io.harness.licensing.Edition;
import io.harness.licensing.services.LicenseService;
import io.harness.ngsettings.SettingCategory;
import io.harness.ngsettings.SettingSource;
import io.harness.ngsettings.SettingUpdateType;
import io.harness.ngsettings.SettingValueType;
import io.harness.ngsettings.SettingsValidatorFactory;
import io.harness.ngsettings.dto.SettingDTO;
import io.harness.ngsettings.dto.SettingRequestDTO;
import io.harness.ngsettings.dto.SettingResponseDTO;
import io.harness.ngsettings.dto.SettingUpdateResponseDTO;
import io.harness.ngsettings.dto.SettingValueResponseDTO;
import io.harness.ngsettings.entities.Setting;
import io.harness.ngsettings.entities.SettingConfiguration;
import io.harness.ngsettings.entities.SettingConfiguration.SettingConfigurationKeys;
import io.harness.ngsettings.events.SettingRestoreEvent;
import io.harness.ngsettings.events.SettingUpdateEvent;
import io.harness.ngsettings.mapper.SettingsMapper;
import io.harness.ngsettings.services.SettingEnforcementValidator;
import io.harness.ngsettings.services.SettingValidator;
import io.harness.ngsettings.utils.SettingUtils;
import io.harness.outbox.api.OutboxService;
import io.harness.repositories.ngsettings.spring.SettingConfigurationRepository;
import io.harness.repositories.ngsettings.spring.SettingRepository;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
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
  @Mock private SettingUtils settingUtils;
  private SettingsServiceImpl settingsService;
  @Mock private Map<String, SettingValidator> settingValidatorMap;
  @Mock private Map<String, SettingEnforcementValidator> settingEnforcementValidatorMap;
  @Mock private LicenseService licenseService;
  @Rule public ExpectedException exceptionRule = ExpectedException.none();
  private String defaultValue = "defaultValue";

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    settingsService = new SettingsServiceImpl(settingConfigurationRepository, settingRepository, settingsMapper,
        transactionTemplate, outboxService, settingValidatorMap, settingEnforcementValidatorMap, licenseService);
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
    mockStatic(SettingUtils.class);
    when(SettingUtils.getDefaultValue(any(), any())).thenReturn(defaultValue);
    when(settingRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndCategory(
             anyString(), any(), any(), any()))
        .thenReturn(new ArrayList<>());
    when(settingConfigurationRepository.findAll(any(Criteria.class)))
        .thenReturn(List.of(settingConfigurations.get(identifier)));
    when(settingsMapper.writeSettingDTO(settingConfiguration, true, defaultValue))
        .thenReturn(SettingDTO.builder().value(defaultValue).build());
    when(settingsMapper.toSetting(any(), any())).thenReturn(Setting.builder().build());
    when(licenseService.calculateAccountEdition(accountIdentifier)).thenReturn(Edition.ENTERPRISE);
    List<SettingResponseDTO> dtoList =
        settingsService.list(accountIdentifier, null, null, SettingCategory.CORE, null, false);
    verify(settingRepository, times(1)).findAll(any(Criteria.class));
    verify(settingConfigurationRepository, times(1)).findAll(any(Criteria.class));
    verify(settingsMapper, times(settingConfigurations.size()))
        .writeSettingResponseDTO(any(), any(), any(Boolean.class));
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
    when(licenseService.calculateAccountEdition(accountIdentifier)).thenReturn(Edition.ENTERPRISE);
    when(settingRepository.findAll(any(Criteria.class))).thenReturn(List.of(settings.get(identifier)));
    when(settingConfigurationRepository.findAll(any(Criteria.class)))
        .thenReturn(List.of(settingConfigurations.get(identifier)));
    when(settingsMapper.writeSettingResponseDTO(setting, settingConfiguration, true, defaultValue))
        .thenReturn(SettingResponseDTO.builder().setting(SettingDTO.builder().identifier(identifier).build()).build());
    when(settingsMapper.toSetting(any(), any())).thenReturn(setting);
    List<SettingResponseDTO> dtoList =
        settingsService.list(accountIdentifier, null, null, SettingCategory.CORE, null, false);
    verify(settingRepository, times(1)).findAll(any(Criteria.class));
    verify(settingConfigurationRepository, times(1)).findAll(any(Criteria.class));
    verify(settingsMapper, times(settings.size())).writeSettingResponseDTO(any(), any(), any(), any());
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
    SettingConfiguration settingConfiguration =
        SettingConfiguration.builder().identifier(identifier).defaultValue(defaultValue).build();
    SettingResponseDTO settingResponseDTO =
        SettingResponseDTO.builder().setting(SettingDTO.builder().identifier(identifier).build()).build();
    SettingUpdateResponseDTO settingBatchResponseDTO = SettingUpdateResponseDTO.builder()
                                                           .updateStatus(true)
                                                           .identifier(identifier)
                                                           .setting(settingResponseDTO.getSetting())
                                                           .build();
    mockStatic(SettingUtils.class);
    when(SettingUtils.getDefaultValue(any(), any())).thenReturn(defaultValue);
    when(SettingUtils.isSettingEditableForAccountEdition(any(), any())).thenReturn(true);
    when(SettingUtils.getSettingSource(setting)).thenReturn(SettingSource.ACCOUNT);
    when(settingRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             accountIdentifier, null, null, identifier))
        .thenReturn(ofNullable(setting));
    Setting updatedSetting = Setting.builder().identifier(identifier).build();
    when(settingRepository.upsert(updatedSetting)).thenReturn(updatedSetting);
    when(settingConfigurationRepository.findByIdentifierAndAllowedScopesIn(anyString(), any()))
        .thenReturn(ofNullable(settingConfiguration));
    when(settingsMapper.writeBatchResponseDTO(settingResponseDTO)).thenReturn(settingBatchResponseDTO);
    SettingDTO settingDTO = SettingDTO.builder().identifier(identifier).build();
    when(settingsMapper.writeNewDTO(setting, settingRequestDTO, settingConfiguration, true, defaultValue))
        .thenReturn(settingDTO);
    when(settingsMapper.writeSettingDTO(setting, settingConfiguration, true, settingConfiguration.getDefaultValue()))
        .thenReturn(settingDTO);
    when(settingsMapper.writeSettingDTO(settingConfiguration, true, defaultValue)).thenReturn(settingDTO);
    when(settingsMapper.toSetting(accountIdentifier, settingDTO)).thenReturn(updatedSetting);
    when(settingsMapper.toSetting(null, settingDTO)).thenReturn(setting);
    when(settingsMapper.writeSettingResponseDTO(setting, settingConfiguration, true)).thenReturn(settingResponseDTO);
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    List<SettingUpdateResponseDTO> batchResponse =
        settingsService.update(accountIdentifier, null, null, List.of(settingRequestDTO));
    verify(settingRepository, times(1))
        .findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
            accountIdentifier, null, null, identifier);
    verify(settingRepository, times(1)).delete(setting);
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
    SettingConfiguration settingConfiguration =
        SettingConfiguration.builder().identifier(identifier).defaultValue(defaultValue).build();
    SettingDTO settingDTO =
        SettingDTO.builder().identifier(identifier).valueType(SettingValueType.STRING).value(value).build();
    SettingResponseDTO settingResponseDTO = SettingResponseDTO.builder().setting(settingDTO).build();
    SettingUpdateResponseDTO settingBatchResponseDTO = SettingUpdateResponseDTO.builder()
                                                           .updateStatus(true)
                                                           .identifier(identifier)
                                                           .setting(settingResponseDTO.getSetting())
                                                           .build();
    mockStatic(SettingUtils.class);
    when(SettingUtils.getDefaultValue(any(), any())).thenReturn(defaultValue);
    when(SettingUtils.isSettingEditableForAccountEdition(any(), any())).thenReturn(true);
    when(settingRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             accountIdentifier, null, null, identifier))
        .thenReturn(ofNullable(setting));
    when(settingConfigurationRepository.findByIdentifierAndAllowedScopesIn(anyString(), any()))
        .thenReturn(ofNullable(settingConfiguration));
    when(settingsMapper.writeNewDTO(setting, settingRequestDTO, settingConfiguration, true, defaultValue))
        .thenReturn(settingDTO);
    when(settingRepository.upsert(newSetting)).thenReturn(newSetting);
    when(settingsMapper.toSetting(accountIdentifier, settingDTO)).thenReturn(newSetting);
    when(settingsMapper.toSetting(null, settingDTO)).thenReturn(setting);
    when(settingsMapper.writeSettingDTO(setting, settingConfiguration, true, defaultValue)).thenReturn(settingDTO);
    when(settingsMapper.writeSettingResponseDTO(newSetting, settingConfiguration, true, value))
        .thenReturn(settingResponseDTO);
    when(settingsMapper.writeBatchResponseDTO(settingResponseDTO)).thenReturn(settingBatchResponseDTO);
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    when(settingsMapper.writeSettingDTO(settingConfiguration, true, defaultValue)).thenReturn(settingDTO);
    List<SettingUpdateResponseDTO> batchResponse =
        settingsService.update(accountIdentifier, null, null, List.of(settingRequestDTO));
    verify(settingRepository, times(1))
        .findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
            accountIdentifier, null, null, identifier);
    verify(settingConfigurationRepository, times(1))
        .findByIdentifierAndAllowedScopesIn(identifier, List.of(ScopeLevel.ACCOUNT));
    verify(settingsMapper, times(0)).writeNewDTO(any(), any(), any(), any(), any(Boolean.class), any());
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
    verify(settingsMapper, times(0)).writeNewDTO(any(), any(), any(), any(), any());
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
    when(settingsMapper.toSetting(any(), any()))
        .thenReturn(Setting.builder().identifier(identifier).value(defaultValue).build());
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
    exceptionRule.expect(EntityNotFoundException.class);
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
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testListSettingsIncludingParentScopes() {
    String accountIdentifier = randomAlphabetic(10);
    String orgIdentifier = randomAlphabetic(10);
    String projectIdentifier = randomAlphabetic(10);

    Map<String, SettingConfiguration> settingConfigurations = new HashMap<>();

    String identifier1 = randomAlphabetic(10);
    SettingConfiguration settingConfiguration1 =
        SettingConfiguration.builder().identifier(identifier1).allowedScopes(Set.of(ScopeLevel.ACCOUNT)).build();
    settingConfigurations.put(identifier1, settingConfiguration1);

    String identifier2 = randomAlphabetic(10);
    SettingConfiguration settingConfiguration2 = SettingConfiguration.builder()
                                                     .identifier(identifier2)
                                                     .allowedScopes(Set.of(ScopeLevel.ACCOUNT, ScopeLevel.ORGANIZATION))
                                                     .build();
    settingConfigurations.put(identifier2, settingConfiguration2);

    String identifier3 = randomAlphabetic(10);
    SettingConfiguration settingConfiguration3 =
        SettingConfiguration.builder()
            .identifier(identifier3)
            .allowedScopes(Set.of(ScopeLevel.ACCOUNT, ScopeLevel.ORGANIZATION, ScopeLevel.PROJECT))
            .build();
    settingConfigurations.put(identifier3, settingConfiguration3);

    mockStatic(SettingUtils.class);
    when(SettingUtils.getDefaultValue(any(), any())).thenReturn(defaultValue);

    when(settingRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndCategory(
             anyString(), any(), any(), any()))
        .thenReturn(new ArrayList<>());

    when(settingConfigurationRepository.findAll(any(Criteria.class)))
        .thenReturn(List.of(settingConfiguration1, settingConfiguration2, settingConfiguration3));

    when(settingsMapper.writeSettingDTO(settingConfiguration1, true, defaultValue))
        .thenReturn(SettingDTO.builder().value(defaultValue).build());
    when(settingsMapper.writeSettingDTO(settingConfiguration2, true, defaultValue))
        .thenReturn(SettingDTO.builder().value(defaultValue).build());
    when(settingsMapper.writeSettingDTO(settingConfiguration3, true, defaultValue))
        .thenReturn(SettingDTO.builder().value(defaultValue).build());

    when(settingsMapper.toSetting(any(), any())).thenReturn(Setting.builder().build());
    when(licenseService.calculateAccountEdition(accountIdentifier)).thenReturn(Edition.ENTERPRISE);

    ArgumentCaptor<Criteria> criteriaArgumentCaptor = ArgumentCaptor.forClass(Criteria.class);

    List<SettingResponseDTO> dtoList =
        settingsService.list(accountIdentifier, orgIdentifier, projectIdentifier, SettingCategory.CORE, null, true);

    verify(settingRepository, times(1)).findAll(any(Criteria.class));

    //
    List<ScopeLevel> scopes = new ArrayList<>();
    scopes.addAll(Arrays.asList(ScopeLevel.PROJECT, ScopeLevel.ACCOUNT, ScopeLevel.ORGANIZATION));

    Criteria expectedCriteria =
        Criteria.where(SettingConfigurationKeys.category)
            .is(SettingCategory.CORE)
            .and(SettingConfigurationKeys.allowedScopes)
            .in(scopes)
            .orOperator(Criteria.where(SettingConfigurationKeys.allowedPlans).is(null),
                Criteria.where(SettingConfigurationKeys.allowedPlans + "." + Edition.ENTERPRISE.toString())
                    .exists(true));

    //
    verify(settingConfigurationRepository, times(1)).findAll(criteriaArgumentCaptor.capture());
    assertThat(expectedCriteria).isEqualTo(criteriaArgumentCaptor.getValue());
    assertThat(dtoList.size()).isEqualTo(settingConfigurations.size());
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
    settingsService.removeSetting(identifier);
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

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testEnforcementValidation() {
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
    SettingConfiguration settingConfiguration =
        SettingConfiguration.builder().identifier(identifier).defaultValue(defaultValue).build();
    SettingDTO settingDTO =
        SettingDTO.builder().identifier(identifier).valueType(SettingValueType.STRING).value(value).build();
    SettingResponseDTO settingResponseDTO = SettingResponseDTO.builder().setting(settingDTO).build();
    SettingUpdateResponseDTO settingBatchResponseDTO = SettingUpdateResponseDTO.builder()
                                                           .updateStatus(true)
                                                           .identifier(identifier)
                                                           .setting(settingResponseDTO.getSetting())
                                                           .build();
    mockStatic(SettingUtils.class);
    mockStatic(SettingsValidatorFactory.class);
    FeatureRestrictionName featureRestrictionName = mock(FeatureRestrictionName.class);
    SettingEnforcementValidator settingEnforcementValidator = mock(SettingEnforcementValidator.class);

    when(SettingUtils.getDefaultValue(any(), any())).thenReturn(defaultValue);
    when(SettingUtils.isSettingEditableForAccountEdition(any(), any())).thenReturn(true);
    when(settingRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
             accountIdentifier, null, null, identifier))
        .thenReturn(ofNullable(setting));
    when(settingConfigurationRepository.findByIdentifierAndAllowedScopesIn(anyString(), any()))
        .thenReturn(ofNullable(settingConfiguration));
    when(settingsMapper.writeNewDTO(setting, settingRequestDTO, settingConfiguration, true, defaultValue))
        .thenReturn(settingDTO);
    when(settingRepository.upsert(newSetting)).thenReturn(newSetting);
    when(settingsMapper.toSetting(accountIdentifier, settingDTO)).thenReturn(newSetting);
    when(settingsMapper.toSetting(null, settingDTO)).thenReturn(setting);
    when(settingsMapper.writeSettingDTO(setting, settingConfiguration, true, defaultValue)).thenReturn(settingDTO);
    when(settingsMapper.writeSettingResponseDTO(newSetting, settingConfiguration, true, value))
        .thenReturn(settingResponseDTO);
    when(settingsMapper.writeBatchResponseDTO(settingResponseDTO)).thenReturn(settingBatchResponseDTO);
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    when(settingsMapper.writeSettingDTO(settingConfiguration, true, defaultValue)).thenReturn(settingDTO);
    when(settingEnforcementValidatorMap.get(identifier)).thenReturn(settingEnforcementValidator);
    when(SettingsValidatorFactory.getFeatureRestrictionName(identifier)).thenReturn(featureRestrictionName);

    List<SettingUpdateResponseDTO> batchResponse =
        settingsService.update(accountIdentifier, null, null, List.of(settingRequestDTO));

    verify(settingRepository, times(1))
        .findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
            accountIdentifier, null, null, identifier);
    verify(settingConfigurationRepository, times(1))
        .findByIdentifierAndAllowedScopesIn(identifier, List.of(ScopeLevel.ACCOUNT));
    verify(settingsMapper, times(0)).writeNewDTO(any(), any(), any(), any(), any(Boolean.class), any());
    verify(settingEnforcementValidator, times(1))
        .validate(accountIdentifier, featureRestrictionName, settingDTO, settingDTO);
    verify(settingRepository, times(1)).upsert(newSetting);
    verify(outboxService, times(1)).save(any(SettingUpdateEvent.class));
    assertThat(batchResponse).contains(settingBatchResponseDTO);
  }
}
