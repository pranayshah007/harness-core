/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.git.checks;

import static io.harness.rule.OwnerRule.SHUBHAM;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cistatus.service.GithubService;
import io.harness.cistatus.service.azurerepo.AzureRepoService;
import io.harness.cistatus.service.bitbucket.BitbucketService;
import io.harness.cistatus.service.gitlab.GitlabService;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoApiAccessDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoApiAccessType;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoAuthenticationDTO;
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
import io.harness.delegate.beans.connector.scm.github.GithubTokenSpecDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabApiAccessDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabApiAccessType;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabConnectorDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabTokenSpecDTO;
import io.harness.delegate.task.ci.GitSCMType;
import io.harness.encryption.SecretRefData;
import io.harness.rule.Owner;
import io.harness.secrets.SecretDecryptor;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class GitStatusCheckHelperTest extends CategoryTest {
  @Mock private GithubService githubService;
  @Mock private BitbucketService bitbucketService;
  @Mock private GitlabService gitlabService;
  @Mock private AzureRepoService azureRepoService;
  @Mock private SecretDecryptor secretDecryptor;
  @InjectMocks private GitStatusCheckHelper gitStatusCheckHelper;

  private final String APP_ID = "APP_ID";
  private final String INSTALL_ID = "123";
  private final String PRIVATE_KEY = "123";

  private final String DESC = "desc";
  private final String STATE = "success";
  private final String BUILD_NUMBER = "buildNumber";
  private final String TITLE = "title";
  private final String REPO = "repo";
  private final String OWNER = "owner";
  private final String USERNAME = "user";
  private final String TOKEN = "token";
  private final String SHA = "e9a0d31c5ac677ec1e06fb3ab69cd1d2cc62a74a";
  private final String IDENTIFIER = "stageIdentifier";
  private final String TARGET_URL = "https://app.harness.io";
  private final String ACCOUNT_ID = "test";
  private final String KEY = "dummyKey";
  private final String AZURE_REPO = "project/_git/repo";

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void testPushStatusForGithubApp() {
    GithubConnectorDTO githubConnectorDTO = GithubConnectorDTO.builder()
                                                .url("https://github.com")
                                                .apiAccess(GithubApiAccessDTO.builder()
                                                               .type(GithubApiAccessType.GITHUB_APP)
                                                               .spec(GithubAppSpecDTO.builder().build())
                                                               .build())
                                                .build();
    GithubAppSpecDTO decryptedAppSpec =
        GithubAppSpecDTO.builder()
            .applicationId(APP_ID)
            .installationId(INSTALL_ID)
            .privateKeyRef(SecretRefData.builder().decryptedValue(PRIVATE_KEY.toCharArray()).build())
            .build();

    when(secretDecryptor.decrypt(any(), any(), any())).thenReturn(decryptedAppSpec);
    when(githubService.getToken(any())).thenReturn(TOKEN);
    when(githubService.sendStatus(any(), any(), any(), any(), any(), any())).thenReturn(true);

    boolean actual = gitStatusCheckHelper.sendStatus(
        GitStatusCheckParams.builder()
            .sha(SHA)
            .identifier(IDENTIFIER)
            .buildNumber(BUILD_NUMBER)
            .gitSCMType(GitSCMType.GITHUB)
            .owner(OWNER)
            .repo(REPO)
            .state(STATE)
            .title(TITLE)
            .target_url(TARGET_URL)
            .desc(DESC)
            .connectorDetails(ConnectorDetails.builder().connectorConfig(githubConnectorDTO).build())
            .build(),
        ACCOUNT_ID);

    assertThat(actual).isEqualTo(true);
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void testPushStatusForGithubToken() {
    GithubConnectorDTO githubConnectorDTO = GithubConnectorDTO.builder()
                                                .url("https://github.com")
                                                .apiAccess(GithubApiAccessDTO.builder()
                                                               .type(GithubApiAccessType.TOKEN)
                                                               .spec(GithubTokenSpecDTO.builder().build())
                                                               .build())
                                                .build();
    GithubTokenSpecDTO decryptedTokenSpec =
        GithubTokenSpecDTO.builder()
            .tokenRef(SecretRefData.builder().decryptedValue(TOKEN.toCharArray()).build())
            .build();

    when(secretDecryptor.decrypt(any(), any(), any())).thenReturn(decryptedTokenSpec);
    when(githubService.sendStatus(any(), any(), any(), any(), any(), any())).thenReturn(true);

    boolean actual = gitStatusCheckHelper.sendStatus(
        GitStatusCheckParams.builder()
            .sha(SHA)
            .identifier(IDENTIFIER)
            .buildNumber(BUILD_NUMBER)
            .gitSCMType(GitSCMType.GITHUB)
            .owner(OWNER)
            .repo(REPO)
            .state(STATE)
            .title(TITLE)
            .target_url(TARGET_URL)
            .desc(DESC)
            .connectorDetails(ConnectorDetails.builder().connectorConfig(githubConnectorDTO).build())
            .build(),
        ACCOUNT_ID);

    assertThat(actual).isEqualTo(true);
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void testPushStatusForBitbucketToken() {
    BitbucketConnectorDTO gitConnectorDTO =
        BitbucketConnectorDTO.builder()
            .url("https://bitbucket.org")
            .apiAccess(BitbucketApiAccessDTO.builder()
                           .type(BitbucketApiAccessType.USERNAME_AND_TOKEN)
                           .spec(BitbucketUsernameTokenApiAccessDTO.builder().build())
                           .build())
            .build();
    BitbucketUsernameTokenApiAccessDTO decryptedTokenSpec =
        BitbucketUsernameTokenApiAccessDTO.builder()
            .username(USERNAME)
            .tokenRef(SecretRefData.builder().decryptedValue(TOKEN.toCharArray()).build())
            .build();

    when(secretDecryptor.decrypt(any(), any(), any())).thenReturn(decryptedTokenSpec);
    when(bitbucketService.sendStatus(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(true);

    boolean actual = gitStatusCheckHelper.sendStatus(
        GitStatusCheckParams.builder()
            .sha(SHA)
            .identifier(IDENTIFIER)
            .buildNumber(BUILD_NUMBER)
            .gitSCMType(GitSCMType.BITBUCKET)
            .owner(OWNER)
            .repo(REPO)
            .state(STATE)
            .title(TITLE)
            .target_url(TARGET_URL)
            .desc(DESC)
            .connectorDetails(ConnectorDetails.builder().connectorConfig(gitConnectorDTO).build())
            .build(),
        ACCOUNT_ID);

    assertThat(actual).isEqualTo(true);
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void testPushStatusForGitlabToken() {
    GitlabConnectorDTO gitConnectorDTO = GitlabConnectorDTO.builder()
                                             .url("https://gitlab.com")
                                             .apiAccess(GitlabApiAccessDTO.builder()
                                                            .type(GitlabApiAccessType.TOKEN)
                                                            .spec(GitlabTokenSpecDTO.builder().build())
                                                            .build())
                                             .build();
    GitlabTokenSpecDTO decryptedTokenSpec =
        GitlabTokenSpecDTO.builder()
            .tokenRef(SecretRefData.builder().decryptedValue(TOKEN.toCharArray()).build())
            .build();

    when(secretDecryptor.decrypt(any(), any(), any())).thenReturn(decryptedTokenSpec);
    when(gitlabService.sendStatus(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(true);

    boolean actual = gitStatusCheckHelper.sendStatus(
        GitStatusCheckParams.builder()
            .sha(SHA)
            .identifier(IDENTIFIER)
            .buildNumber(BUILD_NUMBER)
            .gitSCMType(GitSCMType.GITLAB)
            .owner(OWNER)
            .repo(REPO)
            .state(STATE)
            .title(TITLE)
            .target_url(TARGET_URL)
            .desc(DESC)
            .connectorDetails(ConnectorDetails.builder().connectorConfig(gitConnectorDTO).build())
            .build(),
        ACCOUNT_ID);

    assertThat(actual).isEqualTo(true);
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void testPushStatusForAzure() {
    AzureRepoConnectorDTO gitConnectorDTO =
        AzureRepoConnectorDTO.builder()
            .url("https://dev.azure.com/")
            .apiAccess(AzureRepoApiAccessDTO.builder()
                           .type(AzureRepoApiAccessType.TOKEN)
                           .spec(AzureRepoTokenSpecDTO.builder().build())
                           .build())
            .connectionType(GitConnectionType.REPO)
            .authentication(AzureRepoAuthenticationDTO.builder().authType(GitAuthType.HTTP).build())
            .build();
    AzureRepoTokenSpecDTO decryptedTokenSpec =
        AzureRepoTokenSpecDTO.builder()
            .tokenRef(SecretRefData.builder().decryptedValue(TOKEN.toCharArray()).build())
            .build();

    when(secretDecryptor.decrypt(any(), any(), any())).thenReturn(decryptedTokenSpec);
    when(azureRepoService.sendStatus(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(true);

    boolean actual = gitStatusCheckHelper.sendStatus(
        GitStatusCheckParams.builder()
            .sha(SHA)
            .identifier(IDENTIFIER)
            .buildNumber(BUILD_NUMBER)
            .gitSCMType(GitSCMType.AZURE_REPO)
            .owner(OWNER)
            .repo(REPO)
            .state(STATE)
            .title(TITLE)
            .target_url(TARGET_URL)
            .desc(DESC)
            .connectorDetails(ConnectorDetails.builder().connectorConfig(gitConnectorDTO).build())
            .build(),
        ACCOUNT_ID);

    assertThat(actual).isEqualTo(true);
  }
}
