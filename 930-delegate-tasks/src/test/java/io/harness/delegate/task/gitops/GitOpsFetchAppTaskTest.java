/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.gitops;

import static io.harness.delegate.beans.TaskData.DEFAULT_ASYNC_CALL_TIMEOUT;
import static io.harness.rule.OwnerRule.VAIBHAV_SI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.task.git.TaskStatus;
import io.harness.exception.InvalidRequestException;
import io.harness.git.model.FetchFilesResult;
import io.harness.git.model.GitFile;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.Collections;
import org.jose4j.lang.JoseException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(HarnessTeam.GITOPS)
public class GitOpsFetchAppTaskTest extends CategoryTest {
  @Mock GitOpsTaskHelper gitOpsTaskHelper;
  @InjectMocks
  GitOpsFetchAppTask gitOpsFetchAppTask =
      new GitOpsFetchAppTask(DelegateTaskPackage.builder()
                                 .delegateId("DELEGATE_ID")
                                 .delegateTaskId("TASK_ID")
                                 .data(TaskData.builder().async(true).timeout(DEFAULT_ASYNC_CALL_TIMEOUT).build())
                                 .build(),
          null, delegateTaskResponse -> {}, () -> true);

  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void shouldReturnFailureWhenGitFetchFails() throws IOException, JoseException {
    GitOpsFetchAppTaskParams taskParams = GitOpsFetchAppTaskParams.builder().build();
    doThrow(new InvalidRequestException("Failed to fetch"))
        .when(gitOpsTaskHelper)
        .getFetchFilesResult(any(), any(), any(), anyBoolean());

    GitOpsFetchAppTaskResponse taskResponse = (GitOpsFetchAppTaskResponse) gitOpsFetchAppTask.run(taskParams);
    assertThat(taskResponse).isNotNull();
    assertThat(taskResponse.getTaskStatus()).isEqualTo(TaskStatus.FAILURE);
    assertThat(taskResponse.getErrorMessage()).contains("Failed to fetch");
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void shouldReturnFailureWhenParsingFails() throws IOException, JoseException {
    GitOpsFetchAppTaskParams taskParams = GitOpsFetchAppTaskParams.builder().build();
    FetchFilesResult fetchFilesResult =
        FetchFilesResult.builder()
            .files(Collections.singletonList(GitFile.builder().fileContent("abc").build()))
            .build();
    doReturn(fetchFilesResult).when(gitOpsTaskHelper).getFetchFilesResult(any(), any(), any(), anyBoolean());

    GitOpsFetchAppTaskResponse taskResponse = (GitOpsFetchAppTaskResponse) gitOpsFetchAppTask.run(taskParams);
    assertThat(taskResponse).isNotNull();
    assertThat(taskResponse.getTaskStatus()).isEqualTo(TaskStatus.FAILURE);
    assertThat(taskResponse.getErrorMessage()).contains("Failed to parse yaml file");
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void shouldReturnSuccessTaskResponse() throws IOException, JoseException {
    GitOpsFetchAppTaskParams taskParams = GitOpsFetchAppTaskParams.builder().build();
    FetchFilesResult fetchFilesResult =
        FetchFilesResult.builder()
            .files(Collections.singletonList(GitFile.builder().fileContent("metadata:\n  name: appset").build()))
            .build();
    doReturn(fetchFilesResult).when(gitOpsTaskHelper).getFetchFilesResult(any(), any(), any(), anyBoolean());

    GitOpsFetchAppTaskResponse taskResponse = (GitOpsFetchAppTaskResponse) gitOpsFetchAppTask.run(taskParams);
    assertThat(taskResponse).isNotNull();
    assertThat(taskResponse.getTaskStatus()).isEqualTo(TaskStatus.SUCCESS);
    assertThat(taskResponse.getAppName()).isEqualTo("appset");
  }
}
