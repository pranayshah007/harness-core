/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.response;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.jenkins.JenkinsBuildTaskNGResponse;

import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.helpers.ext.jenkins.JobDetails;

import java.util.List;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

@Value
@Builder
@OwnedBy(PIPELINE)
public class ArtifactTaskExecutionResponse {
  @Singular List<ArtifactDelegateResponse> artifactDelegateResponses;
  boolean isArtifactServerValid;
  boolean isArtifactSourceValid;
  List<String> artifactImages;
  List<BuildDetails> buildDetails;
  List<JobDetails> jobDetails;
  List<String> artifactPath;
  JenkinsBuildTaskNGResponse jenkinsBuildTaskNGResponse;
}
