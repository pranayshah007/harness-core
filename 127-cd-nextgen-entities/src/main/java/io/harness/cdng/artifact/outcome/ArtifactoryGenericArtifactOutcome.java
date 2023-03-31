/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.artifact.outcome;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.artifact.ArtifactSummary;
import io.harness.cdng.artifact.ArtifactoryGenericArtifactSummary;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.Map;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@Value
@Builder
@EqualsAndHashCode(callSuper = false)
@TypeAlias("artifactoryGenericArtifactOutcome")
@JsonTypeName("artifactoryGenericArtifactOutcome")
@OwnedBy(CDP)
// TODO : Create a shared Module b/w pipline and CD/CI where these entities can go to and eventually We need to
// deprecate that module 878-pms-coupling
// @TargetModule(878-pms-coupling)
@RecasterAlias("io.harness.cdng.artifact.outcome.ArtifactoryGenericArtifactOutcome")
public class ArtifactoryGenericArtifactOutcome implements ArtifactOutcome {
  /** Artifactory hub registry connector. */
  String connectorRef;
  /** Artifactory registry repository name. */
  String repositoryName;
  /** Images in repos need to be referenced via a path. */
  String artifactPath;
  /** Artifactory registry repository format. */
  String repositoryFormat;
  /** artifactDirectory refers to directory innside generic repo. */
  String artifactDirectory;
  /** Tag artifactPathFilter is used to get latest build from builds matching artifactPathFilter. */
  String artifactPathFilter;
  /** Identifier for artifact. */
  String identifier;
  /** Artifact type. */
  String type;
  /** Tag refers to exact tag number. */
  String tag;
  /** Whether this config corresponds to primary artifact.*/
  boolean primaryArtifact;
  /** Artifact Metadata. */
  Map<String, String> metadata;

  @Override
  public ArtifactSummary getArtifactSummary() {
    return ArtifactoryGenericArtifactSummary.builder()
        .artifactPath(getArtifactPath())
        .artifactDirectory(getArtifactDirectory())
        .tag(tag)
        .build();
  }

  @Override
  public String getArtifactType() {
    return type;
  }
}
