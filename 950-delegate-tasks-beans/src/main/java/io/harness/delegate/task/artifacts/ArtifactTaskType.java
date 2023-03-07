/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.Getter;

@OwnedBy(HarnessTeam.PIPELINE)
public enum ArtifactTaskType {
  GET_BUILDS("Get Builds"),
  GET_BUILD_NUMBER("Get Build Number"),
  GET_LAST_SUCCESSFUL_BUILD("Get last successful Build"),
  VALIDATE_ARTIFACT_SERVER("Validate Artifact Server"),
  VALIDATE_ARTIFACT_SOURCE("Validate Artifact Source"),
  GET_LABELS("Get Labels"),
  GET_PLANS("Get Plans"),
  GET_FEEDS("Get Feeds"),
  GET_IMAGE_URL("Get Image URL"),
  GET_AUTH_TOKEN("Get Auth Token"),
  GET_IMAGES("Get Images"),
  GET_JOBS("Get Jobs"),
  GET_ARTIFACT_PATH("Get Artifact Path"),
  GET_JOB_PARAMETERS("Get the Job Parameters"),
  JENKINS_BUILD("Trigger Jenkins Build"),
  JENKINS_POLL_TASK("Jenkins Poll TAsk"),
  GET_GITHUB_PACKAGES("Get Github Packages"),
  GET_NEXUS_REPOSITORIES("Get Nexus Repository"),
  GET_AZURE_PROJECTS("Get Azure Artifacts Projects"),
  GET_AZURE_PACKAGES("Get Azure Artifacts Packages"),
  GET_AZURE_FEEDS("Get Azure Artifacts Feeds"),
  GET_AMI_TAGS("Get AMI Tags"),
  BAMBOO_BUILD("Trigger Bamboo Build"),
  ;

  @Getter private final String displayName;

  ArtifactTaskType(String displayName) {
    this.displayName = displayName;
  }
}
