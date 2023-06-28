/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.analysis.services.api;

import io.harness.cvng.analysis.beans.CanaryBlueGreenAdditionalInfo;
import io.harness.cvng.analysis.beans.Risk;
import io.harness.cvng.core.beans.LoadTestAdditionalInfo;
import io.harness.cvng.core.beans.SimpleVerificationAdditionalInfo;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;

import java.util.Optional;

public interface VerificationJobInstanceAnalysisService {
  Optional<Risk> getLatestRiskScore(String accountId, String verificationJobInstanceId);

  CanaryBlueGreenAdditionalInfo getCanaryBlueGreenAdditionalInfo(
      String accountId, VerificationJobInstance verificationJobInstance);

  LoadTestAdditionalInfo getLoadTestAdditionalInfo(String accountId, VerificationJobInstance verificationJobInstance);

  SimpleVerificationAdditionalInfo getSimpleVerificationAdditionalInfo(
      String accountId, VerificationJobInstance verificationJobInstance);

  void addDemoAnalysisData(
      String verificationTaskId, CVConfig cvConfig, VerificationJobInstance verificationJobInstance);
}
