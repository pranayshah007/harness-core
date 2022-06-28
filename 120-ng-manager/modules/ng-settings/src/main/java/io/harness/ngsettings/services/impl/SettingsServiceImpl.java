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
import io.harness.encryption.Scope;
import io.harness.exception.InvalidRequestException;
import io.harness.ngsettings.SettingCategory;
import io.harness.ngsettings.SettingSource;
import io.harness.ngsettings.SettingUpdateType;
import io.harness.ngsettings.SettingValueType;
import io.harness.ngsettings.dto.SettingDTO;
import io.harness.ngsettings.dto.SettingRequestDTO;
import io.harness.ngsettings.dto.SettingResponseDTO;
import io.harness.ngsettings.dto.SettingValueResponseDTO;
import io.harness.ngsettings.entities.Setting;
import io.harness.ngsettings.entities.SettingConfiguration;
import io.harness.ngsettings.mapper.SettingsMapper;
import io.harness.ngsettings.services.SettingsService;
import io.harness.outbox.api.OutboxService;
import io.harness.repositories.SettingConfigurationRepository;
import io.harness.repositories.SettingsRepository;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import org.springframework.transaction.support.TransactionTemplate;

@OwnedBy(PL)
@Slf4j
public class SettingsServiceImpl implements SettingsService {
  private final SettingConfigurationRepository settingConfigurationRepository;
  private final SettingsRepository settingsRepository;
  private final SettingsMapper settingsMapper;
  private final TransactionTemplate transactionTemplate;
  private final OutboxService outboxService;
  private final String SETTING_NOT_FOUND_MESSAGE = "Setting with identifier- [%s] does not exist";

  @Inject
  public SettingsServiceImpl(SettingConfigurationRepository settingConfigurationRepository,
      SettingsRepository settingsRepository, SettingsMapper settingsMapper,
      @Named(OUTBOX_TRANSACTION_TEMPLATE) TransactionTemplate transactionTemplate, OutboxService outboxService) {
    this.settingConfigurationRepository = settingConfigurationRepository;
    this.settingsRepository = settingsRepository;
    this.settingsMapper = settingsMapper;
    this.transactionTemplate = transactionTemplate;
    this.outboxService = outboxService;
  }

  public static SettingSource getSettingSource(String orgIdentifier, String projectIdentifier) {
    if (isNotEmpty(orgIdentifier)) {
      if (isNotEmpty(projectIdentifier)) {
        return SettingSource.PROJECT;
      }
      return SettingSource.ORG;
    }
    return SettingSource.ACCOUNT;
  }

  @Override
  public List<SettingResponseDTO> list(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, SettingCategory category) {
    List<SettingConfiguration> defaultSettings = settingConfigurationRepository.findByCategoryAndAllowedScopesIn(
        category, Collections.singletonList(getScope(orgIdentifier, projectIdentifier)));

    List<Setting> settings = settingsRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndCategory(
        accountIdentifier, orgIdentifier, projectIdentifier, category);
    Map<String, Setting> settingsMap = settings.stream().collect(
        Collectors.toMap(Setting::getIdentifier, Function.identity(), (o, n) -> o, HashMap::new));

    ListIterator<SettingConfiguration> settingsConfigurationIterator = defaultSettings.listIterator();
    List<SettingResponseDTO> settingResponseDTOList = new ArrayList<>();
    while (settingsConfigurationIterator.hasNext()) {
      SettingConfiguration settingConfiguration = settingsConfigurationIterator.next();
      String identifier = settingConfiguration.getIdentifier();
      SettingDTO settingsDTO;
      SettingSource settingSource = null;
      if (settingsMap.containsKey(identifier)) {
        Setting setting = settingsMap.get(identifier);
        settingsDTO = settingsMapper.getSettingDTO(settingConfiguration, setting);
        settingSource = getSettingSource(orgIdentifier, projectIdentifier);
      } else {
        settingsDTO = settingsMapper.getSettingDTO(settingConfiguration, orgIdentifier, projectIdentifier);
      }
      SettingResponseDTO settingResponseDTO = SettingResponseDTO.builder()
                                                  .setting(settingsDTO)
                                                  .name(settingConfiguration.getName())
                                                  .settingSource(settingSource)
                                                  .build();
      settingResponseDTOList.add(settingResponseDTO);
    }
    return settingResponseDTOList;
  }

