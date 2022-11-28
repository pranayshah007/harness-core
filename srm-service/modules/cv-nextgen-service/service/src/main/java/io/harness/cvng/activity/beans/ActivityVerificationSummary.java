/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.activity.beans;

import io.harness.cvng.analysis.beans.Risk;
import io.harness.cvng.beans.activity.ActivityVerificationStatus;

import java.util.Map;
import lombok.Builder;
import lombok.Data;
import net.minidev.json.annotate.JsonIgnore;

@Data
@Builder
public class ActivityVerificationSummary {
  int total;
  int passed;
  int failed;
  int errors;
  int progress;
  int notStarted;
  int aborted;
  long remainingTimeMs;
  int progressPercentage;
  Long startTime;
  Long durationMs;
  Risk risk;
  @JsonIgnore Map<String, ActivityVerificationStatus> verficationStatusMap;

  public ActivityVerificationStatus getAggregatedStatus() {
    if (total == passed) {
      return ActivityVerificationStatus.VERIFICATION_PASSED;
    } else if (progress > 0) {
      return ActivityVerificationStatus.IN_PROGRESS;
    } else if (errors > 0) {
      return ActivityVerificationStatus.ERROR;
    } else if (failed > 0) {
      return ActivityVerificationStatus.VERIFICATION_FAILED;
    } else if (aborted > 0) {
      return ActivityVerificationStatus.ABORTED;
    }
    return ActivityVerificationStatus.NOT_STARTED;
  }
}
