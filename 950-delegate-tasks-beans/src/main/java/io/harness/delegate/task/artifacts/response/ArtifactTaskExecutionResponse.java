/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.response;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.ami.AMITagsResponse;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.bamboo.BambooBuildTaskNGResponse;
import io.harness.delegate.task.jenkins.JenkinsBuildTaskNGResponse;
import io.harness.logging.CommandExecutionStatus;
import io.harness.shell.ExecuteCommandResponse;

import software.wings.helpers.ext.azure.devops.AzureArtifactsFeed;
import software.wings.helpers.ext.azure.devops.AzureArtifactsPackage;
import software.wings.helpers.ext.azure.devops.AzureDevopsProject;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.helpers.ext.jenkins.JobDetails;
import software.wings.helpers.ext.nexus.NexusRepositories;

import java.util.List;
import java.util.Map;
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
  List<AzureDevopsProject> azureArtifactsProjects;
  List<AzureArtifactsFeed> azureArtifactsFeeds;
  List<AzureArtifactsPackage> azureArtifactsPackages;
  AMITagsResponse amiTags;
  List<String> artifactPath;
  List<NexusRepositories> repositories;
  JenkinsBuildTaskNGResponse jenkinsBuildTaskNGResponse;
  BambooBuildTaskNGResponse bambooBuildTaskNGResponse;
  ExecuteCommandResponse executeCommandResponse;
  CommandExecutionStatus status;
  String errorMessage;
  Map<String, String> plans;
}
