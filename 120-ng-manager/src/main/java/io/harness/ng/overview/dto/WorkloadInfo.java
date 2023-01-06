/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.overview.dto;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;
import org.apache.commons.lang3.tuple.Pair;

@Value
@Builder
public class WorkloadInfo {
  List<String> workloadsId;
  List<String> status;
  List<Pair<Long, Long>> timeInterval;
  List<String> deploymentTypeList;
  List<String> pipelineExecutionIdList;
  Map<String, String> uniqueWorkloadNameAndId;
}
