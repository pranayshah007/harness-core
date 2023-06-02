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

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EnforcementMaxLimitValidator extends AbstractEnforcementValidator {
  @Inject
  public EnforcementMaxLimitValidator(EnforcementClient enforcementClient) {
    super(enforcementClient);
  }

  @Override
  public void checkStaticMaxLimit(RestrictionMetadataDTO currentRestriction, SettingDTO newSettingDTO) {
    Long limit = ((StaticLimitRestrictionMetadataDTO) currentRestriction).getLimit();
    Long settingValue = Long.parseLong(newSettingDTO.getValue());
    if (settingValue > limit) {
      throw new InvalidRequestException(
          String.format("%s cannot be greater than %s for given account plan", newSettingDTO.getName(), limit));
    }
  }
}
