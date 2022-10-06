/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.api;

import io.harness.cvng.core.beans.params.ProjectParams;

import java.util.List;
import java.util.Map;

public interface CloudWatchService {
  Map fetchSampleData(ProjectParams projectParams, String connectorIdentifier, String tracingId, String expression,
      String region, String metricName, String metricIdentifier);

  List<String> fetchRegions();
}
