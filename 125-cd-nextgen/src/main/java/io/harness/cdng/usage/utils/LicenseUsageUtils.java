/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.usage.utils;

import static io.harness.licensing.usage.beans.cd.CDLicenseUsageConstants.SERVICE_INSTANCE_LIMIT;
import static io.harness.licensing.usage.beans.cd.CDLicenseUsageConstants.SERVICE_INSTANCE_LIMIT_FOR_LAMBDA;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.entities.InstanceType;

import java.time.Instant;
import java.time.Period;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.CDP)
@UtilityClass
public class LicenseUsageUtils {
  public static long computeLicenseConsumed(long serviceInstanceCount) {
    if (serviceInstanceCount <= SERVICE_INSTANCE_LIMIT) {
      return 1;
    } else {
      return ((serviceInstanceCount - 1) / SERVICE_INSTANCE_LIMIT) + 1;
    }
  }

  public static long computeLicenseConsumedForNG(long serviceInstanceCount, String instanceType) {
    if (InstanceType.AWS_LAMBDA_INSTANCE.toString().equals(instanceType)) {
      if (serviceInstanceCount <= SERVICE_INSTANCE_LIMIT_FOR_LAMBDA) {
        return 1;
      } else {
        return ((serviceInstanceCount - 1) / SERVICE_INSTANCE_LIMIT_FOR_LAMBDA) + 1;
      }
    }
    if (serviceInstanceCount <= SERVICE_INSTANCE_LIMIT) {
      return 1;
    } else {
      return ((serviceInstanceCount - 1) / SERVICE_INSTANCE_LIMIT) + 1;
    }
  }

  public static long getEpochMilliNDaysAgo(long timestamp, int days) {
    return Instant.ofEpochMilli(timestamp).minus(Period.ofDays(days)).toEpochMilli();
  }
}
