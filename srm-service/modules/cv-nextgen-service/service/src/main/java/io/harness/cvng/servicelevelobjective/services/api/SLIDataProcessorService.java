/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.services.api;

import io.harness.cvng.servicelevelobjective.beans.SLIAnalyseRequest;
import io.harness.cvng.servicelevelobjective.beans.SLIAnalyseResponse;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelIndicatorDTO;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public interface SLIDataProcessorService {
  List<SLIAnalyseResponse> process(Map<String, List<SLIAnalyseRequest>> sliAnalyseRequest,
      ServiceLevelIndicatorDTO serviceLevelIndicatorDTO, Instant startTime, Instant endTime);
}
