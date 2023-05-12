/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.git;

import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;
import static io.harness.rule.OwnerRule.SHUBHAM;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cistatus.service.GithubService;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoApiAccessDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoApiAccessType;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectionTypeDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectorDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoTokenSpecDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketApiAccessDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketApiAccessType;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketConnectorDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketUsernameTokenApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessType;
import io.harness.delegate.beans.connector.scm.github.GithubAppSpecDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubOauthDTO;
import io.harness.delegate.beans.connector.scm.github.GithubTokenSpecDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabApiAccessDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabApiAccessType;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabConnectorDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabTokenSpecDTO;
import io.harness.delegate.task.ci.GitSCMType;
import io.harness.encryption.SecretRefData;
import io.harness.git.GitTokenRetriever;
import io.harness.rule.Owner;
import io.harness.secrets.SecretDecryptor;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class GitTokenRetrieverTest extends CategoryTest {
  @Mock private SecretDecryptor secretDecryptor;
  @Mock private GithubService githubService;
  @InjectMocks private GitTokenRetriever gitTokenRetriever;

  private final String APP_ID = "APP_ID";
  private final String INSTALL_ID = "123";
  private final String PRIVATE_KEY = "123";
  private final String TOKEN = "token";
  private final String OAuth = "oauth";
  private final String USERNAME = "user";

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void testRetrieveGithubAppAuthToken() {
    GithubConnectorDTO githubConnectorDTO = GithubConnectorDTO.builder()
                                                .url("https://github.com")
                                                .apiAccess(GithubApiAccessDTO.builder()
                                                               .type(GithubApiAccessType.GITHUB_APP)
                                                               .spec(GithubAppSpecDTO.builder().build())
                                                               .build())
                                                .build();
    ConnectorDetails connectorDetails = ConnectorDetails.builder().connectorConfig(githubConnectorDTO).build();

    GithubAppSpecDTO decryptedAppSpec =
        GithubAppSpecDTO.builder()
            .applicationId(APP_ID)
            .installationId(INSTALL_ID)
            .privateKeyRef(SecretRefData.builder().decryptedValue(PRIVATE_KEY.toCharArray()).build())
            .build();
    when(secretDecryptor.decrypt(any(), any())).thenReturn(decryptedAppSpec);
    when(githubService.getToken(any())).thenReturn(TOKEN);
    String actual = gitTokenRetriever.retrieveAuthToken(GitSCMType.GITHUB, connectorDetails);
    assertThat(actual).isEqualTo(TOKEN);
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void testRetrieveGithubTokenAuthToken() {
    GithubConnectorDTO githubConnectorDTO = GithubConnectorDTO.builder()
                                                .url("https://github.com")
                                                .apiAccess(GithubApiAccessDTO.builder()
                                                               .type(GithubApiAccessType.TOKEN)
                                                               .spec(GithubTokenSpecDTO.builder().build())
                                                               .build())
                                                .build();
    ConnectorDetails connectorDetails = ConnectorDetails.builder().connectorConfig(githubConnectorDTO).build();
    GithubTokenSpecDTO decryptedTokenSpec =
        GithubTokenSpecDTO.builder()
            .tokenRef(SecretRefData.builder().decryptedValue(TOKEN.toCharArray()).build())
            .build();

    when(secretDecryptor.decrypt(any(), any())).thenReturn(decryptedTokenSpec);
    String actual = gitTokenRetriever.retrieveAuthToken(GitSCMType.GITHUB, connectorDetails);
    assertThat(actual).isEqualTo(TOKEN);
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testRetrieveGithubOAuthToken() {
    GithubConnectorDTO githubConnectorDTO = GithubConnectorDTO.builder()
                                                .url("https://github.com")
                                                .apiAccess(GithubApiAccessDTO.builder()
                                                               .type(GithubApiAccessType.OAUTH)
                                                               .spec(GithubOauthDTO.builder().build())
                                                               .build())
                                                .build();
    ConnectorDetails connectorDetails = ConnectorDetails.builder().connectorConfig(githubConnectorDTO).build();
    GithubOauthDTO decryptedOAuthSpec =
        GithubOauthDTO.builder().tokenRef(SecretRefData.builder().decryptedValue(OAuth.toCharArray()).build()).build();

    when(secretDecryptor.decrypt(any(), any())).thenReturn(decryptedOAuthSpec);
    String actual = gitTokenRetriever.retrieveAuthToken(GitSCMType.GITHUB, connectorDetails);
    assertThat(actual).isEqualTo(OAuth);
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void testRetrieveBitbucketTokenAuthToken() {
    BitbucketConnectorDTO gitConnectorDTO =
        BitbucketConnectorDTO.builder()
            .url("https://bitbucket.org")
            .apiAccess(BitbucketApiAccessDTO.builder()
                           .type(BitbucketApiAccessType.USERNAME_AND_TOKEN)
                           .spec(BitbucketUsernameTokenApiAccessDTO.builder().build())
                           .build())
            .build();
    ConnectorDetails connectorDetails = ConnectorDetails.builder().connectorConfig(gitConnectorDTO).build();
    BitbucketUsernameTokenApiAccessDTO decryptedTokenSpec =
        BitbucketUsernameTokenApiAccessDTO.builder()
            .username(USERNAME)
            .tokenRef(SecretRefData.builder().decryptedValue(TOKEN.toCharArray()).build())
            .build();

    when(secretDecryptor.decrypt(any(), any())).thenReturn(decryptedTokenSpec);
    String actual = gitTokenRetriever.retrieveAuthToken(GitSCMType.BITBUCKET, connectorDetails);
    assertThat(actual).isEqualTo(TOKEN);
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testRetrieveBitbucketUsernameRef() {
    BitbucketUsernameTokenApiAccessDTO decryptedTokenSpec =
        BitbucketUsernameTokenApiAccessDTO.builder()
            .usernameRef(SecretRefData.builder().decryptedValue(USERNAME.toCharArray()).build())
            .tokenRef(SecretRefData.builder().decryptedValue(TOKEN.toCharArray()).build())
            .build();

    when(secretDecryptor.decrypt(any(), any())).thenReturn(decryptedTokenSpec);
    String actual = gitTokenRetriever.retrieveBitbucketUsernameFromAPIAccess(any(), any());
    assertThat(actual).isEqualTo(USERNAME);
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void testRetrieveGitlabTokenAuthToken() {
    GitlabConnectorDTO gitConnectorDTO = GitlabConnectorDTO.builder()
                                             .url("https://gitlab.com")
                                             .apiAccess(GitlabApiAccessDTO.builder()
                                                            .type(GitlabApiAccessType.TOKEN)
                                                            .spec(GitlabTokenSpecDTO.builder().build())
                                                            .build())
                                             .build();
    ConnectorDetails connectorDetails = ConnectorDetails.builder().connectorConfig(gitConnectorDTO).build();
    GitlabTokenSpecDTO decryptedTokenSpec =
        GitlabTokenSpecDTO.builder()
            .tokenRef(SecretRefData.builder().decryptedValue(TOKEN.toCharArray()).build())
            .build();

    when(secretDecryptor.decrypt(any(), any())).thenReturn(decryptedTokenSpec);
    String actual = gitTokenRetriever.retrieveAuthToken(GitSCMType.GITLAB, connectorDetails);
    assertThat(actual).isEqualTo(TOKEN);
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void testRetrieveAzureTokenAuthToken() {
    AzureRepoConnectorDTO gitConnectorDTO =
        AzureRepoConnectorDTO.builder()
            .url("https://dev.azure.com/")
            .apiAccess(AzureRepoApiAccessDTO.builder()
                           .type(AzureRepoApiAccessType.TOKEN)
                           .spec(AzureRepoTokenSpecDTO.builder().build())
                           .build())
            .connectionType(AzureRepoConnectionTypeDTO.REPO)
            .authentication(AzureRepoAuthenticationDTO.builder().authType(GitAuthType.HTTP).build())
            .build();
    ConnectorDetails connectorDetails = ConnectorDetails.builder().connectorConfig(gitConnectorDTO).build();

    AzureRepoTokenSpecDTO decryptedTokenSpec =
        AzureRepoTokenSpecDTO.builder()
            .tokenRef(SecretRefData.builder().decryptedValue(TOKEN.toCharArray()).build())
            .build();

    when(secretDecryptor.decrypt(any(), any())).thenReturn(decryptedTokenSpec);
    String actual = gitTokenRetriever.retrieveAuthToken(GitSCMType.AZURE_REPO, connectorDetails);
    assertThat(actual).isEqualTo(TOKEN);
  }
}
