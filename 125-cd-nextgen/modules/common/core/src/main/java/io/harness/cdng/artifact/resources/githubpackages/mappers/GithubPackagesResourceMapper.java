/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.artifact.resources.githubpackages.mappers;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.artifact.resources.githubpackages.dtos.GithubPackageDTO;
import io.harness.cdng.artifact.resources.githubpackages.dtos.GithubPackagesResponseDTO;
import io.harness.delegate.task.artifacts.githubpackages.GithubPackagesArtifactDelegateResponse;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
@UtilityClass
public class GithubPackagesResourceMapper {
  public GithubPackagesResponseDTO toPackagesResponse(
      List<GithubPackagesArtifactDelegateResponse> githubPackagesArtifactDelegateResponses) {
    List<GithubPackageDTO> githubPackages = new ArrayList<>();

    for (GithubPackagesArtifactDelegateResponse response : githubPackagesArtifactDelegateResponses) {
      GithubPackageDTO githubPackage = GithubPackageDTO.builder()
                                           .packageId(response.getPackageId())
                                           .packageName(response.getPackageName())
                                           .packageUrl(response.getPackageUrl())
                                           .packageType(response.getPackageType())
                                           .visibility(response.getPackageVisibility())
                                           .build();

      githubPackages.add(githubPackage);
    }

    return GithubPackagesResponseDTO.builder().githubPackageResponse(githubPackages).build();
  }
}
