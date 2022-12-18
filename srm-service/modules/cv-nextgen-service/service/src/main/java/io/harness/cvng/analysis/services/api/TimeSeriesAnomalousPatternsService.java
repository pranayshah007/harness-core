/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.analysis.services.api;

import io.harness.cvng.analysis.beans.ServiceGuardTimeSeriesAnalysisDTO;
import io.harness.cvng.analysis.beans.TimeSeriesAnomaliesDTO;

import java.util.List;
import java.util.Map;

public interface TimeSeriesAnomalousPatternsService {
  void saveAnomalousPatterns(ServiceGuardTimeSeriesAnalysisDTO analysis, String verificationTaskId);
  Map<String, Map<String, List<TimeSeriesAnomaliesDTO>>> getLongTermAnomalies(String verificationTaskId);
}
