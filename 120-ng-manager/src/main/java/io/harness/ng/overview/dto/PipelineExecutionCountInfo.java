/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.overview.dto;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PipelineExecutionCountInfo {
  List<CountGroupedOnService> executionCountGroupedOnServiceList;

  @Value
  @Builder
  public static class CountGroupedOnService {
    String serviceReference;
    String serviceName;
    Long count;
    List<CountGroupedOnStatus> executionCountGroupedOnStatusList;
    List<CountGroupedOnArtifact> executionCountGroupedOnArtifactList;
  }

  @Value
  @Builder
  public static class CountGroupedOnArtifact {
    /*
    We have started syncing artifactDisplayName recently, to provide historical information without migration we have
    kept artifactPath and artifactVersion. We can remove these two over a period of time.
     */
    String artifactPath;
    String artifactVersion;
    String artifact;
    Long count;
    List<CountGroupedOnStatus> executionCountGroupedOnStatusList;
  }

  @Value
  @Builder
  public static class CountGroupedOnStatus {
    String status;
    Long count;
  }
}
