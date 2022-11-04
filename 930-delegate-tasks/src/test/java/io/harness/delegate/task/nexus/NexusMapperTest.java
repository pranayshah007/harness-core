/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.nexus;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ABOSII;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.nexusconnector.NexusAuthType;
import io.harness.delegate.beans.connector.nexusconnector.NexusAuthenticationDTO;
import io.harness.delegate.beans.connector.nexusconnector.NexusConnectorDTO;
import io.harness.delegate.beans.connector.nexusconnector.NexusUsernamePasswordAuthDTO;
import io.harness.delegate.task.azure.artifact.NexusAzureArtifactRequestDetails;
import io.harness.encryption.SecretRefData;
import io.harness.nexus.NexusRequest;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDP)
public class NexusMapperTest extends CategoryTest {
  private static final String NEXUS_SERVER_URL = "https://nexus.dev";
  private static final String NEXUS_ARTIFACT_URL =
      "https://nexus.dev/repository/azure-webapp-maven/io/harness/test/hello-app/2.0.0/hello-app-2.0.0.jar";
  private final NexusMapper nexusMapper = new NexusMapper();

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void toNexusRequestAzureUsernamePassword() {
    final NexusConnectorDTO nexusConnectorDTO =
        NexusConnectorDTO.builder()
            .version("3.x")
            .nexusServerUrl(NEXUS_SERVER_URL)
            .auth(NexusAuthenticationDTO.builder()
                      .authType(NexusAuthType.USER_PASSWORD)
                      .credentials(
                          NexusUsernamePasswordAuthDTO.builder()
                              .username("username")
                              .passwordRef(SecretRefData.builder().decryptedValue("password".toCharArray()).build())
                              .build())
                      .build())
            .build();
    final NexusAzureArtifactRequestDetails requestDetails =
        NexusAzureArtifactRequestDetails.builder().certValidationRequired(true).artifactUrl(NEXUS_ARTIFACT_URL).build();

    NexusRequest nexusRequest = nexusMapper.toNexusRequest(nexusConnectorDTO, requestDetails);

    assertThat(nexusRequest.getNexusUrl()).isEqualTo(NEXUS_SERVER_URL);
    assertThat(nexusRequest.getUsername()).isEqualTo("username");
    assertThat(nexusRequest.getPassword()).isEqualTo("password".toCharArray());
    assertThat(nexusRequest.isHasCredentials()).isTrue();
    assertThat(nexusRequest.isCertValidationRequired()).isTrue();
    assertThat(nexusRequest.getArtifactRepositoryUrl()).isEqualTo(NEXUS_ARTIFACT_URL);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void toNexusRequestAzureAnonymous() {
    final NexusConnectorDTO nexusConnectorDTO =
        NexusConnectorDTO.builder()
            .version("3.x")
            .nexusServerUrl(NEXUS_SERVER_URL)
            .auth(NexusAuthenticationDTO.builder().authType(NexusAuthType.ANONYMOUS).build())
            .build();

    final NexusAzureArtifactRequestDetails requestDetails =
        NexusAzureArtifactRequestDetails.builder().certValidationRequired(true).artifactUrl(NEXUS_ARTIFACT_URL).build();

    NexusRequest nexusRequest = nexusMapper.toNexusRequest(nexusConnectorDTO, requestDetails);
    assertThat(nexusRequest.isHasCredentials()).isFalse();
    assertThat(nexusRequest.isCertValidationRequired()).isTrue();
    assertThat(nexusRequest.getArtifactRepositoryUrl()).isEqualTo(NEXUS_ARTIFACT_URL);
  }
}