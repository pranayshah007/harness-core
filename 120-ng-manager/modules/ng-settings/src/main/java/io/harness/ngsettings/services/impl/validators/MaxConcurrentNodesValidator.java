package io.harness.ngsettings.services.impl.validators;

import io.harness.exception.InvalidRequestException;
import io.harness.licensing.Edition;
import io.harness.licensing.services.LicenseService;
import io.harness.ngsettings.dto.SettingDTO;
import io.harness.ngsettings.services.SettingValidator;

import com.google.inject.Inject;

public class MaxConcurrentNodesValidator implements SettingValidator {
  @Inject private LicenseService licenseService;

  @Override
  public void validate(String accountIdentifier, SettingDTO oldSettingDTO, SettingDTO newSettingDTO) {
    int maxLimitFree = 5;
    int maxLimitTeam = 10;
    int maxLimitEnterprise = 20;
    int minLimit = 1;
    int value = Integer.parseInt(newSettingDTO.getValue());
    if (value < minLimit) {
      throw new InvalidRequestException("Value should be at least " + minLimit);
    }
    Edition edition = licenseService.calculateAccountEdition(accountIdentifier);
    int maxLimit;
    switch (edition) {
      case TEAM:
        maxLimit = maxLimitTeam;
        break;
      case ENTERPRISE:
        maxLimit = maxLimitEnterprise;
        break;
      default:
        maxLimit = maxLimitFree;
    }
    if (value > maxLimit) {
      throw new InvalidRequestException(String.format(
          "Value exceeds the permitted limit of %s for %s Edition. Reach out to Harness support to have higher limits.",
          maxLimit, edition));
    }
  }
}
