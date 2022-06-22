package io.harness.ngsettings.api.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.outbox.TransactionOutboxModule.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.springdata.TransactionUtils.DEFAULT_TRANSACTION_RETRY_POLICY;

import io.harness.annotations.dev.OwnedBy;
import io.harness.encryption.Scope;
import io.harness.ng.core.setting.SettingCategory;
import io.harness.ng.core.setting.SettingSource;
import io.harness.ng.core.setting.SettingUpdateType;
import io.harness.ngsettings.api.SettingsService;
import io.harness.ngsettings.beans.*;
import io.harness.ngsettings.entities.Setting;
import io.harness.ngsettings.entities.SettingConfiguration;
import io.harness.ngsettings.mapper.SettingsMapper;
import io.harness.ngsettings.repositories.SettingConfigurationRepository;
import io.harness.ngsettings.repositories.SettingsRepository;
import io.harness.outbox.api.OutboxService;

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
    List<SettingConfiguration> defaultSettings = settingConfigurationRepository.findAllByCategoryAndAllowedScopes(
        category, getScope(orgIdentifier, projectIdentifier));

    List<Setting> settings =
        settingsRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndCategory(
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
          Optional<SettingConfiguration> defaultSetting = settingConfigurationRepository.findAByCategoryAndIdentifier(
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
            settingsRepository.save(newSetting);
          } else {
            // ERROR
          }
        }
      }
    }
      return null;
    }));
    return null;
  }

  @Override
  public SettingValueResponseDTO get(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      SettingValueRequestDTO settingValueRequestDTO) {
    Optional<Setting> existingSetting =
        settingsRepository.findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndCategoryAndIdentifier(
            accountIdentifier, orgIdentifier, projectIdentifier, settingValueRequestDTO.getCategory(),
            settingValueRequestDTO.getIdentifier());
    Optional<SettingConfiguration> settingConfiguration = settingConfigurationRepository.findAByCategoryAndIdentifier(
        settingValueRequestDTO.getCategory(), settingValueRequestDTO.getIdentifier());
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
    while(settingConfigurationIterator.hasNext()) {
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

  public SettingSource getSettingSource(String orgIdentifier, String projectIdentifier) {
    if (orgIdentifier != null) {
      if (projectIdentifier != null) {
        return SettingSource.PROJECT;
      }
      return SettingSource.ORG;
    }
    return SettingSource.ACCOUNT;
  }
}
