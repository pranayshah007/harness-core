/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.activity.beans;

import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.beans.activity.ActivityType;
import io.harness.cvng.beans.activity.ActivityVerificationStatus;

import java.util.Set;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@Builder
public class ActivityVerificationResultDTO {
  private ActivityType activityType;
  private String activityId;
  private String activityName;
  private long activityStartTime;
  private String environmentIdentifier;
  private String environmentName;
  private String serviceIdentifier;
  private long endTime;
  private long remainingTimeMs;
  @Builder.Default private int overallRisk = -1;
  private Set<CategoryRisk> preActivityRisks;
  private Set<CategoryRisk> postActivityRisks;
  int progressPercentage;
  ActivityVerificationStatus status;

  @Data
  @Builder
  @EqualsAndHashCode(onlyExplicitlyIncluded = true)
  public static class CategoryRisk {
    @EqualsAndHashCode.Include private CVMonitoringCategory category;

    private Double risk;
  }
}
