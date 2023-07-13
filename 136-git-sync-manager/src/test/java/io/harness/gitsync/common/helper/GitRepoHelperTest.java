/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.helper;

import static io.harness.rule.OwnerRule.ADITHYA;
import static io.harness.rule.OwnerRule.MOHIT_GARG;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitConnectorDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoApiAccessDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectionTypeDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectorDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketApiAccessDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubApiAccessDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabApiAccessDTO;
import io.harness.delegate.beans.connector.scm.gitlab.GitlabConnectorDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.git.GitClientHelper;
import io.harness.gitsync.GitSyncTestBase;
import io.harness.gitsync.beans.GitRepositoryDTO;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class GitRepoHelperTest extends GitSyncTestBase {
  @InjectMocks GitRepoHelper gitRepoHelper;
  ConnectorInfoDTO connectorInfo;
  String accountIdentifier = "accountIdentifier";
  String projectIdentifier = "projectIdentifier";
  String orgIdentifier = "orgIdentifier";
  String repoURL = "https://github.com/harness";
  String completeRepoUrl = "https://github.com/harness/repoName";
  String repoName = "repoName";
  Scope scope;
  @Before
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);
    scope = Scope.builder()
                .accountIdentifier(accountIdentifier)
                .orgIdentifier(orgIdentifier)
                .projectIdentifier(projectIdentifier)
                .build();
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testGetRepoUrlForBitbucketOnPremHTTP() {
    mockStatic(GitClientHelper.class);
    BitbucketConnectorDTO connectorDTO =
        BitbucketConnectorDTO.builder()
            .connectionType(GitConnectionType.ACCOUNT)
            .apiAccess(BitbucketApiAccessDTO.builder().build())
            .authentication(BitbucketAuthenticationDTO.builder().authType(GitAuthType.HTTP).build())
            .url(repoURL)
            .build();
    ConnectorInfoDTO connectorInfo = ConnectorInfoDTO.builder().connectorConfig(connectorDTO).build();
    ScmConnector scmConnector = (ScmConnector) connectorInfo.getConnectorConfig();
    when(GitClientHelper.isBitBucketSAAS(any())).thenReturn(false);
    String responseUrl = gitRepoHelper.getRepoUrl(scmConnector, repoName);

    assertThat(responseUrl.equals(scmConnector.getGitConnectionUrl(GitRepositoryDTO.builder().name(repoName).build())))
        .isTrue();
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testGetRepoUrlForGithub() {
    GithubConnectorDTO githubConnector = GithubConnectorDTO.builder()
                                             .connectionType(GitConnectionType.ACCOUNT)
                                             .apiAccess(GithubApiAccessDTO.builder().build())
                                             .url(repoURL)
                                             .build();
    connectorInfo = ConnectorInfoDTO.builder().connectorConfig(githubConnector).build();
    ScmConnector scmConnector = (ScmConnector) connectorInfo.getConnectorConfig();

    String responseUrl = gitRepoHelper.getRepoUrl(scmConnector, repoName);

    assertThat(responseUrl.equals(completeRepoUrl)).isTrue();
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testGetRepoUrlForBitbucketSAAS() {
    BitbucketConnectorDTO connectorDTO = BitbucketConnectorDTO.builder()
                                             .connectionType(GitConnectionType.ACCOUNT)
                                             .apiAccess(BitbucketApiAccessDTO.builder().build())
                                             .url("https://bitbucket.org/harness")
                                             .build();
    connectorInfo = ConnectorInfoDTO.builder().connectorConfig(connectorDTO).build();
    ScmConnector scmConnector = (ScmConnector) connectorInfo.getConnectorConfig();

    String responseUrl = gitRepoHelper.getRepoUrl(scmConnector, repoName);

    assertThat(responseUrl.equals("https://bitbucket.org/harness/repoName")).isTrue();
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testGetRepoUrlForBitbucketOnPremSSH() {
    BitbucketConnectorDTO connectorDTO =
        BitbucketConnectorDTO.builder()
            .connectionType(GitConnectionType.ACCOUNT)
            .apiAccess(BitbucketApiAccessDTO.builder().build())
            .authentication(BitbucketAuthenticationDTO.builder().authType(GitAuthType.SSH).build())
            .url(repoURL)
            .build();
    connectorInfo = ConnectorInfoDTO.builder().connectorConfig(connectorDTO).build();
    ScmConnector scmConnector = (ScmConnector) connectorInfo.getConnectorConfig();

    String responseUrl = gitRepoHelper.getRepoUrl(scmConnector, repoName);

    assertThat(responseUrl.equals("https://github.com/scm/harness/repoName")).isTrue();
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testGetRepoUrlForAzure() {
    repoURL = "git@ssh.dev.azure.com:v3/repoOrg/repoProject";
    AzureRepoConnectorDTO connectorDTO =
        AzureRepoConnectorDTO.builder()
            .connectionType(AzureRepoConnectionTypeDTO.PROJECT)
            .apiAccess(AzureRepoApiAccessDTO.builder().build())
            .authentication(AzureRepoAuthenticationDTO.builder().authType(GitAuthType.SSH).build())
            .url(repoURL)
            .build();
    connectorInfo = ConnectorInfoDTO.builder().connectorConfig(connectorDTO).build();
    ScmConnector scmConnector = (ScmConnector) connectorInfo.getConnectorConfig();

    String responseUrl = gitRepoHelper.getRepoUrl(scmConnector, repoName);

    assertThat(responseUrl.equals("https://dev.azure.com/repoOrg/repoProject/_git/repoName")).isTrue();
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testGetRepoUrlForGitlab() {
    GitlabConnectorDTO gitlabConnectorDTO = GitlabConnectorDTO.builder()
                                                .connectionType(GitConnectionType.ACCOUNT)
                                                .apiAccess(GitlabApiAccessDTO.builder().build())
                                                .url(repoURL)
                                                .build();
    connectorInfo = ConnectorInfoDTO.builder().connectorConfig(gitlabConnectorDTO).build();
    ScmConnector scmConnector = (ScmConnector) connectorInfo.getConnectorConfig();

    String responseUrl = gitRepoHelper.getRepoUrl(scmConnector, repoName);

    assertThat(responseUrl.equals(completeRepoUrl)).isTrue();
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testGetRepoUrlWhenInvalidConnectorType() {
    AwsCodeCommitConnectorDTO connectorDTO = AwsCodeCommitConnectorDTO.builder().build();
    ConnectorInfoDTO connectorInfo = ConnectorInfoDTO.builder().connectorConfig(connectorDTO).build();
    ScmConnector scmConnector = (ScmConnector) connectorInfo.getConnectorConfig();

    assertThatThrownBy(() -> gitRepoHelper.getRepoUrl(scmConnector, repoName))
        .isInstanceOf(InvalidRequestException.class);
  }
}
