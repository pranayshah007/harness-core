/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.rule.OwnerRule.ARVIND;
import static io.harness.rule.OwnerRule.YOGESH;

import static software.wings.beans.GitRepositoryInfo.GitProvider.BITBUCKET;
import static software.wings.beans.GitRepositoryInfo.GitProvider.GITHUB;
import static software.wings.beans.GitRepositoryInfo.GitProvider.GITLAB;
import static software.wings.beans.GitRepositoryInfo.GitProvider.UNKNOWN;
import static software.wings.beans.yaml.GitCommand.GitCommandType.VALIDATE;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.beans.DelegateTask;
import io.harness.category.element.UnitTests;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.WingsBaseTest;
import software.wings.beans.GitConfig;
import software.wings.beans.GitRepositoryInfo;
import software.wings.beans.yaml.GitCommandExecutionResponse;
import software.wings.service.intfc.DelegateService;
import software.wings.sm.ExecutionContext;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class GitConfigHelperServiceTest extends WingsBaseTest {
  @Mock ExecutionContext context;
  @Mock DelegateService delegateService;

  @Inject @InjectMocks private GitConfigHelperService gitConfigHelperService;

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testRenderGitConfig() {
    String branchExpression = "${branch}";
    String urlExpression = "${url}";
    String refExpression = "${ref}";
    GitConfig gitConfig = GitConfig.builder().branch(branchExpression).repoUrl(urlExpression).build();
    gitConfig.setReference(refExpression);

    when(context.renderExpression(branchExpression)).thenReturn("master");
    when(context.renderExpression(urlExpression)).thenReturn("github.com");
    when(context.renderExpression(refExpression)).thenReturn("tag-1");

    gitConfigHelperService.renderGitConfig(context, gitConfig);

    assertThat(gitConfig.getBranch()).isEqualTo("master");
    assertThat(gitConfig.getRepoUrl()).isEqualTo("github.com");
    assertThat(gitConfig.getReference()).isEqualTo("tag-1");
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void validateGitConfigTestUrlTypeAccount() throws Exception {
    validateGitConfigUrlTypeAccount();
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void validateGitConfigTestUrlTypeRepo() throws Exception {
    validateGitConfigUrlTypeRepo();
  }

  private void validateGitConfigConvert(String expectedRepoUrl, String repoName, GitConfig config, String resetRepoUrl,
      GitConfig.UrlType resetUrlType, String resetRepoName) {
    gitConfigHelperService.convertToRepoGitConfig(config, repoName);

    assertThat(config.getRepoUrl()).isEqualTo(expectedRepoUrl);
    assertThat(config.getUrlType()).isEqualTo(GitConfig.UrlType.REPO);
    assertThat(config.getRepoName()).isEqualTo(repoName);

    config.setRepoUrl(resetRepoUrl);
    config.setUrlType(resetUrlType);
    config.setRepoName(resetRepoName);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testConvertToRepoGitConfigRepo() {
    String repoUrl1 = "https://abc@a.org/account/name";
    String repoUrl2 = "https://abc@a.org/account/name.git";
    String random = "random";
    GitConfig config = GitConfig.builder().build();

    // UrlType: Repo
    config.setUrlType(GitConfig.UrlType.REPO);
    config.setRepoUrl(repoUrl1);
    config.setRepoName(random);
    validateGitConfigConvert(repoUrl1, null, config, repoUrl1, GitConfig.UrlType.REPO, random);
    validateGitConfigConvert(repoUrl1, random, config, repoUrl1, GitConfig.UrlType.REPO, random);

    config.setRepoUrl(repoUrl2);
    validateGitConfigConvert(repoUrl2, null, config, repoUrl2, GitConfig.UrlType.REPO, random);
    validateGitConfigConvert(repoUrl2, random, config, repoUrl2, GitConfig.UrlType.REPO, random);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testConvertToRepoGitConfigAccount() {
    List<String> urls =
        Arrays.asList("https://abc@a.org/account/", "https://abc@a.org/account///", "https://abc@a.org/account");
    List<String> repoNames = Arrays.asList("/repo", "///repo", "repo");
    List<String> extendedRepoNames = Arrays.asList("/xyz/repo", "///xyz/repo", "xyz/repo");

    String repoUrl1 = "https://abc@a.org/account/repo";
    String repoUrl2 = "https://abc@a.org/account/xyz/repo";
    GitConfig config = GitConfig.builder().build();

    // UrlType: Account
    config.setUrlType(GitConfig.UrlType.ACCOUNT);

    for (String url : urls) {
      config.setRepoUrl(url);
      for (String repoName : repoNames) {
        validateGitConfigConvert(repoUrl1, repoName, config, url, GitConfig.UrlType.ACCOUNT, null);
      }
      for (String repoName : extendedRepoNames) {
        validateGitConfigConvert(repoUrl2, repoName, config, url, GitConfig.UrlType.ACCOUNT, null);
      }
    }
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testCreateRepositoryInfo() throws Exception {
    Map<String, GitRepositoryInfo.GitProvider> expectedMap = new HashMap<>();
    String displayUrl = "user/repo";
    expectedMap.put("https://github.com/user/repo.git", GITHUB);
    expectedMap.put("git@github.com:user/repo.git", GITHUB);
    expectedMap.put("https://abc@github.com/user/repo", GITHUB);
    expectedMap.put("https://bitbucket.org/user/repo.git", BITBUCKET);
    expectedMap.put("git@bitbucket.org:user/repo.git", BITBUCKET);
    expectedMap.put("https://abc@bitbucket.org/user/repo", BITBUCKET);
    expectedMap.put("https://gitlab.com/user/repo.git", GITLAB);
    expectedMap.put("git@gitlab.com:user/repo.git", GITLAB);
    expectedMap.put("https://abc@gitlab.com/user/repo", GITLAB);
    expectedMap.put("https://xyz.com/user/repo.git", UNKNOWN);
    expectedMap.put("git@xyz.com:user/repo.git", UNKNOWN);
    expectedMap.put("https://abc@xyz.com/user/repo", UNKNOWN);
    expectedMap.forEach((k, v) -> {
      GitRepositoryInfo repositoryInfo =
          gitConfigHelperService.createRepositoryInfo(GitConfig.builder().repoUrl(k).build(), null);
      assertThat(repositoryInfo).isNotNull();
      assertThat(repositoryInfo.getUrl()).isEqualTo(k);
      assertThat(repositoryInfo.getDisplayUrl()).isEqualTo(displayUrl);
      assertThat(repositoryInfo.getProvider()).isEqualTo(v);
    });
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testConvertToRepoGitConfigException() {
    assertThatThrownBy(() -> gitConfigHelperService.convertToRepoGitConfig(null, null))
        .isInstanceOf(GeneralException.class);
    assertThatThrownBy(() -> {
      GitConfig config = GitConfig.builder().urlType(GitConfig.UrlType.ACCOUNT).build();
      gitConfigHelperService.convertToRepoGitConfig(config, null);
    }).isInstanceOf(InvalidRequestException.class);
  }

  private void validateGitConfigUrlTypeRepo() throws InterruptedException {
    GitConfig gitConfig = GitConfig.builder()
                              .accountId(ACCOUNT_ID)
                              .keyAuth(false)
                              .username("")
                              .password("".toCharArray())
                              .urlType(GitConfig.UrlType.REPO)
                              .accountId("id")
                              .build();
    List<EncryptedDataDetail> encryptionDetails = new ArrayList<>();
    doReturn(GitCommandExecutionResponse.builder().build()).when(delegateService).executeTaskV2(any());
    ArgumentCaptor<DelegateTask> captor = ArgumentCaptor.forClass(DelegateTask.class);

    gitConfigHelperService.validateGitConfig(gitConfig, encryptionDetails);
    verify(delegateService, times(1)).executeTaskV2(captor.capture());
    DelegateTask task = captor.getAllValues().get(0);
    Object[] parameters = task.getData().getParameters();
    assertThat(parameters.length).isEqualTo(3);
    assertThat(parameters[0]).isEqualTo(VALIDATE);
    assertThat(parameters[1]).isEqualTo(gitConfig);
    assertThat(parameters[2]).isEqualTo(encryptionDetails);
  }

  private void validateGitConfigUrlTypeAccount() throws InterruptedException {
    GitConfig gitConfig = GitConfig.builder()
                              .accountId(ACCOUNT_ID)
                              .repoUrl("http://x.y/z")
                              .keyAuth(false)
                              .username("")
                              .password("".toCharArray())
                              .urlType(GitConfig.UrlType.ACCOUNT)
                              .build();

    doReturn(GitCommandExecutionResponse.builder().build()).when(delegateService).executeTaskV2(any());

    List<EncryptedDataDetail> encryptionDetails = new ArrayList<>();
    gitConfigHelperService.validateGitConfig(gitConfig, encryptionDetails);
    verify(delegateService, times(0)).executeTaskV2(any());

    gitConfig.setRepoName("");
    gitConfigHelperService.validateGitConfig(gitConfig, encryptionDetails);
    verify(delegateService, times(0)).executeTaskV2(any());

    gitConfig.setRepoName("repo.git");
    ArgumentCaptor<DelegateTask> captor = ArgumentCaptor.forClass(DelegateTask.class);
    gitConfigHelperService.validateGitConfig(gitConfig, encryptionDetails);
    verify(delegateService, times(1)).executeTaskV2(captor.capture());

    DelegateTask task = captor.getAllValues().get(0);
    Object[] parameters = task.getData().getParameters();
    assertThat(parameters.length).isEqualTo(3);
    assertThat(parameters[0]).isEqualTo(VALIDATE);
    assertThat(parameters[1]).isEqualTo(gitConfig);
    assertThat(parameters[2]).isEqualTo(encryptionDetails);
  }
}
