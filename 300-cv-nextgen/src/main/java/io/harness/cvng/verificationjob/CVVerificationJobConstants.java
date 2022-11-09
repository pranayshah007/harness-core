/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.verificationjob;

public interface CVVerificationJobConstants {
  String JOB_IDENTIFIER_KEY = "jobIdentifier";
  String SERVICE_IDENTIFIER_KEY = "serviceIdentifier";
  String ENV_IDENTIFIER_KEY = "envIdentifier";
  String DURATION_KEY = "duration";
  String FAIL_ON_NO_ANALYSIS_KEY = "failOnNoAnalysis";
  String SENSITIVITY_KEY = "sensitivity";
  String RUNTIME_STRING = "<+input>";
  String TRAFFIC_SPLIT_PERCENTAGE_KEY = "trafficSplitPercentage";
}
