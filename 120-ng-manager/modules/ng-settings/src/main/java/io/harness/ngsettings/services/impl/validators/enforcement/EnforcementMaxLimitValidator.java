/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngsettings.services.impl.validators.enforcement;

import static io.harness.remote.client.NGRestUtils.getResponse;

import static java.util.Objects.isNull;

import io.harness.enforcement.beans.metadata.FeatureRestrictionMetadataDTO;
import io.harness.enforcement.beans.metadata.RestrictionMetadataDTO;
import io.harness.enforcement.beans.metadata.StaticLimitRestrictionMetadataDTO;
import io.harness.enforcement.client.EnforcementClient;
import io.harness.enforcement.constants.FeatureRestrictionName;
import io.harness.exception.InvalidRequestException;
import io.harness.licensing.Edition;
import io.harness.ngsettings.dto.SettingDTO;
import io.harness.ngsettings.services.SettingEnforcementValidator;

import com.google.inject.Inject;
import javax.ws.rs.InternalServerErrorException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EnforcementMaxLimitValidator implements SettingEnforcementValidator {
  private final EnforcementClient enforcementClient;

  @Inject
  public EnforcementMaxLimitValidator(EnforcementClient enforcementClient) {
    this.enforcementClient = enforcementClient;
  }

  @Override
  public void validate(String accountIdentifier, FeatureRestrictionName featureRestrictionName,
      SettingDTO oldSettingDTO, SettingDTO newSettingDTO) {
    FeatureRestrictionMetadataDTO featureRestrictionMetadataDTO = null;
    try {
      featureRestrictionMetadataDTO =
          getResponse(enforcementClient.getFeatureRestrictionMetadata(featureRestrictionName, accountIdentifier));
    } catch (Exception ex) {
      log.error(
          "[SettingEnforcementValidator]: Error occurred while fetching enforcement metadata for featureRestrictionName: {}",
          featureRestrictionName, ex);
      throw new InternalServerErrorException("Failed to update the setting. Please try again later.");
    }
    if (isNull(featureRestrictionMetadataDTO)) {
      log.error(
          "[SettingEnforcementValidator]: FeatureRestrictionMetadataDTO returned as null for featureRestrictionName: {}",
          featureRestrictionName);
      throw new InternalServerErrorException("Failed to update the setting. Please try again later.");
    }
    Edition edition = featureRestrictionMetadataDTO.getEdition();
    RestrictionMetadataDTO currentRestriction = featureRestrictionMetadataDTO.getRestrictionMetadata().get(edition);
    if (isNull(currentRestriction)) {
      log.error(
          "[SettingEnforcementValidator]: FeatureRestrictionMetadataDTO contains no metadata for featureRestrictionName: {} for edition {}",
          featureRestrictionName, edition);
      throw new InternalServerErrorException("Failed to update the setting. Please try again later.");
    }

    switch (currentRestriction.getRestrictionType()) {
      case STATIC_LIMIT:
        checkStaticMaxLimit(currentRestriction, newSettingDTO);
        break;
      default:
        log.warn(
            "The setting validator EnforcementMaxLimitValidator does not have implementation for restriction type: {}",
            currentRestriction.getRestrictionType());
    }
  }

  private void checkStaticMaxLimit(RestrictionMetadataDTO currentRestriction, SettingDTO newSettingDTO) {
    Long limit = ((StaticLimitRestrictionMetadataDTO) currentRestriction).getLimit();
    Long settingValue = Long.parseLong(newSettingDTO.getValue());
    if (settingValue > limit) {
      throw new InvalidRequestException(
          String.format("%s cannot be greater than %s for your account plan", newSettingDTO.getName(), limit));
    }
  }
}
