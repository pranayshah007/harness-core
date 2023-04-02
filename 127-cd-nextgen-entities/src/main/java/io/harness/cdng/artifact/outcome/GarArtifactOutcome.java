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
import io.harness.cdng.artifact.GarArtifactSummary;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.Map;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@Value
@Builder
@EqualsAndHashCode(callSuper = false)
@TypeAlias("GarArtifactOutcome")
@JsonTypeName("GarArtifactOutcome")
@OwnedBy(CDC)
@RecasterAlias("io.harness.ngpipeline.artifact.bean.GarArtifactOutcome")
public class GarArtifactOutcome implements ArtifactOutcome {
  String connectorRef;
  String project;
  String repositoryName;
  String region;
  @JsonProperty("package") String pkg;
  String version;
  String versionRegex;
  String type;
  String identifier;
  String image;
  String registryHostname;
  String imagePullSecret;
  String repositoryType;
  Map<String, String> metadata;
  Map<String, String> label;
  boolean primaryArtifact;
  @Override
  public ArtifactSummary getArtifactSummary() {
    return GarArtifactSummary.builder()
        .pkg(pkg)
        .project(project)
        .region(region)
        .repositoryName(repositoryName)
        .version(version)
        .build();
  }
  @Override
  public String getArtifactType() {
    return type;
  }

  @Override
  public String getTag() {
    return version;
  }
}
