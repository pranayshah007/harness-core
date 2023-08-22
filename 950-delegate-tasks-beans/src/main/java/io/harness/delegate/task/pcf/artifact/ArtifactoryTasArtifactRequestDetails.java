/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.pcf.artifact;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.artifact.ArtifactMetadataKeys;
import io.harness.exception.InvalidArgumentsException;
import io.harness.expression.Expression;

import com.google.common.collect.ImmutableMap;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_PCF})
@Data
@Builder
@OwnedBy(CDP)
public class ArtifactoryTasArtifactRequestDetails implements TasArtifactRequestDetails {
  private String repositoryFormat;
  @Expression(ALLOW_SECRETS) private String repository;
  @Expression(ALLOW_SECRETS) private List<String> artifactPaths;

  public Map<String, String> toMetadata() {
    String artifactPath = Paths.get(repository, getArtifactPath()).toString();

    return ImmutableMap.of(
        ArtifactMetadataKeys.artifactName, artifactPath, ArtifactMetadataKeys.artifactPath, artifactPath);
  }

  public String getArtifactPath() {
    return artifactPaths.stream().findFirst().orElseThrow(
        () -> new InvalidArgumentsException("Expected at least a single artifact path"));
  }

  @Override
  public String getArtifactName() {
    return getArtifactPath();
  }
}
