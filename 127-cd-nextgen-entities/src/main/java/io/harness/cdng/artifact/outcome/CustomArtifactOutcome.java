/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.artifact.outcome;
import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.artifact.ArtifactSummary;
import io.harness.cdng.artifact.CustomArtifactSummary;
import io.harness.delegate.task.artifacts.ArtifactSourceType;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.Map;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
@Value
@Builder
@EqualsAndHashCode(callSuper = false)
@TypeAlias("customArtifactOutcome")
@JsonTypeName("customArtifactOutcome")
@OwnedBy(CDC)
@RecasterAlias("io.harness.cdng.artifact.outcome.customArtifactOutcome")
public class CustomArtifactOutcome implements ArtifactOutcome {
  /** Identifier for artifact. */
  String identifier;
  /** Whether this config corresponds to primary artifact.*/
  boolean primaryArtifact;
  /** Value that refers to exact artifact version. */
  String version;
  /** Value that refers to exact artifact version tag. */
  String image;
  /** artifactPath */
  String artifactPath;
  /** display Name*/
  String displayName;
  /** Custom Artifact metadata */
  Map<String, String> metadata;

  /* Field for adding support for <artifact.tag> */
  String tag;

  @Override
  public ArtifactSummary getArtifactSummary() {
    return CustomArtifactSummary.builder().version(version).build();
  }

  @Override
  public String getArtifactType() {
    return ArtifactSourceType.CUSTOM_ARTIFACT.getDisplayName();
  }

  @Override
  public String getTag() {
    return version;
  }
}
