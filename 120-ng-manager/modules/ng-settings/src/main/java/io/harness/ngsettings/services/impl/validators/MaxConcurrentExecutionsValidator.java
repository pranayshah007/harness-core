package io.harness.ngsettings.services.impl.validators;

import io.harness.MaxConcurrencyConfig;
import io.harness.MaxConcurrentExecutionsConfig;
import io.harness.exception.InvalidRequestException;
import io.harness.licensing.Edition;
import io.harness.licensing.services.LicenseService;
import io.harness.ngsettings.SettingGroupIdentifiers;
import io.harness.ngsettings.dto.SettingDTO;
import io.harness.ngsettings.services.SettingValidator;

import com.google.inject.Inject;

public class MaxConcurrentExecutionsValidator implements SettingValidator {
  @Inject private LicenseService licenseService;
  @Inject private MaxConcurrentExecutionsConfig maxConcurrentExecutionsConfig;

  @Override
  public void validate(String accountIdentifier, SettingDTO oldSettingDTO, SettingDTO newSettingDTO) {
    MaxConcurrencyConfig maxConcurrencyConfig;
    if (SettingGroupIdentifiers.MAX_CONCURRENT_NODES.equals(oldSettingDTO.getGroupIdentifier())) {
      maxConcurrencyConfig = maxConcurrentExecutionsConfig.getMaxConcurrentNodesConfig();
    } else if(SettingGroupIdentifiers.MAX_CONCURRENT_PIPELINE.equals(oldSettingDTO.getGroupIdentifier())){
      maxConcurrencyConfig = maxConcurrentExecutionsConfig.getMaxConcurrentPipelinesConfig();
    } else {
      return;
    }
    int minLimit = 1;
    int value = Integer.parseInt(newSettingDTO.getValue());
    if (value < minLimit) {
      throw new InvalidRequestException("Value should be at least " + minLimit);
    }
    Edition edition = licenseService.calculateAccountEdition(accountIdentifier);
    int maxLimit;
    switch (edition) {
      case TEAM:
        maxLimit = maxConcurrencyConfig.getTeam();
        break;
      case ENTERPRISE:
        maxLimit = maxConcurrencyConfig.getEnterprise();
        break;
      default:
        maxLimit = maxConcurrencyConfig.getFree();
    }
    if (value > maxLimit) {
      throw new InvalidRequestException(String.format(
          "Value exceeds the permitted limit of %s for %s Edition. Reach out to Harness support to have higher limits.",
          maxLimit, edition));
    }
  }
}
