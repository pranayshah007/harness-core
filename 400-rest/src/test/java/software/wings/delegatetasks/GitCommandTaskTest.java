/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks;

import static io.harness.exception.WingsException.ADMIN_SRE;
import static io.harness.rule.OwnerRule.DEEPAK;
import static io.harness.rule.OwnerRule.FERNANDOD;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.powermock.api.mockito.PowerMockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.eraro.ErrorCode;
import io.harness.exception.YamlException;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.GitConfig;
import software.wings.beans.yaml.GitCommand.GitCommandType;
import software.wings.beans.yaml.GitCommandExecutionResponse;
import software.wings.beans.yaml.GitCommitRequest;
import software.wings.service.intfc.GitService;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.yaml.gitSync.YamlGitConfig;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PL)
public class GitCommandTaskTest extends WingsBaseTest {
  @Mock GitService gitService;
  @Mock ILogStreamingTaskClient logStreamingTaskClient;
  @Mock Consumer<DelegateTaskResponse> consumer;
  @Mock BooleanSupplier preExecute;
  @Mock EncryptionService encryptionService;

  static final String accountId = "accountId";
  final DelegateTaskPackage delegateTaskPackage =
      DelegateTaskPackage.builder().data(TaskData.builder().build()).accountId(accountId).build();

  @InjectMocks
  GitCommandTask gitCommandTask = new GitCommandTask(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void testSanitizationOfLogs() {
    GitCommandType gitCommandType = GitCommandType.COMMIT_AND_PUSH;
    GitConfig gitConfig = GitConfig.builder().password("password".toCharArray()).build();
    GitCommitRequest gitCommitRequest =
        GitCommitRequest.builder().yamlGitConfig(YamlGitConfig.builder().build()).build();
    when(gitService.commitAndPush(any()))
        .thenThrow(new YamlException("Error Message with password, and again password"));
    GitCommandExecutionResponse gitCommandResponse =
        gitCommandTask.run(new Object[] {gitCommandType, gitConfig, null, gitCommitRequest});
    assertThat(gitCommandResponse.getErrorMessage()).isEqualTo("Error Message with #######, and again #######");
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldOperationFailureReturnGeneralYamlErrorCode() {
    GitCommandType gitCommandType = GitCommandType.COMMIT_AND_PUSH;
    GitConfig gitConfig = GitConfig.builder().password("password".toCharArray()).build();
    GitCommitRequest gitCommitRequest =
        GitCommitRequest.builder().yamlGitConfig(YamlGitConfig.builder().build()).build();
    when(gitService.commitAndPush(any())).thenThrow(new YamlException("AN ERROR MESSAGE", ADMIN_SRE));
    GitCommandExecutionResponse gitCommandResponse =
        gitCommandTask.run(new Object[] {gitCommandType, gitConfig, null, gitCommitRequest});
    assertThat(ErrorCode.GENERAL_YAML_ERROR).isEqualTo(gitCommandResponse.getErrorCode());
  }
}
