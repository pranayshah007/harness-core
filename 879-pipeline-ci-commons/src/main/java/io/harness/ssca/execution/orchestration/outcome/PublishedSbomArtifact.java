/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.execution.orchestration.outcome;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@Value
@Builder
@OwnedBy(HarnessTeam.SSCA)
@TypeAlias("publishedSbomArtifact")
@RecasterAlias("io.harness.ssca.execution.orchestration.outcome.PublishedSbomArtifact")
public class PublishedSbomArtifact {
  String id;
  String url;
  String imageName;
  String tag;
  String digest;
  String sbomName;
  String sbomUrl;
  String stepExecutionId;
  boolean isSbomAttested;
  int allowListViolationCount;
  int denyListViolationCount;
}
