/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngsettings.services.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.outbox.TransactionOutboxModule.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.springdata.TransactionUtils.DEFAULT_TRANSACTION_RETRY_POLICY;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.beans.ScopeLevel;
import io.harness.exception.InvalidRequestException;
import io.harness.ngsettings.SettingCategory;
import io.harness.ngsettings.SettingUpdateType;
import io.harness.ngsettings.dto.SettingDTO;
import io.harness.ngsettings.dto.SettingRequestDTO;
import io.harness.ngsettings.dto.SettingResponseDTO;
import io.harness.ngsettings.dto.SettingUpdateResponseDTO;
import io.harness.ngsettings.dto.SettingValueResponseDTO;
import io.harness.ngsettings.entities.Setting;
import io.harness.ngsettings.entities.Setting.SettingKeys;
import io.harness.ngsettings.entities.SettingConfiguration;
import io.harness.ngsettings.events.SettingRestoreEvent;
import io.harness.ngsettings.events.SettingUpdateEvent;
import io.harness.ngsettings.mapper.SettingsMapper;
import io.harness.ngsettings.services.SettingsService;
import io.harness.ngsettings.utils.SettingUtils;
import io.harness.outbox.api.OutboxService;
import io.harness.repositories.ngsettings.spring.SettingConfigurationRepository;
import io.harness.repositories.ngsettings.spring.SettingRepository;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(PL)
@Slf4j
public class SettingsServiceImpl implements SettingsService {
  private final SettingConfigurationRepository settingConfigurationRepository;
  private final SettingRepository settingRepository;
  private final SettingsMapper settingsMapper;
  private final TransactionTemplate transactionTemplate;
  private final OutboxService outboxService;

  @Inject
  public SettingsServiceImpl(SettingConfigurationRepository settingConfigurationRepository,
      SettingRepository settingRepository, SettingsMapper settingsMapper,
      @Named(OUTBOX_TRANSACTION_TEMPLATE) TransactionTemplate transactionTemplate, OutboxService outboxService) {
    this.settingConfigurationRepository = settingConfigurationRepository;
    this.settingRepository = settingRepository;
    this.settingsMapper = settingsMapper;
    this.transactionTemplate = transactionTemplate;
    this.outboxService = outboxService;
  }

  @Override
  public List<SettingResponseDTO> list(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      SettingCategory category, String groupIdentifier) {
    Map<String, SettingConfiguration> settingConfigurations =
        getSettingConfigurations(accountIdentifier, orgIdentifier, projectIdentifier, category, groupIdentifier);
    Map<Pair<String, Scope>, Setting> settings =
        getSettings(accountIdentifier, orgIdentifier, projectIdentifier, category, groupIdentifier);
    List<SettingResponseDTO> settingResponseDTOList = new ArrayList<>();
    settingConfigurations.forEach((identifier, settingConfiguration) -> {
      Pair<String, Scope> currentScopeSettingKey =
          new ImmutablePair<>(identifier, Scope.of(accountIdentifier, orgIdentifier, projectIdentifier));
      Setting parentSetting = getSettingFromParentScope(
          Scope.of(accountIdentifier, orgIdentifier, projectIdentifier), identifier, settingConfiguration);
      if (settings.containsKey(currentScopeSettingKey)) {
        settingResponseDTOList.add(settingsMapper.writeSettingResponseDTO(
            settings.get(currentScopeSettingKey), settingConfiguration, true, parentSetting.getValue()));
      } else {
        Boolean isSettingEditable =
            ScopeLevel.of(accountIdentifier, orgIdentifier, projectIdentifier) == ScopeLevel.ACCOUNT
            || parentSetting.getAllowOverrides();
        settingResponseDTOList.add(
            settingsMapper.writeSettingResponseDTO(parentSetting, settingConfiguration, isSettingEditable));
      }
    });
    return settingResponseDTOList;
  }

