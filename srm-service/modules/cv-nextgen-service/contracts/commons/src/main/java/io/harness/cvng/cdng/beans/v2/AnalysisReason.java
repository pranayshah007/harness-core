/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.cdng.beans.v2;

public enum AnalysisReason {
  CUSTOM_FAIL_FAST_THRESHOLD,
  ML_ANALYSIS,
  NO_CONTROL_DATA,
  NO_TEST_DATA,
  NO_FAIL_FAST_THRESHOLD_BREACHED

}
