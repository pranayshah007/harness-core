/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.artifacts.azureartifacts.beans;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;

import lombok.experimental.UtilityClass;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
@OwnedBy(HarnessTeam.CDP)
@UtilityClass
public class AzureArtifactsExceptionConstants {
  public final String DOWNLOAD_FROM_AZURE_ARTIFACTS_FAILED =
      "Failed while trying to download artifact from AZURE_ARTIFACTS"
      + " with identifier: %s";

  public final String DOWNLOAD_FROM_AZURE_ARTIFACTS_HINT =
      "Please review the Artifact Details and check the Azure DevOps project/organization "
      + "feed of the artifact.";

  public final String DOWNLOAD_FROM_AZURE_ARTIFACTS_EXPLANATION =
      "Failed to download package artifact: %s with type: %s from"
      + " AZURE_ARTIFACTS feed: %s with version: %s";
}
