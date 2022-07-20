/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngsettings.services.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.outbox.TransactionOutboxModule.OUTBOX_TRANSACTION_TEMPLATE;

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
import io.harness.ngsettings.entities.SettingConfiguration;
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
      SettingCategory category, String group) {
    Map<String, SettingConfiguration> settingConfigurations =
        getSettingConfigurations(accountIdentifier, orgIdentifier, projectIdentifier, category, group);
    Map<String, Setting> settings = getSettings(accountIdentifier, orgIdentifier, projectIdentifier, category, group);
    // get parent scopes settings

    List<SettingResponseDTO> settingResponseDTOList = new ArrayList<>();
    settingConfigurations.forEach((identifier, settingConfiguration) -> {
      if (settings.containsKey(identifier)) {
        settingResponseDTOList.add(
            settingsMapper.writeSettingResponseDTO(settings.get(identifier), settingConfiguration, true));
      } else {
        // check for parent scopes in hierarchy
        Scope currentScope = Scope.of(accountIdentifier, orgIdentifier, projectIdentifier);
        settingResponseDTOList.add(getSettingResponseDTOFromParentScopes(currentScope, settingConfiguration));
      }
    });
    return settingResponseDTOList;
  }

  private SettingResponseDTO getSettingResponseDTOFromParentScopes(
      Scope currentScope, SettingConfiguration settingConfiguration) {
    while ((currentScope = getParentScope(currentScope)) != null) {
      Optional<Setting> setting =
          settingRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
              currentScope.getAccountIdentifier(), currentScope.getOrgIdentifier(), currentScope.getProjectIdentifier(),
              settingConfiguration.getIdentifier());
      if (setting.isPresent()) {
        return settingsMapper.writeSettingResponseDTO(
            setting.get(), settingConfiguration, setting.get().getAllowOverrides());
      }
    }
    return settingsMapper.writeSettingResponseDTO(settingConfiguration, settingConfiguration.getAllowOverrides());
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
          SettingConfiguration settingConfiguration =
              restoreSetting(accountIdentifier, orgIdentifier, projectIdentifier, settingRequestDTO);
          settingResponseDTO =
              settingsMapper.writeSettingResponseDTO(settingConfiguration, settingConfiguration.getAllowOverrides());
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
    if (getParentScope(currentScope) == null) {
      return;
    }
    while ((currentScope = getParentScope(currentScope)) != null) {
      Optional<Setting> setting =
          settingRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
              currentScope.getAccountIdentifier(), currentScope.getOrgIdentifier(), currentScope.getProjectIdentifier(),
              settingRequestDTO.getIdentifier());
      if (setting.isPresent()) {
        if (setting.get().getAllowOverrides()) {
          return;
        }
        throw new InvalidRequestException(
            String.format("Setting- %s cannot be overridden at the current scope", settingRequestDTO.getIdentifier()));
      }
    }
    Optional<SettingConfiguration> settingConfiguration =
        settingConfigurationRepository.findByIdentifier(settingRequestDTO.getIdentifier());
    if (settingConfiguration.isEmpty()) {
      throw new InvalidRequestException(String.format("Setting- %s does not exist", settingRequestDTO.getIdentifier()));
    }
    if (!settingConfiguration.get().getAllowOverrides()) {
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
      value = getSettingValueFromParentScope(Scope.of(accountIdentifier, orgIdentifier, projectIdentifier), identifier,
          settingConfiguration.getDefaultValue());
    }
    return SettingValueResponseDTO.builder().valueType(settingConfiguration.getValueType()).value(value).build();
  }

  private String getSettingValueFromParentScope(Scope currentScope, String identifier, String defaultValue) {
    while ((currentScope = getParentScope(currentScope)) != null) {
      Optional<Setting> setting =
          settingRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
              currentScope.getAccountIdentifier(), currentScope.getOrgIdentifier(), currentScope.getProjectIdentifier(),
              identifier);
      if (setting.isPresent()) {
        return setting.get().getValue();
      }
    }
    return defaultValue;
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

  private Map<String, Setting> getSettings(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      SettingCategory category, String group) {
    List<Setting> settings;
    if (group != null) {
      settings = settingRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndCategoryAndGroup(
          accountIdentifier, orgIdentifier, projectIdentifier, category, group);
    } else {
      settings = settingRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndCategory(
          accountIdentifier, orgIdentifier, projectIdentifier, category);
    }
    return settings.stream().collect(Collectors.toMap(Setting::getIdentifier, Function.identity()));
  }

  private Map<String, SettingConfiguration> getSettingConfigurations(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, SettingCategory category, String group) {
    Scope scope = Scope.of(accountIdentifier, orgIdentifier, projectIdentifier);
    List<ScopeLevel> scopes = Collections.singletonList(ScopeLevel.of(scope));
    List<SettingConfiguration> defaultSettingConfigurations;
    if (group != null) {
      defaultSettingConfigurations =
          settingConfigurationRepository.findByCategoryAndGroupAndAllowedScopesIn(category, group, scopes);
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
    if (settingOptional.isPresent()) {
      newSettingDTO = settingsMapper.writeNewDTO(settingOptional.get(), settingRequestDTO, settingConfiguration);
    } else {
      newSettingDTO =
          settingsMapper.writeNewDTO(orgIdentifier, projectIdentifier, settingRequestDTO, settingConfiguration);
    }
    if (settingRequestDTO.getAllowOverrides() == false) {
      deleteSettingInSubScopes(accountIdentifier, orgIdentifier, projectIdentifier, settingRequestDTO);
    }
    SettingUtils.validate(newSettingDTO);
    Setting setting = settingRepository.upsert(settingsMapper.toSetting(accountIdentifier, newSettingDTO));
    return settingsMapper.writeSettingResponseDTO(setting, settingConfiguration, true);
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

  private SettingConfiguration restoreSetting(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, SettingRequestDTO settingRequestDTO) {
    Optional<Setting> setting =
        settingRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
            accountIdentifier, orgIdentifier, projectIdentifier, settingRequestDTO.getIdentifier());
    SettingConfiguration settingConfiguration =
        getSettingConfiguration(accountIdentifier, orgIdentifier, projectIdentifier, settingRequestDTO.getIdentifier());
    setting.ifPresent(existingSetting -> {
      settingRepository.delete(existingSetting);
      if (settingConfiguration.getAllowOverrides() == false) {
        deleteSettingInSubScopes(accountIdentifier, orgIdentifier, projectIdentifier, settingRequestDTO);
      }
    });
    return settingConfiguration;
  }

  private void deleteSettingInSubScopes(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, SettingRequestDTO settingRequestDTO) {
    if (isEmpty(orgIdentifier)) {
      settingRepository.deleteByAccountIdentifierAndIdentifier(accountIdentifier, settingRequestDTO.getIdentifier());
    } else if (isEmpty(projectIdentifier)) {
      settingRepository.deleteByAccountIdentifierAndOrgIdentifierAndIdentifier(
          accountIdentifier, orgIdentifier, settingRequestDTO.getIdentifier());
    }
  }

  private Scope getParentScope(Scope currentScope) {
    if (isNotEmpty(currentScope.getProjectIdentifier())) {
      return Scope.builder()
          .accountIdentifier(currentScope.getAccountIdentifier())
          .orgIdentifier(currentScope.getOrgIdentifier())
          .build();
    } else if (isNotEmpty(currentScope.getOrgIdentifier())) {
      return Scope.builder().accountIdentifier(currentScope.getAccountIdentifier()).build();
    } else {
      return null;
    }
  }
}
