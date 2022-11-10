/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.shell.ssh;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.IVAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.nexusconnector.NexusAuthType;
import io.harness.delegate.beans.connector.nexusconnector.NexusAuthenticationDTO;
import io.harness.delegate.beans.connector.nexusconnector.NexusConnectorDTO;
import io.harness.delegate.beans.connector.nexusconnector.NexusUsernamePasswordAuthDTO;
import io.harness.delegate.task.nexus.NexusMapper;
import io.harness.delegate.task.ssh.artifact.NexusArtifactDelegateConfig;
import io.harness.encryption.SecretRefData;
import io.harness.exception.HintException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.logging.LogCallback;
import io.harness.nexus.NexusRequest;
import io.harness.rule.Owner;
import io.harness.security.encryption.SecretDecryptionService;

import software.wings.helpers.ext.nexus.NexusService;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(CDP)
public class NexusArtifactCommandUnitHandlerTest extends CategoryTest {
  private static final String ARTIFACT_URL =
      "https://nexus3.dev.harness.io/repository/maven-releases/mygroup/myartifact/1.8/myartifact-1.8.war";
  private static final String ARTIFACT_NAME = "myartifact-1.8.war";
  private static final String NEXUS_SERVER_URL = "https://nexus3.dev.harness.io/";
  private static final String NEXUS_VERSION = "3.1";
  private static final String NEXUS_USERNAME = "username";
  private static final String NEXUS_PWD_DECRYPTED_VALUE = "decryptedValue";

  @Mock private SecretDecryptionService secretDecryptionService;
  @Mock private NexusService nexusService;
  @Mock DecryptableEntity decryptableEntity;
  @Mock LogCallback logCallback;
  @InjectMocks @Spy private NexusMapper nexusMapper;

  @InjectMocks private NexusArtifactCommandUnitHandler nexusArtifactCommandUnitHandler;

