/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.enforcement.handlers;

import static io.harness.remote.client.NGRestUtils.getResponse;

import io.harness.enforcement.beans.FeatureRestrictionUsageDTO;
import io.harness.enforcement.beans.metadata.RestrictionMetadataDTO;
import io.harness.enforcement.constants.FeatureRestrictionName;
import io.harness.enforcement.interfaces.EnforcementSdkSupportInterface;
import io.harness.exception.InvalidRequestException;
import io.harness.licensing.LicenseConstant;

import lombok.experimental.UtilityClass;

@UtilityClass
public class RestrictionUtils {
  public long getCurrentUsage(EnforcementSdkSupportInterface limitRestriction,
      FeatureRestrictionName featureRestrictionName, String accountIdentifier,
      RestrictionMetadataDTO restrictionMetadataDTO) {
    try {
      FeatureRestrictionUsageDTO response = getResponse(limitRestriction.getEnforcementSdkClient().getRestrictionUsage(
          featureRestrictionName, accountIdentifier, restrictionMetadataDTO));
      return response.getCount();
    } catch (Exception e) {
      throw new InvalidRequestException(String.format("Failed to query usage data for feature [%s] and accountId [%s]",
                                            featureRestrictionName.name(), accountIdentifier),
          e);
    }
  }

  public boolean isAvailable(long currentCount, long limit, boolean allowedIfEqual) {
    if (limit == LicenseConstant.UNLIMITED) {
      return true;
    }

    if (currentCount == limit) {
      return allowedIfEqual;
    } else {
      return currentCount < limit;
    }
  }
}
