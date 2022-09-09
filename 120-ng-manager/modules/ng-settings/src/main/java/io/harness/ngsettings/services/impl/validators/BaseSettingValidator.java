package io.harness.ngsettings.services.impl.validators;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ngsettings.dto.SettingDTO;
import io.harness.ngsettings.services.SettingValidator;

import com.google.inject.Inject;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Slf4j
public class BaseSettingValidator implements SettingValidator {
  private final Map<String, SettingValidator> settingValidatorMap;

  @Inject
  public BaseSettingValidator(Map<String, SettingValidator> settingValidatorMap) {
    this.settingValidatorMap = settingValidatorMap;
  }

  @Override
  public void validate(String accountIdentifier, SettingDTO oldSettingDTO, SettingDTO newSettingDTO) {
    SettingValidator settingValidator = settingValidatorMap.get(oldSettingDTO.getIdentifier());
    if (settingValidator != null) {
      settingValidator.validate(accountIdentifier, oldSettingDTO, newSettingDTO);
    }
  }
}
