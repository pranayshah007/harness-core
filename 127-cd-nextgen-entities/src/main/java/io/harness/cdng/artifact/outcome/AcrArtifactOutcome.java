/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.artifact.outcome;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.artifact.AcrArtifactSummary;
import io.harness.cdng.artifact.ArtifactSummary;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@Value
@Builder
@EqualsAndHashCode(callSuper = false)
@TypeAlias("acrArtifactOutcome")
@JsonTypeName("acrArtifactOutcome")
@OwnedBy(CDC)
@RecasterAlias("io.harness.ngpipeline.artifact.bean.AcrArtifactOutcome")
public class AcrArtifactOutcome implements ArtifactOutcome {
  /**
   * ACR registry connector.
   */
  String connectorRef;
  /**
   * subscription to which ACR registry belongs to.
   */
  String subscription;
  /**
   * registry name withing ACR.
   */
  String registry;
  /**
   * repository path.
   */
  String repository;
  /**
   * Tag refers to exact tag number.
   */
  String tag;
  /**
   * Tag regex is used to get latest build from builds matching regex.
   */
  String tagRegex;
  /**
   * Identifier for artifact.
   */
  String identifier;
  /**
   * Artifact type.
   */
  String type;
  /**
   * Whether this config corresponds to primary artifact.
   */
  boolean primaryArtifact;
  /**
   * registryHostName/imagePath:tag
   */
  String image;
  /**
   * imagePullSecret for Gcr credentials base encoded.
   */
  String imagePullSecret;
  /**
   * dockerConfigJson for docker credentials base encoded.
   */
  String dockerConfigJsonSecret;

  @Override
  public ArtifactSummary getArtifactSummary() {
    return AcrArtifactSummary.builder().imagePath(repository).tag(tag).build();
  }

  @Override
  public String getArtifactType() {
    return type;
  }
}