  @Before
  public void setup() {
    doReturn(decryptableEntity).when(secretDecryptionService).decrypt(any(), anyList());
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testDownloadFromRemoteRepoAuthUserPassword() throws IOException {
    InputStream nexusArtifact = new ByteArrayInputStream("nexusArtifactContent".getBytes(Charset.defaultCharset()));
    SshExecutorFactoryContext context = getSshExecutorFactoryContext(NexusAuthType.USER_PASSWORD);
    when(nexusService.downloadArtifactByUrl(any(NexusRequest.class), any(String.class), any(String.class)))
        .thenReturn(Pair.of("nexusArtifact", nexusArtifact));

    InputStream inputStream = nexusArtifactCommandUnitHandler.downloadFromRemoteRepo(context, logCallback);
    assertThat(new String(inputStream.readAllBytes())).isEqualTo("nexusArtifactContent");

    ArgumentCaptor<NexusRequest> nexusRequestArgumentCaptor = ArgumentCaptor.forClass(NexusRequest.class);
    ArgumentCaptor<String> artifactNameCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> artifactUrlCaptor = ArgumentCaptor.forClass(String.class);

    verify(nexusService)
        .downloadArtifactByUrl(
            nexusRequestArgumentCaptor.capture(), artifactNameCaptor.capture(), artifactUrlCaptor.capture());

    assertArtifactNameAndUrl(artifactNameCaptor, artifactUrlCaptor);
    assertNexusRequestAuthUsernamePassword(nexusRequestArgumentCaptor);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testDownloadFromRemoteRepoAuthAnonymous() throws IOException {
    InputStream nexusArtifact = new ByteArrayInputStream("nexusArtifactContent".getBytes(Charset.defaultCharset()));
    SshExecutorFactoryContext context = getSshExecutorFactoryContext(NexusAuthType.ANONYMOUS);
    when(nexusService.downloadArtifactByUrl(any(NexusRequest.class), any(String.class), any(String.class)))
        .thenReturn(Pair.of("nexusArtifact", nexusArtifact));

    InputStream inputStream = nexusArtifactCommandUnitHandler.downloadFromRemoteRepo(context, logCallback);
    assertThat(new String(inputStream.readAllBytes())).isEqualTo("nexusArtifactContent");

    ArgumentCaptor<NexusRequest> nexusRequestArgumentCaptor = ArgumentCaptor.forClass(NexusRequest.class);
    ArgumentCaptor<String> artifactNameCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> artifactUrlCaptor = ArgumentCaptor.forClass(String.class);

    verify(nexusService)
        .downloadArtifactByUrl(
            nexusRequestArgumentCaptor.capture(), artifactNameCaptor.capture(), artifactUrlCaptor.capture());

    assertArtifactNameAndUrl(artifactNameCaptor, artifactUrlCaptor);
    assertNexusRequestAuthAnonymous(nexusRequestArgumentCaptor);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testDownloadFromRemoteExceptionally() {
    SshExecutorFactoryContext context = getSshExecutorFactoryContext(NexusAuthType.USER_PASSWORD);
    when(nexusService.downloadArtifactByUrl(any(NexusRequest.class), any(String.class), any(String.class)))
        .thenThrow(new InvalidArgumentsException("Invalid artifact path"));

    assertThatThrownBy(() -> nexusArtifactCommandUnitHandler.downloadFromRemoteRepo(context, logCallback))
        .hasMessage(
            "Please review the Nexus Artifact Details and check the repository and package details. We recommend also checking for the artifact on Nexus server")
        .isInstanceOf(HintException.class);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetArtifactSize() {
    final Long expectedArtifactSize = 1L;
    SshExecutorFactoryContext context = getSshExecutorFactoryContext(NexusAuthType.USER_PASSWORD);
    when(nexusService.getFileSize(any(NexusRequest.class), any(String.class), any(String.class)))
        .thenReturn(expectedArtifactSize);

    Long artifactSize = nexusArtifactCommandUnitHandler.getArtifactSize(context, logCallback);

    assertThat(artifactSize).isEqualTo(expectedArtifactSize);

    ArgumentCaptor<NexusRequest> nexusRequestArgumentCaptor = ArgumentCaptor.forClass(NexusRequest.class);
    ArgumentCaptor<String> artifactNameCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> artifactUrlCaptor = ArgumentCaptor.forClass(String.class);

    verify(nexusService)
        .getFileSize(nexusRequestArgumentCaptor.capture(), artifactNameCaptor.capture(), artifactUrlCaptor.capture());

    assertArtifactNameAndUrl(artifactNameCaptor, artifactUrlCaptor);
    assertNexusRequestAuthUsernamePassword(nexusRequestArgumentCaptor);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetArtifactSizeExceptionally() {
    SshExecutorFactoryContext context = getSshExecutorFactoryContext(NexusAuthType.USER_PASSWORD);
    when(nexusService.getFileSize(any(NexusRequest.class), any(String.class), any(String.class)))
        .thenThrow(new InvalidArgumentsException("Invalid artifact path"));

    assertThatThrownBy(() -> nexusArtifactCommandUnitHandler.getArtifactSize(context, logCallback))
        .hasMessage("Invalid artifact path")
        .isInstanceOf(InvalidArgumentsException.class);
  }

  private SshExecutorFactoryContext getSshExecutorFactoryContext(NexusAuthType nexusAuthType) {
    NexusUsernamePasswordAuthDTO credentials =
        NexusUsernamePasswordAuthDTO.builder()
            .username(NEXUS_USERNAME)
            .passwordRef(SecretRefData.builder().decryptedValue(NEXUS_PWD_DECRYPTED_VALUE.toCharArray()).build())
            .build();
    NexusAuthenticationDTO nexusAuthenticationDTO =
        NexusAuthenticationDTO.builder().credentials(credentials).authType(nexusAuthType).build();
    NexusConnectorDTO connectorDTO = NexusConnectorDTO.builder()
                                         .auth(nexusAuthenticationDTO)
                                         .nexusServerUrl(NEXUS_SERVER_URL)
                                         .version(NEXUS_VERSION)
                                         .build();
    ConnectorInfoDTO connectorInfoDTO =
        ConnectorInfoDTO.builder().connectorConfig(connectorDTO).connectorType(ConnectorType.NEXUS).build();
    Map<String, String> metadata = new HashMap<>();
    metadata.put("url", ARTIFACT_URL);

    NexusArtifactDelegateConfig nexusArtifactDelegateConfig = NexusArtifactDelegateConfig.builder()
                                                                  .artifactUrl(ARTIFACT_URL)
                                                                  .identifier("identifier")
                                                                  .isCertValidationRequired(false)
                                                                  .connectorDTO(connectorInfoDTO)
                                                                  .metadata(metadata)
                                                                  .encryptedDataDetails(Collections.emptyList())
                                                                  .build();
    return SshExecutorFactoryContext.builder().artifactDelegateConfig(nexusArtifactDelegateConfig).build();
  }

  private void assertArtifactNameAndUrl(
      ArgumentCaptor<String> artifactNameCaptor, ArgumentCaptor<String> artifactUrlCaptor) {
    assertThat(artifactNameCaptor.getValue()).isEqualTo(ARTIFACT_NAME);
    assertThat(artifactUrlCaptor.getValue()).isEqualTo(ARTIFACT_URL);
  }

  private void assertNexusRequestAuthUsernamePassword(ArgumentCaptor<NexusRequest> nexusRequestArgumentCaptor) {
    NexusRequest nexusRequest = nexusRequestArgumentCaptor.getValue();
    assertThat(nexusRequest.getNexusUrl()).isEqualTo(NEXUS_SERVER_URL);
    assertThat(nexusRequest.getArtifactRepositoryUrl()).isEqualTo(ARTIFACT_URL);
    assertThat(nexusRequest.getPassword()).isEqualTo(NEXUS_PWD_DECRYPTED_VALUE.toCharArray());
    assertThat(nexusRequest.getUsername()).isEqualTo(NEXUS_USERNAME);
    assertThat(nexusRequest.getVersion()).isEqualTo(NEXUS_VERSION);
    assertThat(nexusRequest.isCertValidationRequired()).isEqualTo(false);
    assertThat(nexusRequest.isHasCredentials()).isEqualTo(true);
  }

  private void assertNexusRequestAuthAnonymous(ArgumentCaptor<NexusRequest> nexusRequestArgumentCaptor) {
    NexusRequest nexusRequest = nexusRequestArgumentCaptor.getValue();
    assertThat(nexusRequest.getNexusUrl()).isEqualTo(NEXUS_SERVER_URL);
    assertThat(nexusRequest.getArtifactRepositoryUrl()).isEqualTo(ARTIFACT_URL);
    assertThat(nexusRequest.getVersion()).isEqualTo(NEXUS_VERSION);
    assertThat(nexusRequest.isCertValidationRequired()).isEqualTo(false);
    assertThat(nexusRequest.isHasCredentials()).isEqualTo(false);
  }
}
