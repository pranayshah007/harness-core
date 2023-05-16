/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.googlecloudsource;

import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.response.ArtifactBuildDetailsNG;
import io.harness.delegate.task.artifacts.response.ArtifactDelegateResponse;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = false)
public class GoogleCloudSourceArtifactDelegateResponse extends ArtifactDelegateResponse {
  /** refers to GCP Project*/
  String project;
  /** refers to GCS repository*/
  String repository;
  /** refers to sourceDirectory in GCS repository*/
  String sourceDirectory;
  /** refers to branch in GCS repository*/
  String branch;
  /** refers to commitId in GCS repository*/
  String commitId;
  /** refers to tag in GCS repository*/
  String tag;

  @Builder
  public GoogleCloudSourceArtifactDelegateResponse(ArtifactBuildDetailsNG buildDetails, ArtifactSourceType sourceType,
      String project, String repository, String sourceDirectory, String branch, String commitId, String tag) {
    super(buildDetails, sourceType);

    this.project = project;
    this.repository = repository;
    this.sourceDirectory = sourceDirectory;
    this.branch = branch;
    this.commitId = commitId;
    this.tag = tag;
  }
}
