/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.shell.ssh;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.VITALIE;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifacts.githubpackages.beans.GithubPackagesInternalConfig;
import io.harness.artifacts.githubpackages.service.GithubPackagesRegistryService;
import io.harness.beans.DecryptableEntity;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessType;
import io.harness.delegate.beans.connector.scm.github.GithubAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubCredentialsDTO;
import io.harness.delegate.beans.connector.scm.github.GithubHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.github.GithubHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.github.GithubHttpCredentialsSpecDTO;
import io.harness.delegate.beans.connector.scm.github.GithubTokenSpecDTO;
import io.harness.delegate.beans.connector.scm.github.GithubUsernameTokenDTO;
import io.harness.delegate.task.ssh.artifact.GithubPackagesArtifactDelegateConfig;
import io.harness.delegate.task.ssh.exception.SshExceptionConstants;
import io.harness.encryption.SecretRefData;
import io.harness.exception.HintException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;
import io.harness.security.encryption.SecretDecryptionService;

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
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(CDP)
public class GithubPackageArtifactCommandUnitHandlerTest extends CategoryTest {
  private static final String MAVEN_PACKAGE_TYPE = "maven";
  private static final String ARTIFACT_URL = "https://github.com/testuser/repo/myartifact-1.8.war";
  private static final String ARTIFACT_NAME = "myartifact-1.8.war";
  private static final String GITHUB_USERNAME = "username";
  private static final String GITHUB_TOKEN_DECRYPTED_VALUE = "decryptedValue";

  @Mock private SecretDecryptionService secretDecryptionService;
  @Mock DecryptableEntity decryptableEntity;
  @Mock LogCallback logCallback;

  @Mock GithubPackagesRegistryService githubPackagesRegistryService;

  @InjectMocks private GithubPackageArtifactCommandUnitHandler handler;

