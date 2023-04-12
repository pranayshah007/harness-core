/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.artifact.outcome;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.artifact.ArtifactSummary;
import io.harness.cdng.artifact.ArtifactoryArtifactSummary;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.Map;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@Value
@Builder
@EqualsAndHashCode(callSuper = false)
@TypeAlias("artifactoryArtifactOutcome")
@JsonTypeName("artifactoryArtifactOutcome")
@OwnedBy(CDP)
// TODO : Create a shared Module b/w pipline and CD/CI where these entities can go to and eventually We need to
// deprecate that module 878-pms-coupling
// @TargetModule(878-pms-coupling)
@RecasterAlias("io.harness.cdng.artifact.outcome.ArtifactoryArtifactOutcome")
public class ArtifactoryArtifactOutcome implements ArtifactOutcome {
  /**
   * Artifactory hub registry connector.
   */
  String connectorRef;
  /**
   * Artifactory registry repository name.
   */
  String repositoryName;
  /**
   * Images in repos need to be referenced via a path.
   */
  String artifactPath;
  /**
   * Artifactory registry repository format.
   */
  String repositoryFormat;
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
   * imagePullSecret for artifactory credentials base encoded.
   */
  String imagePullSecret;
  /**
   * dockerConfigJson for docker credentials base encoded.
   */
  String dockerConfigJsonSecret;

  String registryHostname;

  Map<String, String> metadata;

  Map<String, String> label;

  @Override
  public ArtifactSummary getArtifactSummary() {
    return ArtifactoryArtifactSummary.builder().artifactPath(getArtifactPath()).tag(getTag()).build();
  }

  @Override
  public String getArtifactType() {
    return type;
  }
}
