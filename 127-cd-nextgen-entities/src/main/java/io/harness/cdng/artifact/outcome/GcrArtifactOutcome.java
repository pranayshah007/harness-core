/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.artifact.outcome;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.artifact.ArtifactSummary;
import io.harness.cdng.artifact.GcrArtifactSummary;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.Map;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@Value
@Builder
@EqualsAndHashCode(callSuper = false)
@TypeAlias("gcrArtifactOutcome")
@JsonTypeName("gcrArtifactOutcome")
@OwnedBy(CDC)
@RecasterAlias("io.harness.ngpipeline.artifact.bean.GcrArtifactOutcome")
public class GcrArtifactOutcome implements ArtifactOutcome {
  /**
   * Docker hub registry connector.
   */
  String connectorRef;
  /**
   * Images in repos need to be referenced via a path.
   */
  String imagePath;
  /**
   * Tag refers to exact tag number.
   */
  String tag;
  /**
   * Tag regex is used to get latest build from builds matching regex.
   */
  String tagRegex;
  /**
   * RegistryHostname
   */
  String registryHostname;
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

  String digest;

  Map<String, String> metadata;
  Map<String, String> label;
  /**
   * dockerConfigJson for docker credentials base encoded.
   */
  String dockerConfigJsonSecret;

  @Override
  public ArtifactSummary getArtifactSummary() {
    return GcrArtifactSummary.builder().imagePath(imagePath).tag(tag).build();
  }

  @Override
  public String getArtifactType() {
    return type;
  }
}
