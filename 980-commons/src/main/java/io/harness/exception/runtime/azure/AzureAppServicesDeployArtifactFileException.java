/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.exception.runtime.azure;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;

import java.nio.file.Path;
import lombok.Getter;

@OwnedBy(CDP)
public class AzureAppServicesDeployArtifactFileException extends AzureAppServicesRuntimeException {
  @Getter private final String artifactFilePath;
  @Getter private final String artifactType;

  public AzureAppServicesDeployArtifactFileException(Path artifactFilePath, String artifactType, Throwable cause) {
    this(artifactFilePath.getNameCount() > 2
            ? artifactFilePath.subpath(artifactFilePath.getNameCount() - 2, artifactFilePath.getNameCount()).toString()
            : artifactFilePath.toString(),
        artifactType, cause);
  }

  public AzureAppServicesDeployArtifactFileException(String artifactFilePath, String artifactType, Throwable cause) {
    super(format("Failed to deploy artifact file: %s", artifactFilePath), cause);
    this.artifactFilePath = artifactFilePath;
    this.artifactType = artifactType;
  }
}
