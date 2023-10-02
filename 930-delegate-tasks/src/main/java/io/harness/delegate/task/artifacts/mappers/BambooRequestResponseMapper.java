/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.mappers;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.connector.bamboo.BambooConstant;
import io.harness.delegate.beans.connector.bamboo.BambooUserNamePasswordDTO;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.bamboo.BambooArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.bamboo.BambooArtifactDelegateResponse;
import io.harness.encryption.FieldWithPlainTextOrSecretValueHelper;

import software.wings.beans.BambooConfig;
import software.wings.helpers.ext.jenkins.BuildDetails;

import lombok.experimental.UtilityClass;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
@UtilityClass
public class BambooRequestResponseMapper {
  public BambooConfig toBambooConfig(BambooArtifactDelegateRequest request) {
    String password = "";
    String username = "";
    if (request.getBambooConnectorDTO().getAuth() != null
        && request.getBambooConnectorDTO().getAuth().getCredentials() != null
        && request.getBambooConnectorDTO().getAuth().getAuthType().getDisplayName()
            == BambooConstant.USERNAME_PASSWORD) {
      BambooUserNamePasswordDTO credentials =
          (BambooUserNamePasswordDTO) request.getBambooConnectorDTO().getAuth().getCredentials();
      if (credentials.getPasswordRef() != null) {
        password = EmptyPredicate.isNotEmpty(credentials.getPasswordRef().getDecryptedValue())
            ? new String(credentials.getPasswordRef().getDecryptedValue())
            : "";
      }
      username = FieldWithPlainTextOrSecretValueHelper.getSecretAsStringFromPlainTextOrSecretRef(
          credentials.getUsername(), credentials.getUsernameRef());
    }
    return BambooConfig.builder()
        .bambooUrl(request.getBambooConnectorDTO().getBambooUrl())
        .password(password.toCharArray())
        .username(username)
        .build();
  }

  public BambooArtifactDelegateResponse toBambooArtifactDelegateResponse(
      BuildDetails buildDetails, BambooArtifactDelegateRequest attributeRequest) {
    String artifactPath = "";
    if (EmptyPredicate.isNotEmpty(attributeRequest.getArtifactPaths())
        && EmptyPredicate.isNotEmpty(attributeRequest.getArtifactPaths().get(0))) {
      artifactPath = attributeRequest.getArtifactPaths().get(0);
    }
    return BambooArtifactDelegateResponse.builder()
        .buildDetails(ArtifactBuildDetailsMapper.toBuildDetailsNG(buildDetails))
        .sourceType(ArtifactSourceType.BAMBOO)
        .artifactPath(artifactPath)
        .build(buildDetails.getNumber())
        .planKey(attributeRequest.getPlanKey())
        .build();
  }
}
