/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.helper;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.BHAVYA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectorDTO;
import io.harness.delegate.beans.connector.scm.bitbucket.BitbucketConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.gitsync.beans.GitRepositoryDTO;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PL)
public class GitFilePathHelperTest extends CategoryTest {
  @Mock GitSyncConnectorHelper gitSyncConnectorHelper;
  @InjectMocks GitFilePathHelper gitFilePathHelper;

  String accountIdentifier = "accountIdentifier";
  String projectIdentifier = "projectIdentifier";
  String orgIdentifier = "orgIdentifier";
  String filePath = "filePath";
  String branch = "branch";
  String connectorRef = "connectorRef";
  String repoName = "repoName";
  Scope scope = Scope.of(accountIdentifier, orgIdentifier, projectIdentifier);
  GitRepositoryDTO gitRepositoryDTO = GitRepositoryDTO.builder().name(repoName).build();
  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testNullFilePath() {
    try {
      gitFilePathHelper.validateFilePath(null);
    } catch (Exception ex) {
      WingsException exception = ExceptionUtils.cause(InvalidRequestException.class, ex);
      assertThat(exception).isNotNull();
      assertThat(exception.getMessage()).isEqualTo(GitFilePathHelper.NULL_FILE_PATH_ERROR_MESSAGE);
    }
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testValidateFilePath_whenFilePathDoesNotHaveCorrectFormat() {
    String filePath = "//.harness/abc.yaml////";
    try {
      gitFilePathHelper.validateFilePath(filePath);
    } catch (Exception ex) {
      WingsException exception = ExceptionUtils.cause(InvalidRequestException.class, ex);
      assertThat(exception).isNotNull();
      assertThat(exception.getMessage())
          .isEqualTo(String.format(GitFilePathHelper.INVALID_FILE_PATH_FORMAT_ERROR_MESSAGE, filePath));
    }
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testValidateFilePath_whenFilePathIsNotInHarnessDirectory() {
    String filePath = "abc.yaml";
    try {
      gitFilePathHelper.validateFilePath(filePath);
    } catch (Exception ex) {
      WingsException exception = ExceptionUtils.cause(InvalidRequestException.class, ex);
      assertThat(exception).isNotNull();
      assertThat(exception.getMessage())
          .isEqualTo(String.format(GitFilePathHelper.FILE_PATH_INVALID_DIRECTORY_ERROR_FORMAT, filePath));
    }
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testValidateFilePath_whenFilePathHasInvalidExtension() {
    String filePath = ".harness/abc.py";
    try {
      gitFilePathHelper.validateFilePath(filePath);
    } catch (Exception ex) {
      WingsException exception = ExceptionUtils.cause(InvalidRequestException.class, ex);
      assertThat(exception).isNotNull();
      assertThat(exception.getMessage())
          .isEqualTo(String.format(GitFilePathHelper.FILE_PATH_INVALID_EXTENSION_ERROR_FORMAT, filePath));
    }
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testGetFileUrlForGithub_ifBranchNameIsNull() {
    GithubConnectorDTO githubConnector = GithubConnectorDTO.builder().connectionType(GitConnectionType.REPO).build();
    doReturn(githubConnector)
        .when(gitSyncConnectorHelper)
        .getScmConnectorForGivenRepo(anyString(), anyString(), anyString(), anyString(), anyString());
    try {
      gitFilePathHelper.getFileUrl(scope, connectorRef, null, filePath, gitRepositoryDTO);
    } catch (WingsException ex) {
      assertThat(ex).isInstanceOf(InvalidRequestException.class);
    }
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testGetFileUrlForGithub_ifFilePathIsEmpty() {
    GithubConnectorDTO githubConnector = GithubConnectorDTO.builder().connectionType(GitConnectionType.REPO).build();
    doReturn(githubConnector)
        .when(gitSyncConnectorHelper)
        .getScmConnectorForGivenRepo(anyString(), anyString(), anyString(), anyString(), anyString());
    try {
      gitFilePathHelper.getFileUrl(scope, connectorRef, branch, "", gitRepositoryDTO);
    } catch (WingsException ex) {
      assertThat(ex).isInstanceOf(InvalidRequestException.class);
    }
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testGetFileUrlForGithub_ifConnectorIsRepoType() {
    String repoUrl = "https://github.com/harness/repoName/";
    GithubConnectorDTO githubConnector =
        GithubConnectorDTO.builder().connectionType(GitConnectionType.REPO).url(repoUrl).build();
    doReturn(githubConnector)
        .when(gitSyncConnectorHelper)
        .getScmConnectorForGivenRepo(anyString(), anyString(), anyString(), anyString(), anyString());
    String fileUrl = gitFilePathHelper.getFileUrl(scope, connectorRef, branch, filePath, gitRepositoryDTO);
    assertThat(fileUrl).isEqualTo(repoUrl + "blob/" + branch + "/" + filePath);
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testGetFileUrlForGithub_ifConnectorIsAccountType() {
    String repoUrl = "https://github.com/harness/";
    GithubConnectorDTO githubConnector =
        GithubConnectorDTO.builder().connectionType(GitConnectionType.ACCOUNT).url(repoUrl).build();
    doReturn(githubConnector)
        .when(gitSyncConnectorHelper)
        .getScmConnectorForGivenRepo(anyString(), anyString(), anyString(), anyString(), anyString());
    String fileUrl = gitFilePathHelper.getFileUrl(scope, connectorRef, branch, filePath, gitRepositoryDTO);
    assertThat(fileUrl).isEqualTo(repoUrl + "repoName/blob/" + branch + "/" + filePath);
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testGetFileUrlForBitbucketCloud() {
    String repoUrl = "https://bitbucket.org/harness/repoName/";
    BitbucketConnectorDTO bitbucketConnectorDTO =
        BitbucketConnectorDTO.builder().connectionType(GitConnectionType.REPO).url(repoUrl).build();
    doReturn(bitbucketConnectorDTO)
        .when(gitSyncConnectorHelper)
        .getScmConnectorForGivenRepo(anyString(), anyString(), anyString(), anyString(), anyString());
    String fileUrl = gitFilePathHelper.getFileUrl(scope, connectorRef, branch, filePath, gitRepositoryDTO);
    assertThat(fileUrl).isEqualTo(repoUrl + "src/" + branch + "/" + filePath);
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testGetFileUrlForBitbucketServer() {
    String repoUrl = "https://bitbucket.dev.harness.io/scm/harness/repoName";
    BitbucketConnectorDTO bitbucketConnectorDTO =
        BitbucketConnectorDTO.builder().connectionType(GitConnectionType.REPO).url(repoUrl).build();
    doReturn(bitbucketConnectorDTO)
        .when(gitSyncConnectorHelper)
        .getScmConnectorForGivenRepo(anyString(), anyString(), anyString(), anyString(), anyString());
    String fileUrl = gitFilePathHelper.getFileUrl(scope, connectorRef, branch, filePath, gitRepositoryDTO);
    assertThat(fileUrl).isEqualTo(
        "https://bitbucket.dev.harness.io/projects/harness/repos/repoName/browse/filePath?at=refs/heads/branch");
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testGetFileUrlForAzureRepo_ForRepoTypeConnector() {
    String repoUrl = "https://dev.azure.com/org/gitSync/_git/repoName";
    AzureRepoConnectorDTO azureRepoConnectorDTO =
            AzureRepoConnectorDTO.builder().connectionType(GitConnectionType.REPO).url(repoUrl)
                    .authentication(AzureRepoAuthenticationDTO.builder().authType(GitAuthType.HTTP).build()).build();
    GitRepositoryDTO gitRepository = GitRepositoryDTO.builder().name(repoName).projectName("gitSync").build();
    doReturn(azureRepoConnectorDTO)
            .when(gitSyncConnectorHelper)
            .getScmConnectorForGivenRepo(anyString(), anyString(), anyString(), anyString(), anyString());
    String fileUrl = gitFilePathHelper.getFileUrl(scope, connectorRef, branch, filePath, gitRepository);
    assertThat(fileUrl).isEqualTo(
            "https://dev.azure.com/org/gitSync/_git/repoName?path=filePath&version=GBbranch");
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testGetFileUrlForAzureRepo_ForAccountTypeConnector() {
    String repoUrl = "https://dev.azure.com/org";
    String repoProjectName = "repoProjectName";
    AzureRepoConnectorDTO azureRepoConnectorDTO =
            AzureRepoConnectorDTO.builder().connectionType(GitConnectionType.ACCOUNT).url(repoUrl).build();
    GitRepositoryDTO gitRepository = GitRepositoryDTO.builder().name(repoName).projectName(repoProjectName).build();
    doReturn(azureRepoConnectorDTO)
            .when(gitSyncConnectorHelper)
            .getScmConnectorForGivenRepo(anyString(), anyString(), anyString(), anyString(), anyString());
    String fileUrl = gitFilePathHelper.getFileUrl(scope, connectorRef, branch, filePath, gitRepository);
    assertThat(fileUrl).isEqualTo(
            "https://dev.azure.com/org/repoProjectName/_git/repoName?path=filePath&version=GBbranch");
  }
}
