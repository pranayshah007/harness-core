/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.statemachine.entities;

import io.harness.cvng.analysis.entities.HealthVerificationPeriod;
import io.harness.cvng.analysis.services.api.HealthVerificationService;
import io.harness.cvng.statemachine.beans.AnalysisState;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
// TODO: rename this to HealthVerificationState, this will also require thinking about migration as the classpath is
// saved in db
public class ActivityVerificationState extends AnalysisState {
  @JsonIgnore @Inject private transient HealthVerificationService healthVerificationService;
  private Instant preActivityVerificationStartTime;
  private Instant postActivityVerificationStartTime;
  private final StateType type = StateType.ACTIVITY_VERIFICATION;

  private String duration;

  @Builder.Default private Instant analysisCompletedUntil = Instant.ofEpochMilli(0);

  // pre vs post Activity
  private HealthVerificationPeriod healthVerificationPeriod;

  public Duration getDurationObj() {
    return Duration.parse(duration);
  }

  public void setDuration(Duration duration) {
    this.duration = duration.toString();
  }

  public boolean isAllAnalysesComplete() {
    boolean shouldTransition = true;
    if (analysisCompletedUntil.isBefore(getInputs().getEndTime())) {
      shouldTransition = false;
    }
    return shouldTransition;
  }
}
