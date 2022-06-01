/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.dashboard.services.api;

import io.harness.cvng.activity.beans.ActivityVerificationResultDTO.CategoryRisk;
import io.harness.cvng.analysis.beans.Risk;
import io.harness.cvng.analysis.entities.HealthVerificationPeriod;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

public interface HealthVerificationHeatMapService {
  void updateRisk(String verificationTaskId, Double overallRisk, Instant endTime,
      HealthVerificationPeriod healthVerificationPeriod);
  Optional<Risk> getVerificationRisk(String accountId, String verificationJobInstanceId);

  Set<CategoryRisk> getVerificationJobInstanceAggregatedRisk(
      String accountId, String verificationJobInstanceId, HealthVerificationPeriod healthVerificationPeriod);
}
