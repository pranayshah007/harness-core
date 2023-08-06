/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.kustomize;
import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;

import lombok.experimental.UtilityClass;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@OwnedBy(CDP)
@UtilityClass
public class KustomizeExceptionConstants {
  // Kustomize error
  public static final String ACCUMULATING_RESOURCES = "accumulating resources";
  public static final String EVALSYMLINK_FAILURE = "evalsymlink failure";
  public static final String RESOURCE_NOT_FOUND =
      "Kustomize manifest resource not found ${RESOURCE_PATH} inside kustomize base directory";

  // Kustomize Hint
  public static String EVALSYMLINK_ERROR_HINT =
      "All the resources that are required to compile the manifest must be present within Kustomize Base Path. Please check manifest(s) for any references to missing resources and create them.";
  public final String KUSTOMIZE_IO_EXCEPTION_HINT =
      "Please connect remotely to Harness delegate and verify network connection between Kubernetes cluster and Harness delegate.";
  public final String KUSTOMIZE_TIMEOUT_EXCEPTION_HINT =
      "Please connect remotely to Harness delegate and verify if Harness delegate is whitelisted to access Kubernetes API.";
  public final String KUSTOMIZE_BUILD_FAILED_HINT =
      "Please validate the path to the folder that contains the correct kustomization yaml file.\n- Validate the files that are being used to build the kustomize manifest.";

  // Kustomize Explanation
  public static String EVALSYMLINK_ERROR_EXPLANATION = "Manifests may contain references to some missing resource(s).";
  public final String KUSTOMIZE_IO_EXPLANATION = "Kustomize build failed due to I/O error";
  public final String KUSTOMIZE_TIMEOUT_EXPLANATION = "Time out while trying to build kustomize manifest.";
  public final String KUSTOMIZE_BUILD_FAILED_EXPLANATION = "Kustomize build failed due to invalid manifest resources.";
}
