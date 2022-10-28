package io.harness.ngsettings.services.impl.validators;

import io.harness.MaxConcurrentExecutionsConfig;
import io.harness.exception.InvalidRequestException;
import io.harness.licensing.services.LicenseService;
import io.harness.ngsettings.dto.SettingDTO;
import io.harness.ngsettings.services.SettingValidator;
import io.harness.yaml.core.timeout.Timeout;

import com.google.inject.Inject;

public class PipelineLevelTimeoutValidator implements SettingValidator {
  @Inject private LicenseService licenseService;
  @Inject private MaxConcurrentExecutionsConfig maxConcurrentExecutionsConfig;

  @Override
  public void validate(String accountIdentifier, SettingDTO oldSettingDTO, SettingDTO newSettingDTO) {
    Timeout maxTimeout = Timeout.fromString("1h");
    Timeout minTimeout = Timeout.fromString("10s");

    Timeout newTimeout;
    try {
      newTimeout = Timeout.fromString(newSettingDTO.getValue());
    } catch (Exception ex) {
      throw new InvalidRequestException("Could not parse provided timeout. Please provide a valid timeout string.");
    }
    if (newTimeout.getTimeoutInMillis() > maxTimeout.getTimeoutInMillis()
        || newTimeout.getTimeoutInMillis() < minTimeout.getTimeoutInMillis()) {
      throw new InvalidRequestException(
          String.format("Provided timeout is not in permitted Range. Please provide the timeout between %s and %s",
              minTimeout.getTimeoutString(), maxTimeout.getTimeoutString()));
    }
  }
}
