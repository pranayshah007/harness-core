/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml;

import static io.harness.rule.OwnerRule.DEEPAK;
import static io.harness.rule.OwnerRule.JELENA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.eraro.ErrorCode;
import io.harness.exception.GitConnectionDelegateException;
import io.harness.exception.WingsException;
import io.harness.git.model.GitRepositoryType;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import software.wings.beans.GitConfig;
import software.wings.beans.GitOperationContext;

import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import java.io.File;
import java.util.concurrent.ExecutionException;
import org.assertj.core.api.Assertions;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.errors.TransportException;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class GitClientHelperTest {
  private static final String GIT_CONNECTOR_ID = UUIDGenerator.generateUuid();

  @Inject @InjectMocks GitClientHelper gitClientHelper;
  @Mock LoadingCache<String, File> cache;

  @Test(expected = GitConnectionDelegateException.class)
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void test_checkIfGitConnectivityIssueInCaseOfTransportException() {
    gitClientHelper.checkIfGitConnectivityIssue(
        new GitAPIException("Git Exception", new TransportException("Transport Exception")) {});
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void test_checkIfGitConnectivityIssueIsNotTrownInCaseOfOtherExceptions() {
    gitClientHelper.checkIfGitConnectivityIssue(new GitAPIException("newTransportException") {});
  }

  @Test(expected = GitConnectionDelegateException.class)
  @Owner(developers = JELENA)
  @Category(UnitTests.class)
  public void test_checkIfGitConnectivityIssueInCaseOfRefNotFoundException() {
    gitClientHelper.checkIfGitConnectivityIssue(new RefNotFoundException("Invalid commit Id"));
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testGetRepoDirectory() {
    final String repoDirectory =
        gitClientHelper.getRepoDirectory(GitOperationContext.builder()
                                             .gitConnectorId("id")
                                             .gitConfig(GitConfig.builder()
                                                            .accountId("accountId")
                                                            .gitRepoType(GitRepositoryType.HELM)
                                                            .repoUrl("http://github.com/my-repo")
                                                            .build())
                                             .build());
    assertThat(repoDirectory)
        .isEqualTo("./repository/helm/accountId/id/my-repo/9d0502fc8d289f365a3fdcb24607c878b68fad36");
  }

  @Test
  @Owner(developers = OwnerRule.FERNANDOD)
  @Category(UnitTests.class)
  public void shouldGetLockObjectWithoutRetry() throws ExecutionException {
    gitClientHelper.getLockObject(GIT_CONNECTOR_ID);
    verify(cache).get(GIT_CONNECTOR_ID);
  }

  @Test
  @Owner(developers = OwnerRule.FERNANDOD)
  @Category(UnitTests.class)
  public void shouldGetLockObjectUsingRetry() throws ExecutionException {
    Mockito.when(cache.get(GIT_CONNECTOR_ID)).thenThrow(RuntimeException.class);

    Assertions.assertThatThrownBy(() -> gitClientHelper.getLockObject(GIT_CONNECTOR_ID))
        .isInstanceOf(WingsException.class)
        .hasMessage(ErrorCode.GENERAL_ERROR.name());

    verify(cache, times(2)).get(GIT_CONNECTOR_ID);
    verify(cache).invalidate(GIT_CONNECTOR_ID);
  }
}
