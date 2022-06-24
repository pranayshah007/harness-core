/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngsettings.services.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.outbox.TransactionOutboxModule.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.springdata.TransactionUtils.DEFAULT_TRANSACTION_RETRY_POLICY;

import io.harness.annotations.dev.OwnedBy;
import io.harness.encryption.Scope;
import io.harness.ngsettings.SettingCategory;
import io.harness.ngsettings.SettingSource;
import io.harness.ngsettings.SettingUpdateType;
import io.harness.ngsettings.beans.SettingDTO;
import io.harness.ngsettings.beans.SettingRequestDTO;
import io.harness.ngsettings.beans.SettingResponseDTO;
import io.harness.ngsettings.beans.SettingValueRequestDTO;
import io.harness.ngsettings.beans.SettingValueResponseDTO;
import io.harness.ngsettings.entities.Setting;
import io.harness.ngsettings.entities.SettingConfiguration;
import io.harness.ngsettings.mapper.SettingsMapper;
import io.harness.ngsettings.services.SettingsService;
import io.harness.outbox.api.OutboxService;
import io.harness.repositories.SettingConfigurationRepository;
import io.harness.repositories.SettingsRepository;

import com.google.inject.Inject;
import com.google.inject.name.Named;

import java.util.*;
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

  @Override
  public List<SettingResponseDTO> list(
          String accountIdentifier, String orgIdentifier, String projectIdentifier, SettingCategory category) {
    List<SettingConfiguration> defaultSettings = settingConfigurationRepository.findByCategoryAndAllowedScopesIn(
            category, Arrays.asList(new String[]{ getScope(orgIdentifier, projectIdentifier)}));

    List<Setting> settings = settingsRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndCategory(
        accountIdentifier, orgIdentifier, projectIdentifier, category);
    Map<String, Setting> settingsMap = settings.stream().collect(
        Collectors.toMap(Setting::getIdentifier, Function.identity(), (o, n) -> o, HashMap::new));

    ListIterator<SettingConfiguration> settingsConfigurationIterator = defaultSettings.listIterator();
    List<SettingResponseDTO> settingResponseDTOList = new ArrayList<>();
    while (settingsConfigurationIterator.hasNext()) {
      SettingConfiguration settingConfiguration = settingsConfigurationIterator.next();
      SettingDTO settingsDTO = settingsMapper.getSettingDTO(settingConfiguration);
      settingsDTO.setOrgIdentifier(orgIdentifier);
      settingsDTO.setProjectIdentifier(projectIdentifier);
      String identifier = settingConfiguration.getIdentifier();
      SettingResponseDTO settingResponseDTO;
      if (settingsMap.containsKey(identifier)) {
        Setting setting = settingsMap.get(identifier);
        settingsDTO.setValue(setting.getValue());
        settingResponseDTO = SettingResponseDTO.builder()
                                 .setting(settingsDTO)
                                 .name(settingConfiguration.getName())
                                 .lastModifiedAt(setting.getLastModifiedAt())
                                 .settingSource(getSettingSource(orgIdentifier, projectIdentifier))
                                 .build();
      } else {
        settingResponseDTO = SettingResponseDTO.builder()
                                 .setting(settingsDTO)
                                 .name(settingConfiguration.getName())
                                 .lastModifiedAt(null)
                                 .settingSource(SettingSource.DEFAULT)
                                 .build();
      }
      settingResponseDTOList.add(settingResponseDTO);
    }
    return settingResponseDTOList;
  }

  @Override
  public List<SettingResponseDTO> update(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      List<SettingRequestDTO> settingRequestDTOList) {
    List<Setting> updatedSettingsList = new ArrayList<>();
    ListIterator<SettingRequestDTO> settingRequestDTOListIterator = settingRequestDTOList.listIterator();
    Failsafe.with(DEFAULT_TRANSACTION_RETRY_POLICY).get(() -> transactionTemplate.execute(status -> {
    while (settingRequestDTOListIterator.hasNext()) {
      SettingRequestDTO settingRequestDTO = settingRequestDTOListIterator.next();
      Optional<Setting> existingSetting =
          settingsRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndCategoryAndIdentifier(
              accountIdentifier, orgIdentifier, projectIdentifier, settingRequestDTO.getCategory(),
              settingRequestDTO.getIdentifier());
      if (settingRequestDTO.getUpdateType() == SettingUpdateType.RESTORE) {
        if (existingSetting.isPresent()) {
          settingsRepository.delete(existingSetting.get());
        } else {
          // ERROR
        }
      } else {
        if (existingSetting.isPresent()) {
          Setting updatedSetting = existingSetting.get();
          updatedSetting.setValue(settingRequestDTO.getValue());
          updatedSetting.setAllowOverrides(settingRequestDTO.getAllowOverrides());
          updatedSetting.setLastModifiedAt(System.currentTimeMillis());
          settingsRepository.save(updatedSetting);
        } else {
          Setting updatedSettingResponse = null;
          if (existingSetting.isPresent()) {
            Setting updatedSetting = existingSetting.get();
            updatedSetting.setValue(settingRequestDTO.getValue());
            updatedSetting.setAllowOverrides(settingRequestDTO.getAllowOverrides());
            updatedSetting.setLastModifiedAt(System.currentTimeMillis());
            updatedSettingResponse = settingsRepository.save(updatedSetting);
          } else {
            Optional<SettingConfiguration> defaultSetting = settingConfigurationRepository.findByCategoryAndIdentifier(
                settingRequestDTO.getCategory(), settingRequestDTO.getIdentifier());
            if (defaultSetting.isPresent()) {
              SettingConfiguration settingConfiguration = defaultSetting.get();
              SettingDTO settingDTO = settingsMapper.getSettingDTO(settingConfiguration);
              settingDTO.setOrgIdentifier(orgIdentifier);
              settingDTO.setProjectIdentifier(projectIdentifier);
              settingDTO.setValue(settingRequestDTO.getValue());
              settingDTO.setAllowOverrides(settingRequestDTO.getAllowOverrides());
              SettingResponseDTO settingResponseDTO =
                  SettingResponseDTO.builder()
                      .setting(settingDTO)
                      .name(settingConfiguration.getName())
                      .lastModifiedAt(System.currentTimeMillis())
                      .settingSource(getSettingSource(orgIdentifier, projectIdentifier))
                      .build();
              Setting newSetting = settingsMapper.getSetting(settingResponseDTO, accountIdentifier);
              updatedSettingResponse = settingsRepository.save(newSetting);
            } else {
              // ERROR
            }
          }
          if(updatedSettingResponse != null) {
            updatedSettingsList.add(updatedSettingResponse);
          }
        }
      }
      return updatedSettingsList.stream().map(setting -> settingsMapper.settingtoSettingResponseDTO(setting)).collect(Collectors.toList());
    }));
    return updatedSettingsList.stream().map(setting -> settingsMapper.settingtoSettingResponseDTO(setting)).collect(Collectors.toList());
  }

  @Override
  public SettingValueResponseDTO get(String identifier, SettingCategory category, String accountIdentifier, String orgIdentifier, String projectIdentifier,
      SettingValueRequestDTO settingValueRequestDTO) {
    Optional<Setting> existingSetting =
        settingsRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndCategoryAndIdentifier(
            accountIdentifier, orgIdentifier, projectIdentifier, category,
            identifier);
    Optional<SettingConfiguration> settingConfiguration = settingConfigurationRepository.findByCategoryAndIdentifier(
        category, identifier);
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
    Iterator<SettingConfiguration> settingConfigurationIterator = settingConfigurationRepository.findAll().iterator();
    while (settingConfigurationIterator.hasNext()) {
      settingConfigurationList.add(settingConfigurationIterator.next());
    }
    return settingConfigurationList;
  }

  @Override
  public void deleteConfig(String identifier) {
    Optional<SettingConfiguration> exisingSetting = settingConfigurationRepository.findById(identifier);
    if(exisingSetting.isPresent()) {
      settingConfigurationRepository.delete(exisingSetting.get());
    }
  }

  @Override
  public SettingConfiguration upsertConfig(SettingConfiguration settingConfiguration) {
    return (SettingConfiguration) settingConfigurationRepository.save(settingConfiguration);
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

  public static SettingSource getSettingSource(String orgIdentifier, String projectIdentifier) {
    if (orgIdentifier != null) {
      if (projectIdentifier != null) {
        return SettingSource.PROJECT;
      }
      return SettingSource.ORG;
    }
    return SettingSource.ACCOUNT;
  }
}
