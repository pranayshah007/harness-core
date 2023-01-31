/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.cf.artifact;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.RISHABH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.helper.DecryptionHelper;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessType;
import io.harness.delegate.beans.connector.scm.github.GithubAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.github.GithubHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.github.GithubTokenSpecDTO;
import io.harness.delegate.beans.connector.scm.github.GithubUsernamePasswordDTO;
import io.harness.delegate.beans.connector.scm.github.GithubUsernameTokenDTO;
import io.harness.delegate.beans.pcf.artifact.TasArtifactRegistryType;
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDP)
public class TasGithubPackageRegistrySettingsProviderTest extends CategoryTest {
  @Mock DecryptionHelper decryptionHelper;
  TasGithubPackageRegistrySettingsProvider tasGithubPackageRegistrySettingsProvider =
      new TasGithubPackageRegistrySettingsProvider();
  private String username = "username";
  private String password = "password";

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    when(decryptionHelper.decrypt(any(), any())).thenReturn(null);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetContainerSettingsForGithubPackageRegistry() {
    GithubConnectorDTO githubConnectorDTO =
        GithubConnectorDTO.builder()
            .authentication(getGithubUserNameAuth())
            .apiAccess(GithubApiAccessDTO.builder()
                           .type(GithubApiAccessType.TOKEN)
                           .spec(GithubTokenSpecDTO.builder()
                                     .tokenRef(SecretRefData.builder().decryptedValue(password.toCharArray()).build())
                                     .build())
                           .build())
            .build();

    TasArtifactCreds containerSettingsResult = tasGithubPackageRegistrySettingsProvider.getContainerSettings(
        TasTestUtils.createTestContainerArtifactConfig(githubConnectorDTO, TasArtifactRegistryType.GCR),
        decryptionHelper);

    assertThat(containerSettingsResult.getPassword()).isEqualTo(password);
    assertThat(containerSettingsResult.getUsername()).isEqualTo(username);
    assertThat(containerSettingsResult.getUrl()).isEqualTo("https://test.registry.io/");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetContainerSettingsForGithubPackageRegistryToken() {
    GithubConnectorDTO githubConnectorDTO =
        GithubConnectorDTO.builder()
            .authentication(getGithubUserNameToken())
            .apiAccess(GithubApiAccessDTO.builder()
                           .type(GithubApiAccessType.TOKEN)
                           .spec(GithubTokenSpecDTO.builder()
                                     .tokenRef(SecretRefData.builder().decryptedValue(password.toCharArray()).build())
                                     .build())
                           .build())
            .build();

    TasArtifactCreds containerSettingsResult = tasGithubPackageRegistrySettingsProvider.getContainerSettings(
        TasTestUtils.createTestContainerArtifactConfig(githubConnectorDTO, TasArtifactRegistryType.GCR),
        decryptionHelper);

    assertThat(containerSettingsResult.getPassword()).isEqualTo(password);
    assertThat(containerSettingsResult.getUsername()).isEqualTo("token-username");
    assertThat(containerSettingsResult.getUrl()).isEqualTo("https://test.registry.io/");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetContainerSettingsForGithubPackageRegistryNullAPIToken() {
    GithubConnectorDTO githubConnectorDTO = GithubConnectorDTO.builder()
                                                .authentication(getGithubUserNameAuth())
                                                .apiAccess(GithubApiAccessDTO.builder()
                                                               .type(GithubApiAccessType.TOKEN)
                                                               .spec(GithubTokenSpecDTO.builder().build())
                                                               .build())
                                                .build();

    assertThatThrownBy(
        ()
            -> tasGithubPackageRegistrySettingsProvider.getContainerSettings(
                TasTestUtils.createTestContainerArtifactConfig(githubConnectorDTO, TasArtifactRegistryType.GCR),
                decryptionHelper))
        .hasMessage("The token reference for the Github Connector is null");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetContainerSettingsForGithubPackageRegistryInvalidAPIType() {
    GithubConnectorDTO githubConnectorDTO = GithubConnectorDTO.builder()
                                                .authentication(getGithubUserNameAuth())
                                                .apiAccess(GithubApiAccessDTO.builder()
                                                               .type(GithubApiAccessType.OAUTH)
                                                               .spec(GithubTokenSpecDTO.builder().build())
                                                               .build())
                                                .build();

    assertThatThrownBy(
        ()
            -> tasGithubPackageRegistrySettingsProvider.getContainerSettings(
                TasTestUtils.createTestContainerArtifactConfig(githubConnectorDTO, TasArtifactRegistryType.GCR),
                decryptionHelper))
        .hasMessage("Please select the API Access auth type to Token");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetContainerSettingsForGithubPackageRegistryNoApiAccess() {
    GithubConnectorDTO githubConnectorDTO =
        GithubConnectorDTO.builder().authentication(getGithubUserNameAuth()).build();
    assertThatThrownBy(
        ()
            -> tasGithubPackageRegistrySettingsProvider.getContainerSettings(
                TasTestUtils.createTestContainerArtifactConfig(githubConnectorDTO, TasArtifactRegistryType.GCR),
                decryptionHelper))
        .hasMessage("Please enable the API Access for the Github Connector");

    GithubConnectorDTO githubConnectorDTOSSh =
        GithubConnectorDTO.builder()
            .authentication(GithubAuthenticationDTO.builder().authType(GitAuthType.SSH).build())
            .build();
    assertThatThrownBy(
        ()
            -> tasGithubPackageRegistrySettingsProvider.getContainerSettings(
                TasTestUtils.createTestContainerArtifactConfig(githubConnectorDTOSSh, TasArtifactRegistryType.GCR),
                decryptionHelper))
        .hasMessage("Invalid credentials type, Ssh are not supported");
  }

  private GithubAuthenticationDTO getGithubUserNameAuth() {
    return GithubAuthenticationDTO.builder()
        .authType(GitAuthType.HTTP)
        .credentials(GithubHttpCredentialsDTO.builder()
                         .type(GithubHttpAuthenticationType.USERNAME_AND_PASSWORD)
                         .httpCredentialsSpec(
                             GithubUsernamePasswordDTO.builder()
                                 .username(username)
                                 .passwordRef(SecretRefData.builder().decryptedValue(password.toCharArray()).build())
                                 .build())
                         .build())
        .build();
  }

  private GithubAuthenticationDTO getGithubUserNameToken() {
    return GithubAuthenticationDTO.builder()
        .authType(GitAuthType.HTTP)
        .credentials(GithubHttpCredentialsDTO.builder()
                         .type(GithubHttpAuthenticationType.USERNAME_AND_TOKEN)
                         .httpCredentialsSpec(
                             GithubUsernameTokenDTO.builder()
                                 .username("token-username")
                                 .tokenRef(SecretRefData.builder().decryptedValue("token".toCharArray()).build())
                                 .build())
                         .build())
        .build();
  }
}