  @Override
  public List<SettingResponseDTO> update(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      List<SettingRequestDTO> settingRequestDTOList) {
    ListIterator<SettingRequestDTO> settingRequestDTOListIterator = settingRequestDTOList.listIterator();
    return Failsafe.with(DEFAULT_TRANSACTION_RETRY_POLICY).get(() -> transactionTemplate.execute(status -> {
      List<Setting> updatedSettingsList = new ArrayList<>();
      while (settingRequestDTOListIterator.hasNext()) {
        SettingRequestDTO settingRequestDTO = settingRequestDTOListIterator.next();
        Optional<Setting> existingSettingOptional =
            settingsRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
                accountIdentifier, orgIdentifier, projectIdentifier, settingRequestDTO.getIdentifier());
        Setting existingSetting = null;
        if (existingSettingOptional.isPresent()) {
          existingSetting = existingSettingOptional.get();
        }
        if (settingRequestDTO.getUpdateType() == SettingUpdateType.RESTORE) {
          updatedSettingsList.add(
              restoreSetting(existingSetting, settingRequestDTO, accountIdentifier, orgIdentifier, projectIdentifier));
        } else {
          updatedSettingsList.add(
              updateSetting(existingSetting, settingRequestDTO, accountIdentifier, orgIdentifier, projectIdentifier));
        }
      }
      return updatedSettingsList.stream().map(settingsMapper::settingtoSettingResponseDTO).collect(Collectors.toList());
    }));
  }

  private Setting updateSetting(Setting existingSetting, SettingRequestDTO settingRequestDTO, String accountIdentifier,
      String orgIdentifier, String projectIdentifier) {
    if (existingSetting != null) {
      Optional<SettingConfiguration> settingConfigurationOptional =
          settingConfigurationRepository.findByIdentifier(existingSetting.getIdentifier());
      if (!settingConfigurationOptional.isPresent()) {
        throw new InvalidRequestException(String.format(SETTING_NOT_FOUND_MESSAGE, settingRequestDTO.getIdentifier()));
      }
      SettingConfiguration settingConfiguration = settingConfigurationOptional.get();
      SettingValueType valueType = settingConfiguration.getValueType();
      Set<String> allowedValues = settingConfiguration.getAllowedValues();
      checkValueCanBeParsed(settingRequestDTO.getIdentifier(), valueType, settingRequestDTO.getValue());
      checkValueIsAllowed(settingRequestDTO.getIdentifier(), allowedValues, settingRequestDTO.getValue());
      existingSetting.setValue(settingRequestDTO.getValue());
      existingSetting.setAllowOverrides(settingRequestDTO.getAllowOverrides());
      existingSetting.setLastModifiedAt(System.currentTimeMillis());
      return settingsRepository.save(existingSetting);
    } else {
      Optional<SettingConfiguration> defaultSetting =
          settingConfigurationRepository.findByIdentifier(settingRequestDTO.getIdentifier());
      if (!defaultSetting.isPresent()) {
        throw new InvalidRequestException(String.format(SETTING_NOT_FOUND_MESSAGE, settingRequestDTO.getIdentifier()));
      }
      SettingConfiguration settingConfiguration = defaultSetting.get();
      checkValueCanBeParsed(
          settingRequestDTO.getIdentifier(), settingConfiguration.getValueType(), settingRequestDTO.getValue());
      checkValueIsAllowed(
          settingRequestDTO.getIdentifier(), settingConfiguration.getAllowedValues(), settingRequestDTO.getValue());
      SettingDTO settingDTO = settingsMapper.getSettingDTO(settingConfiguration, orgIdentifier, projectIdentifier);
      settingDTO.setValue(settingRequestDTO.getValue());
      settingDTO.setAllowOverrides(settingRequestDTO.getAllowOverrides());
      SettingResponseDTO settingResponseDTO = SettingResponseDTO.builder()
                                                  .setting(settingDTO)
                                                  .name(settingConfiguration.getName())
                                                  .settingSource(getSettingSource(orgIdentifier, projectIdentifier))
                                                  .build();
      Setting newSetting = settingsMapper.getSetting(settingResponseDTO, accountIdentifier);
      return settingsRepository.save(newSetting);
    }
  }

  private Setting restoreSetting(Setting existingSetting, SettingRequestDTO settingRequestDTO, String accountIdentifier,
      String orgIdentifier, String projectIdentifier) {
    if (existingSetting != null) {
      settingsRepository.delete(existingSetting);
    }
    Optional<SettingConfiguration> defaultSetting =
        settingConfigurationRepository.findByIdentifier(settingRequestDTO.getIdentifier());
    if (!defaultSetting.isPresent()) {
      throw new InvalidRequestException(String.format(SETTING_NOT_FOUND_MESSAGE, settingRequestDTO.getIdentifier()));
    }
    return settingsMapper.settingConfigurationToSetting(
        defaultSetting.get(), accountIdentifier, orgIdentifier, projectIdentifier);
  }

  @Override
  public SettingValueResponseDTO get(
      String identifier, String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    Optional<Setting> existingSetting =
        settingsRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndIdentifier(
            accountIdentifier, orgIdentifier, projectIdentifier, identifier);
    Optional<SettingConfiguration> settingConfiguration = settingConfigurationRepository.findByIdentifier(identifier);
    if (!settingConfiguration.isPresent()) {
      throw new InvalidRequestException(String.format(SETTING_NOT_FOUND_MESSAGE, identifier));
    }
    String value;
    if (existingSetting.isPresent()) {
      value = existingSetting.get().getValue();
    } else {
      value = settingConfiguration.get().getDefaultValue();
    }
    return SettingValueResponseDTO.builder().valueType(settingConfiguration.get().getValueType()).value(value).build();
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
  public void deleteConfig(String identifier) {
    Optional<SettingConfiguration> exisingSettingConfig = settingConfigurationRepository.findByIdentifier(identifier);
    exisingSettingConfig.ifPresent(settingConfigurationRepository::delete);
    List<Setting> existingSettings = settingsRepository.findByIdentifier(identifier);
    existingSettings.forEach(settingsRepository::delete);
  }

  @Override
  public SettingConfiguration upsertConfig(SettingConfiguration settingConfiguration) {
    try {
      checkValueCanBeParsed(settingConfiguration.getIdentifier(), settingConfiguration.getValueType(),
          settingConfiguration.getDefaultValue());
      checkValueIsAllowed(settingConfiguration.getIdentifier(), settingConfiguration.getAllowedValues(),
          settingConfiguration.getDefaultValue());
      return settingConfigurationRepository.save(settingConfiguration);
    } catch (Exception e) {
      log.error(e.getMessage());
    }
    return null;
  }

  private void checkValueCanBeParsed(String identifier, SettingValueType valueType, String value) {
    switch (valueType) {
      case BOOLEAN:
        if (!(value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false"))) {
          throw new InvalidRequestException(
              String.format("For setting with identifier- %s, only boolean values are allowed. Recieved input- %s",
                  identifier, value));
        }
        break;
      case NUMBER:
        try {
          Double.parseDouble(value);
        } catch (Exception e) {
          throw new InvalidRequestException(
              String.format("For setting with identifier- %s, only numbered values are allowed. Recieved input- %s",
                  identifier, value));
        }
        break;
      default:
    }
  }

  private void checkValueIsAllowed(String identifier, Set<String> allowedValues, String value) {
    if (allowedValues != null && !allowedValues.contains(value)) {
      throw new InvalidRequestException(
          String.format("The value- \"%s\" is not allowed for the setting with id- %s", value, identifier));
    }
  }

  public String getScope(String orgIdentifier, String projectIdentifier) {
    if (orgIdentifier != null) {
      if (projectIdentifier != null) {
        return Scope.PROJECT.toString();
      }
      return Scope.ORG.toString();
    }
    return Scope.ACCOUNT.toString();
  }
}
