/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.artifact.outcome;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.artifact.ArtifactSummary;
import io.harness.cdng.artifact.GoogleCloudSourceArtifactSummary;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@Value
@Builder
@EqualsAndHashCode(callSuper = false)
@TypeAlias("GoogleCloudSourceArtifactOutcome")
@JsonTypeName("GoogleCloudSourceArtifactOutcome")
@OwnedBy(CDC)
@RecasterAlias("io.harness.ngpipeline.artifact.bean.GoogleCloudSourceArtifactOutcome")
public class GoogleCloudSourceArtifactOutcome implements ArtifactOutcome {
  /** Google Cloud Storage connector. */
  String connectorRef;

  /** project */
  String project;

  /** sourceDirectory */
  String sourceDirectory;

  /** Repository */
  String repository;

  /** Identifier for artifact. */
  String identifier;

  /** Artifact type. */
  String type;

  /** Branch */
  String branch;

  /** CommitId */
  String commitId;

  /** Tag */
  String gitTag;

  String fetchType;

  /** Whether this config corresponds to primary artifact.*/
  boolean primaryArtifact;
  @Override
  public ArtifactSummary getArtifactSummary() {
    return GoogleCloudSourceArtifactSummary.builder()
        .repository(repository)
        .sourceDirectory(sourceDirectory)
        .tag(gitTag)
        .branch(branch)
        .commitId(commitId)
        .build();
  }

  @Override
  public String getArtifactType() {
    return type;
  }

  @Override
  public String getTag() {
    return sourceDirectory;
  }
}
