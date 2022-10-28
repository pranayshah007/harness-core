package io.harness.ngsettings.services.impl.validators;

import io.harness.MaxConcurrencyConfig;
import io.harness.MaxConcurrentExecutionsConfig;
import io.harness.exception.InvalidRequestException;
import io.harness.licensing.Edition;
import io.harness.licensing.services.LicenseService;
import io.harness.ngsettings.dto.SettingDTO;
import io.harness.ngsettings.services.SettingValidator;

import com.google.inject.Inject;

public class MaxConcurrentPipelinesValidator implements SettingValidator {
  @Inject private LicenseService licenseService;
  @Inject private MaxConcurrentExecutionsConfig maxConcurrentExecutionsConfig;

  @Override
  public void validate(String accountIdentifier, SettingDTO oldSettingDTO, SettingDTO newSettingDTO) {
    MaxConcurrencyConfig maxConcurrentPipelinesConfig = maxConcurrentExecutionsConfig.getMaxConcurrentPipelinesConfig();
    int minLimit = 1;
    int value = Integer.parseInt(newSettingDTO.getValue());
    if (value < minLimit) {
      throw new InvalidRequestException("Value should be at least " + minLimit);
    }
    Edition edition = licenseService.calculateAccountEdition(accountIdentifier);
    int maxLimit;
    switch (edition) {
      case TEAM:
        maxLimit = maxConcurrentPipelinesConfig.getTeam();
        break;
      case ENTERPRISE:
        maxLimit = maxConcurrentPipelinesConfig.getEnterprise();
        break;
      default:
        maxLimit = maxConcurrentPipelinesConfig.getFree();
    }
    if (value > maxLimit) {
      throw new InvalidRequestException(String.format(
          "Value exceeds the permitted limit of %s for %s Edition. Reach out to Harness support to have higher limits.",
          maxLimit, edition));
    }
  }
}
