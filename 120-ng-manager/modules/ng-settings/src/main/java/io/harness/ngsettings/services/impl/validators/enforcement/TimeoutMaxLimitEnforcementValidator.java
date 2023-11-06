/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngsettings.services.impl.validators.enforcement;

import io.harness.enforcement.beans.metadata.RestrictionMetadataDTO;
import io.harness.enforcement.beans.metadata.StaticLimitRestrictionMetadataDTO;
import io.harness.enforcement.client.EnforcementClient;
import io.harness.exception.InvalidRequestException;
import io.harness.ngsettings.dto.SettingDTO;
import io.harness.yaml.core.timeout.Timeout;

import com.google.inject.Inject;
import java.util.concurrent.TimeUnit;

public class TimeoutMaxLimitEnforcementValidator extends AbstractEnforcementValidator {
  @Inject
  public TimeoutMaxLimitEnforcementValidator(EnforcementClient enforcementClient) {
    super(enforcementClient);
  }

  @Override
  void checkStaticMaxLimit(RestrictionMetadataDTO currentRestriction, SettingDTO newSettingDTO) {
    Long limit = ((StaticLimitRestrictionMetadataDTO) currentRestriction).getLimit();
    Long settingValue = Timeout.fromString(newSettingDTO.getValue()).getTimeoutInMillis() / 1000;
    long hours = TimeUnit.SECONDS.toHours(limit);
    long days = TimeUnit.HOURS.toDays(hours);
    if (settingValue > limit) {
      if (hours < 24) {
        throw new InvalidRequestException(
            String.format("%s cannot be greater than %s hours for given account plan", newSettingDTO.getName(), hours));
      }
      throw new InvalidRequestException(
          String.format("%s cannot be greater than %s days for given account plan", newSettingDTO.getName(), days));
    }
  }
}
