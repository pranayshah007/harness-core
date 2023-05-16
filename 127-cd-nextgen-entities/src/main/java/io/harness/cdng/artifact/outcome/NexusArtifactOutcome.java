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
import io.harness.cdng.artifact.NexusArtifactSummary;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.Map;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@Value
@Builder
@EqualsAndHashCode(callSuper = false)
@TypeAlias("nexusArtifactOutcome")
@JsonTypeName("nexusArtifactOutcome")
@OwnedBy(CDP)
// TODO : Create a shared Module b/w pipline and CD/CI where these entities can go to and eventually We need to
// deprecate that module 878-pms-coupling
// @TargetModule(878-pms-coupling)
@RecasterAlias("io.harness.cdng.artifact.outcome.NexusArtifactOutcome")
public class NexusArtifactOutcome implements ArtifactOutcome {
  /** Nexus hub registry connector. */
  String connectorRef;
  /** Nexus registry repository name. */
  String repositoryName;
  /** Artifacts in repos need to be referenced via a path. */
  String artifactPath;
  /** Nexus registry repository format. */
  String repositoryFormat;
  /** Tag refers to exact tag number. */
  String tag;
  /** Tag regex is used to get latest build from builds matching regex. */
  String tagRegex;
  /** Identifier for artifact. */
  String identifier;
  /** Artifact type. */
  String type;
  /** Whether this config corresponds to primary artifact.*/
  boolean primaryArtifact;
  /** domainName/imagePath:tag */
  String image;
  /** imagePullSecret for nexus credentials base encoded.*/
  String imagePullSecret;

  String registryHostname;

  String displayName;

  String digest;

  /** Artifact Metadata. */
  Map<String, String> metadata;

  Map<String, String> label;

  @Override
  public ArtifactSummary getArtifactSummary() {
    return NexusArtifactSummary.builder().artifactPath(getArtifactPath()).tag(getTag()).build();
  }

  @Override
  public String getArtifactType() {
    return type;
  }
}
