/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.artifact.outcome;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.artifact.ArtifactSummary;
import io.harness.cdng.artifact.S3ArtifactSummary;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@Value
@Builder
@EqualsAndHashCode(callSuper = false)
@TypeAlias("S3ArtifactOutcome")
@JsonTypeName("s3ArtifactOutcome")
@OwnedBy(CDC)
@RecasterAlias("io.harness.ngpipeline.artifact.bean.S3ArtifactOutcome")
public class S3ArtifactOutcome implements ArtifactOutcome {
  /** AWS connector. */
  String connectorRef;

  /** region */
  String region;

  /** Bucket in repos */
  String bucketName;

  /** artifactPath refers to the exact filePath*/
  String filePath;

  /** filePathRegex regex is used to get latest artifactPaths from builds matching regex. */
  String filePathRegex;

  /** Identifier for artifact. */
  String identifier;

  /** Artifact type. */
  String type;

  /** Whether this config corresponds to primary artifact.*/
  boolean primaryArtifact;

  @Override
  public ArtifactSummary getArtifactSummary() {
    return S3ArtifactSummary.builder().bucketName(bucketName).filePath(filePath).tag(bucketName).build();
  }

  @Override
  public String getArtifactType() {
    return type;
  }

  @Override
  public String getTag() {
    return filePath;
  }
}