  @Override
  public List<SettingUpdateResponseDTO> update(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      List<SettingRequestDTO> settingRequestDTOList) {
    List<SettingUpdateResponseDTO> settingResponses = new ArrayList<>();
    Scope currentScope = Scope.of(accountIdentifier, orgIdentifier, projectIdentifier);
    settingRequestDTOList.forEach(settingRequestDTO -> {
      try {
        SettingResponseDTO settingResponseDTO;
        checkOverridesAreAllowedInParentScope(currentScope, settingRequestDTO);
        if (settingRequestDTO.getUpdateType() == SettingUpdateType.RESTORE) {
          settingResponseDTO = restoreSetting(accountIdentifier, orgIdentifier, projectIdentifier, settingRequestDTO);
        } else {
          settingResponseDTO = updateSetting(accountIdentifier, orgIdentifier, projectIdentifier, settingRequestDTO);
        }
        settingResponses.add(settingsMapper.writeBatchResponseDTO(settingResponseDTO));
      } catch (Exception exception) {
        log.error("Error when updating setting:", exception);
        settingResponses.add(settingsMapper.writeBatchResponseDTO(settingRequestDTO.getIdentifier(), exception));
      }
    });
    return settingResponses;
  }

  private void checkOverridesAreAllowedInParentScope(Scope currentScope, SettingRequestDTO settingRequestDTO) {
    ScopeLevel currentScopeLevel = ScopeLevel.of(currentScope);
    if (currentScopeLevel.equals(ScopeLevel.ACCOUNT)) {
      return;
    }
    while ((currentScope = SettingUtils.getParentScope(currentScope)) != null) {
      Optional<Setting> setting =
          settingRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
              currentScope.getAccountIdentifier(), currentScope.getOrgIdentifier(), currentScope.getProjectIdentifier(),
              settingRequestDTO.getIdentifier());
      if (!setting.isPresent()) {
        continue;
      }
      if (Boolean.FALSE.equals(setting.get().getAllowOverrides())) {
        throw new InvalidRequestException(
            String.format("Setting- %s cannot be overridden at the current scope", settingRequestDTO.getIdentifier()));
      } else {
        return;
      }
    }
    Optional<SettingConfiguration> settingConfiguration =
        settingConfigurationRepository.findByIdentifier(settingRequestDTO.getIdentifier());
    if (settingConfiguration.isEmpty()) {
      throw new InvalidRequestException(String.format("Setting- %s does not exist", settingRequestDTO.getIdentifier()));
    }
    if (SettingUtils.getHighestScopeForSetting(settingConfiguration.get().getAllowedScopes())
            .equals(currentScopeLevel)) {
      return;
    }
    if (Boolean.FALSE.equals(settingConfiguration.get().getAllowOverrides())) {
      throw new InvalidRequestException(
          String.format("Setting- %s cannot be overridden at the current scope", settingRequestDTO.getIdentifier()));
    }
  }

  @Override
  public SettingValueResponseDTO get(
      String identifier, String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    SettingConfiguration settingConfiguration =
        getSettingConfiguration(accountIdentifier, orgIdentifier, projectIdentifier, identifier);
    Optional<Setting> existingSetting =
        settingRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
            accountIdentifier, orgIdentifier, projectIdentifier, identifier);
    String value;
    if (existingSetting.isPresent()) {
      value = existingSetting.get().getValue();
    } else {
      value = getSettingFromParentScope(
          Scope.of(accountIdentifier, orgIdentifier, projectIdentifier), identifier, settingConfiguration)
                  .getValue();
    }
    return SettingValueResponseDTO.builder().valueType(settingConfiguration.getValueType()).value(value).build();
  }

  private Setting getSettingFromParentScope(
      Scope currentScope, String identifier, SettingConfiguration settingConfiguration) {
    while ((currentScope = SettingUtils.getParentScope(currentScope)) != null) {
      Optional<Setting> setting =
          settingRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
              currentScope.getAccountIdentifier(), currentScope.getOrgIdentifier(), currentScope.getProjectIdentifier(),
              identifier);
      if (setting.isPresent()) {
        return setting.get();
      }
    }
    return settingsMapper.toSetting(null, settingsMapper.writeSettingDTO(settingConfiguration, true));
  }

  @Override
  public List<SettingConfiguration> listDefaultSettings() {
    List<SettingConfiguration> settingConfigurationList = new ArrayList<>();
    for (SettingConfiguration settingConfiguration : settingConfigurationRepository.findAll()) {
      settingConfigurationList.add(settingConfiguration);
    }
    return settingConfigurationList;
  }

  @Override
  public void removeSettingFromConfiguration(String identifier) {
    Optional<SettingConfiguration> exisingSettingConfig = settingConfigurationRepository.findByIdentifier(identifier);
    exisingSettingConfig.ifPresent(settingConfigurationRepository::delete);
    List<Setting> existingSettings = settingRepository.findByIdentifier(identifier);
    settingRepository.deleteAll(existingSettings);
  }

  @Override
  public SettingConfiguration upsertSettingConfiguration(SettingConfiguration settingConfiguration) {
    SettingUtils.validate(settingConfiguration);
    return settingConfigurationRepository.save(settingConfiguration);
  }

  private Map<Pair<String, Scope>, Setting> getSettings(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, SettingCategory category, String groupIdentifier) {
    List<Setting> settings;
    Criteria criteria =
        Criteria.where(SettingKeys.accountIdentifier)
            .is(accountIdentifier)
            .and(SettingKeys.category)
            .is(category)
            .andOperator(new Criteria().orOperator(
                Criteria.where(SettingKeys.orgIdentifier).is(null).and(SettingKeys.projectIdentifier).is(null),
                Criteria.where(SettingKeys.orgIdentifier).is(orgIdentifier).and(SettingKeys.projectIdentifier).is(null),
                Criteria.where(SettingKeys.orgIdentifier)
                    .is(orgIdentifier)
                    .and(SettingKeys.projectIdentifier)
                    .is(projectIdentifier)));
    if (isNotEmpty(groupIdentifier)) {
      criteria.and(SettingKeys.groupIdentifier).is(groupIdentifier);
    }
    settings = settingRepository.findAll(criteria);
    return settings.stream().collect(Collectors.toMap(setting
        -> new ImmutablePair<>(setting.getIdentifier(),
            Scope.of(accountIdentifier, setting.getOrgIdentifier(), setting.getProjectIdentifier())),
        Function.identity()));
  }

  private Map<String, SettingConfiguration> getSettingConfigurations(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, SettingCategory category, String groupIdentifier) {
    Scope scope = Scope.of(accountIdentifier, orgIdentifier, projectIdentifier);
    List<ScopeLevel> scopes = Collections.singletonList(ScopeLevel.of(scope));
    List<SettingConfiguration> defaultSettingConfigurations;
    if (isNotEmpty(groupIdentifier)) {
      defaultSettingConfigurations = settingConfigurationRepository.findByCategoryAndGroupIdentifierAndAllowedScopesIn(
          category, groupIdentifier, scopes);
    } else {
      defaultSettingConfigurations = settingConfigurationRepository.findByCategoryAndAllowedScopesIn(category, scopes);
    }
    return defaultSettingConfigurations.stream().collect(
        Collectors.toMap(SettingConfiguration::getIdentifier, Function.identity()));
  }

  private SettingResponseDTO updateSetting(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, SettingRequestDTO settingRequestDTO) {
    SettingConfiguration settingConfiguration =
        getSettingConfiguration(accountIdentifier, orgIdentifier, projectIdentifier, settingRequestDTO.getIdentifier());
    Optional<Setting> settingOptional =
        settingRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
            accountIdentifier, orgIdentifier, projectIdentifier, settingRequestDTO.getIdentifier());
    SettingDTO newSettingDTO;
    SettingDTO oldSettingDTO;
    if (settingOptional.isPresent()) {
      oldSettingDTO = settingsMapper.writeSettingDTO(
          settingOptional.get(), settingConfiguration, true, settingConfiguration.getDefaultValue());
      newSettingDTO = settingsMapper.writeNewDTO(settingOptional.get(), settingRequestDTO, settingConfiguration, true);
    } else {
      oldSettingDTO = settingsMapper.writeSettingDTO(settingConfiguration, true);
      newSettingDTO =
          settingsMapper.writeNewDTO(orgIdentifier, projectIdentifier, settingRequestDTO, settingConfiguration, true);
    }
    if (Boolean.FALSE.equals(settingRequestDTO.getAllowOverrides())) {
      deleteSettingInSubScopes(Scope.of(accountIdentifier, orgIdentifier, projectIdentifier), settingRequestDTO);
    }
    SettingUtils.validate(newSettingDTO);
    Setting setting = settingRepository.upsert(settingsMapper.toSetting(accountIdentifier, newSettingDTO));
    return Failsafe.with(DEFAULT_TRANSACTION_RETRY_POLICY).get(() -> transactionTemplate.execute(status -> {
      outboxService.save(new SettingUpdateEvent(accountIdentifier, oldSettingDTO, newSettingDTO));
      Setting parentSetting = getSettingFromParentScope(Scope.of(accountIdentifier, orgIdentifier, projectIdentifier),
          settingRequestDTO.getIdentifier(), settingConfiguration);
      return settingsMapper.writeSettingResponseDTO(setting, settingConfiguration, true, parentSetting.getValue());
    }));
  }

  private SettingConfiguration getSettingConfiguration(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String identifier) {
    Scope scope = Scope.of(accountIdentifier, orgIdentifier, projectIdentifier);
    Optional<SettingConfiguration> settingConfigurationOptional =
        settingConfigurationRepository.findByIdentifierAndAllowedScopesIn(
            identifier, Collections.singletonList(ScopeLevel.of(scope)));
    if (settingConfigurationOptional.isEmpty()) {
      throw new NotFoundException(String.format(
          "Setting [%s] is either invalid or is not applicable in scope [%s]", identifier, ScopeLevel.of(scope)));
    }
    return settingConfigurationOptional.get();
  }

  private SettingResponseDTO restoreSetting(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, SettingRequestDTO settingRequestDTO) {
    Optional<Setting> setting =
        settingRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
            accountIdentifier, orgIdentifier, projectIdentifier, settingRequestDTO.getIdentifier());
    SettingConfiguration settingConfiguration =
        getSettingConfiguration(accountIdentifier, orgIdentifier, projectIdentifier, settingRequestDTO.getIdentifier());
    SettingDTO settingDTO;
    SettingDTO oldSettingDTO;
    if (setting.isPresent()) {
      oldSettingDTO = settingsMapper.writeSettingDTO(
          setting.get(), settingConfiguration, true, settingConfiguration.getDefaultValue());
      settingDTO = settingsMapper.writeNewDTO(setting.get(), settingRequestDTO, settingConfiguration, true);
    } else {
      oldSettingDTO = settingsMapper.writeSettingDTO(settingConfiguration, true);
      settingDTO =
          settingsMapper.writeNewDTO(orgIdentifier, projectIdentifier, settingRequestDTO, settingConfiguration, true);
    }
    Setting parentSetting = getSettingFromParentScope(Scope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        settingRequestDTO.getIdentifier(), settingConfiguration);
    return Failsafe.with(DEFAULT_TRANSACTION_RETRY_POLICY).get(() -> transactionTemplate.execute(status -> {
      setting.ifPresent(settingRepository::delete);
      outboxService.save(new SettingRestoreEvent(accountIdentifier, oldSettingDTO, settingDTO));
      return settingsMapper.writeSettingResponseDTO(parentSetting, settingConfiguration, true);
    }));
  }

  private void deleteSettingInSubScopes(Scope currentScope, SettingRequestDTO settingRequestDTO) {
    ScopeLevel currentScopeLevel = ScopeLevel.of(currentScope);
    if (currentScopeLevel.equals(ScopeLevel.ACCOUNT)) {
      settingRepository.deleteByAccountIdentifierAndOrgIdentifierNotNullAndIdentifier(
          currentScope.getAccountIdentifier(), settingRequestDTO.getIdentifier());
    } else if (currentScopeLevel.equals(ScopeLevel.ORGANIZATION)) {
      settingRepository.deleteByAccountIdentifierAndOrgIdentifierAndProjectIdentifierNotNullAndIdentifier(
          currentScope.getAccountIdentifier(), currentScope.getOrgIdentifier(), settingRequestDTO.getIdentifier());
    }
  }
}
