/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.artifactory;
import static io.harness.encryption.FieldWithPlainTextOrSecretValueHelper.getSecretAsStringFromPlainTextOrSecretRef;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.artifactory.ArtifactoryConfigRequest;
import io.harness.artifactory.ArtifactoryConfigRequest.ArtifactoryConfigRequestBuilder;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthType;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryUsernamePasswordAuthDTO;

import com.google.inject.Singleton;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
@Singleton
public class ArtifactoryRequestMapper {
  public ArtifactoryConfigRequest toArtifactoryRequest(ArtifactoryConnectorDTO artifactoryConnector) {
    final ArtifactoryConfigRequestBuilder artifactoryConfigRequestBuilder =
        ArtifactoryConfigRequest.builder()
            .artifactoryUrl(artifactoryConnector.getArtifactoryServerUrl())
            .hasCredentials(false);
    if (artifactoryConnector.getAuth().getAuthType() == ArtifactoryAuthType.USER_PASSWORD) {
      final ArtifactoryUsernamePasswordAuthDTO credentials =
          (ArtifactoryUsernamePasswordAuthDTO) artifactoryConnector.getAuth().getCredentials();
      artifactoryConfigRequestBuilder.hasCredentials(true)
          .password(credentials.getPasswordRef().getDecryptedValue())
          .username(getSecretAsStringFromPlainTextOrSecretRef(credentials.getUsername(), credentials.getUsernameRef()));
    }
    return artifactoryConfigRequestBuilder.build();
  }
}
