/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.pipeline.helpers;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.exception.InvalidArgumentsException;
import io.harness.steps.matrix.StrategyParameters;

import software.wings.utils.ArtifactType;

import java.util.Locale;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(CDP)
public class ExecutionStrategyUtils {
  private static boolean isSSHServiceDefinitionType(ServiceDefinitionType serviceDefinitionType) {
    return ServiceDefinitionType.SSH.name().equals(serviceDefinitionType.name());
  }

  private static String artifactSSHTypeSuffix(ArtifactType artifactType) {
    if (ArtifactType.OTHER.equals(artifactType)) {
      return ArtifactType.WAR.name().toLowerCase(Locale.ROOT);
    } else if (ArtifactType.RPM.equals(artifactType)) {
      return ArtifactType.JAR.name().toLowerCase(Locale.ROOT);
    } else if (ArtifactType.ZIP.equals(artifactType)) {
      return ArtifactType.TAR.name().toLowerCase(Locale.ROOT);
    } else {
      return artifactType.name().toLowerCase(Locale.ROOT);
    }
  }

  private static String artifactWinRmTypeSuffix(ArtifactType artifactType) {
    if (ArtifactType.IIS_APP.equals(artifactType)) {
      return ArtifactType.IIS_APP.name().toLowerCase(Locale.ROOT);
    } else if (ArtifactType.IIS_VirtualDirectory.equals(artifactType)) {
      return ArtifactType.IIS_VirtualDirectory.name().toLowerCase(Locale.ROOT);
    } else if (ArtifactType.IIS.equals(artifactType)) {
      return ArtifactType.IIS.name().toLowerCase(Locale.ROOT);
    } else if (ArtifactType.OTHER.equals(artifactType)) {
      return ArtifactType.OTHER.name().toLowerCase(Locale.ROOT);
    }

    throw new InvalidArgumentsException(format("Unsupported artifact type found: %s", artifactType.name()));
  }

  public static String resolveArtifactTypeSuffix(
      ServiceDefinitionType serviceDefinitionType, StrategyParameters strategyParameters) {
    return isSSHServiceDefinitionType(serviceDefinitionType)
        ? artifactSSHTypeSuffix(strategyParameters.getArtifactType())
        : artifactWinRmTypeSuffix(strategyParameters.getArtifactType());
  }
}
