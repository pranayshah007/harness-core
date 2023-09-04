/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.artifact.outcome;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.artifact.ArtifactSummary;
import io.harness.cdng.artifact.DockerArtifactSummary;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.google.common.collect.Sets;
import java.util.Map;
import java.util.Set;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@CodePulse(
    module = ProductModule.CDS, components = {HarnessModuleComponent.CDS_ARTIFACTS}, unitCoverageRequired = false)
@Value
@Builder
@EqualsAndHashCode(callSuper = false)
@TypeAlias("dockerArtifactOutcome")
@JsonTypeName("dockerArtifactOutcome")
@OwnedBy(CDC)
@RecasterAlias("io.harness.ngpipeline.artifact.bean.DockerArtifactOutcome")
public class DockerArtifactOutcome implements ArtifactOutcome {
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
   * domainName/imagePath:tag
   */
  String image;
  /**
   * imagePullSecret for docker credentials base encoded.
   */
  String imagePullSecret;
  /**
   * tag label.
   */
  Map<String, String> label;
  /**
   * display Name
   */
  String displayName;
  /**
   * display Name
   */
  String digest;
  /**
   * Custom Artifact metadata
   */
  Map<String, String> metadata;
  /**
   * dockerConfigJson for docker credentials base encoded.
   */
  String dockerConfigJsonSecret;

  @Override
  public ArtifactSummary getArtifactSummary() {
    return DockerArtifactSummary.builder().imagePath(getImagePath()).tag(getTag()).build();
  }

  @Override
  public String getArtifactType() {
    return type;
  }

  @Override
  public Set<String> getMetaTags() {
    return Sets.newHashSet(getTag(), getImage(), getImagePath());
  }
}
