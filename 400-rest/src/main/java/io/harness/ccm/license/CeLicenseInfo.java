/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.license;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Duration;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@OwnedBy(CE)
public class CeLicenseInfo {
  @JsonIgnore public static final int CE_TRIAL_GRACE_PERIOD_DAYS = 15;

  private CeLicenseType licenseType;
  private long expiryTime;

  @JsonIgnore
  public long getExpiryTimeWithGracePeriod() {
    // adding 15 days to long.max value becomes -ve
    long gracePeriodTime = expiryTime + Duration.ofDays(CE_TRIAL_GRACE_PERIOD_DAYS).toMillis();
    if (gracePeriodTime > expiryTime) {
      return gracePeriodTime;
    }
    return expiryTime;
  }

  @JsonIgnore
  public boolean isValidLicenceType() {
    switch (licenseType) {
      case LIMITED_TRIAL:
      case FULL_TRIAL:
      case PAID:
        return true;
      default:
        return false;
    }
  }
}
