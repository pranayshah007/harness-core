/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.commons.beans.recommendation.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class RecommendClusterRequest {
  Boolean allowBurst;
  Boolean allowOlderGen;
  List<String> category;
  List<String> excludes;
  List<String> includes;
  Long maxNodes;
  @Builder.Default Long minNodes = 3L;
  List<String> networkPerf;
  @Builder.Default Long onDemandPct = 100L;
  Boolean sameSize;
  Double sumCpu;
  @Builder.Default Long sumGpu = 0L;
  Double sumMem;
  String zone;
  Double minCpu;
  Double minMem;
}