  @Before
  public void setup() {
    doReturn(decryptableEntity).when(secretDecryptionService).decrypt(any(), anyList());
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testDownloadFromRemoteRepoAuthUserPassword() throws IOException {
    InputStream artifactIS = new ByteArrayInputStream("githubArtifactContent".getBytes(Charset.defaultCharset()));
    SshExecutorFactoryContext context = getSshExecutorFactoryContext();
    when(githubPackagesRegistryService.downloadArtifactByUrl(
             any(GithubPackagesInternalConfig.class), any(String.class), any(String.class)))
        .thenReturn(Pair.of("githubArtifact", artifactIS));

    InputStream inputStream = handler.downloadFromRemoteRepo(context, logCallback);
    assertThat(new String(inputStream.readAllBytes())).isEqualTo("githubArtifactContent");

    ArgumentCaptor<GithubPackagesInternalConfig> githubPackagesInternalConfigCaptor =
        ArgumentCaptor.forClass(GithubPackagesInternalConfig.class);
    ArgumentCaptor<String> artifactNameCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> artifactUrlCaptor = ArgumentCaptor.forClass(String.class);

    verify(githubPackagesRegistryService)
        .downloadArtifactByUrl(
            githubPackagesInternalConfigCaptor.capture(), artifactNameCaptor.capture(), artifactUrlCaptor.capture());

    assertArtifactNameAndUrl(artifactNameCaptor, artifactUrlCaptor);
    assertRequestAuthUsernameToken(githubPackagesInternalConfigCaptor);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testDownloadFromRemoteExceptionally() {
    SshExecutorFactoryContext context = getSshExecutorFactoryContext();
    when(githubPackagesRegistryService.downloadArtifactByUrl(
             any(GithubPackagesInternalConfig.class), any(String.class), any(String.class)))
        .thenThrow(new InvalidArgumentsException("Invalid artifact path"));

    assertThatThrownBy(() -> handler.downloadFromRemoteRepo(context, logCallback))
        .hasMessage(SshExceptionConstants.GITHUB_PACKAGE_ARTIFACT_DOWNLOAD_HINT)
        .isInstanceOf(HintException.class);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testGetArtifactSize() {
    byte[] bytes = new byte[] {0, 1, 2, 3};
    InputStream artifactIS = new ByteArrayInputStream(bytes);
    SshExecutorFactoryContext context = getSshExecutorFactoryContext();
    when(githubPackagesRegistryService.downloadArtifactByUrl(
             any(GithubPackagesInternalConfig.class), any(String.class), any(String.class)))
        .thenReturn(Pair.of("githubArtifact", artifactIS));

    Long artifactSize = handler.getArtifactSize(context, logCallback);

    assertThat(artifactSize).isEqualTo(bytes.length);

    ArgumentCaptor<GithubPackagesInternalConfig> githubPackagesInternalConfigCaptor =
        ArgumentCaptor.forClass(GithubPackagesInternalConfig.class);
    ArgumentCaptor<String> artifactNameCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> artifactUrlCaptor = ArgumentCaptor.forClass(String.class);

    verify(githubPackagesRegistryService)
        .downloadArtifactByUrl(
            githubPackagesInternalConfigCaptor.capture(), artifactNameCaptor.capture(), artifactUrlCaptor.capture());

    assertArtifactNameAndUrl(artifactNameCaptor, artifactUrlCaptor);
    assertRequestAuthUsernameToken(githubPackagesInternalConfigCaptor);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testGetArtifactSizeExceptionally() {
    SshExecutorFactoryContext context = getSshExecutorFactoryContext();
    when(githubPackagesRegistryService.downloadArtifactByUrl(
             any(GithubPackagesInternalConfig.class), any(String.class), any(String.class)))
        .thenThrow(new InvalidArgumentsException("Invalid artifact path"));

    assertThatThrownBy(() -> handler.downloadFromRemoteRepo(context, logCallback))
        .hasMessage(SshExceptionConstants.GITHUB_PACKAGE_ARTIFACT_DOWNLOAD_HINT)
        .isInstanceOf(HintException.class);
  }

  private SshExecutorFactoryContext getSshExecutorFactoryContext() {
    GithubHttpCredentialsSpecDTO githubHttpCredentialsSpecDTO =
        GithubUsernameTokenDTO.builder()
            .username(GITHUB_USERNAME)
            .tokenRef(SecretRefData.builder().decryptedValue(GITHUB_TOKEN_DECRYPTED_VALUE.toCharArray()).build())
            .build();
    GithubCredentialsDTO credentials = GithubHttpCredentialsDTO.builder()
                                           .type(GithubHttpAuthenticationType.USERNAME_AND_TOKEN)
                                           .httpCredentialsSpec(githubHttpCredentialsSpecDTO)
                                           .build();
    GithubAuthenticationDTO githubAuthenticationDTO =
        GithubAuthenticationDTO.builder().credentials(credentials).authType(GitAuthType.HTTP).build();

    GithubTokenSpecDTO githubTokenSpecDTO =
        GithubTokenSpecDTO.builder()
            .tokenRef(SecretRefData.builder().decryptedValue(GITHUB_TOKEN_DECRYPTED_VALUE.toCharArray()).build())
            .build();

    GithubApiAccessDTO githubApiAccessDTO =
        GithubApiAccessDTO.builder().type(GithubApiAccessType.TOKEN).spec(githubTokenSpecDTO).build();

    GithubConnectorDTO githubConnectorDTO =
        GithubConnectorDTO.builder().authentication(githubAuthenticationDTO).apiAccess(githubApiAccessDTO).build();
    ConnectorInfoDTO connectorInfoDTO =
        ConnectorInfoDTO.builder().connectorConfig(githubConnectorDTO).connectorType(ConnectorType.GITHUB).build();

    Map<String, String> metadata = new HashMap<>();
    metadata.put("url", ARTIFACT_URL);

    GithubPackagesArtifactDelegateConfig githubPackagesArtifactDelegateConfig =
        GithubPackagesArtifactDelegateConfig.builder()
            .packageType(MAVEN_PACKAGE_TYPE)
            .artifactUrl(ARTIFACT_URL)
            .identifier("identifier")
            .connectorDTO(connectorInfoDTO)
            .metadata(metadata)
            .encryptedDataDetails(Collections.emptyList())
            .build();

    return SshExecutorFactoryContext.builder().artifactDelegateConfig(githubPackagesArtifactDelegateConfig).build();
  }

  private void assertArtifactNameAndUrl(
      ArgumentCaptor<String> artifactNameCaptor, ArgumentCaptor<String> artifactUrlCaptor) {
    assertThat(artifactNameCaptor.getValue()).isEqualTo(ARTIFACT_NAME);
    assertThat(artifactUrlCaptor.getValue()).isEqualTo(ARTIFACT_URL);
  }

  private void assertRequestAuthUsernameToken(
      ArgumentCaptor<GithubPackagesInternalConfig> githubPackagesInternalConfigCaptor) {
    GithubPackagesInternalConfig githubPackagesInternalConfig = githubPackagesInternalConfigCaptor.getValue();
    assertThat(githubPackagesInternalConfig.getToken()).isEqualTo(GITHUB_TOKEN_DECRYPTED_VALUE);
    assertThat(githubPackagesInternalConfig.getUsername()).isEqualTo(GITHUB_USERNAME);
    assertThat(githubPackagesInternalConfig.hasCredentials()).isEqualTo(true);
  }
}
