/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks;

import static io.harness.delegate.beans.TaskData.DEFAULT_ASYNC_CALL_TIMEOUT;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.rule.OwnerRule.AGORODETKI;
import static io.harness.rule.OwnerRule.VGLIJIN;

import static software.wings.beans.TaskType.GCB;
import static software.wings.beans.command.GcbTaskParams.GcbTaskType.POLL;
import static software.wings.beans.command.GcbTaskParams.GcbTaskType.START;
import static software.wings.beans.dto.Log.Builder.aLog;
import static software.wings.delegatetasks.GcbDelegateResponse.gcbDelegateResponseOf;
import static software.wings.utils.WingsTestConstants.ACTIVITY_ID;
import static software.wings.utils.WingsTestConstants.BRANCH_NAME;
import static software.wings.utils.WingsTestConstants.BUILD_ID;
import static software.wings.utils.WingsTestConstants.COMMIT_SHA;
import static software.wings.utils.WingsTestConstants.INLINE_SPEC;
import static software.wings.utils.WingsTestConstants.TAG_NAME;
import static software.wings.utils.WingsTestConstants.TRIGGER_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.task.TaskParameters;
import io.harness.git.model.GitFile;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.serializer.JsonUtils;
import io.harness.utils.Functions;

import software.wings.beans.GcpConfig;
import software.wings.beans.command.GcbTaskParams;
import software.wings.beans.yaml.GitFetchFilesResult;
import software.wings.helpers.ext.gcb.GcbService;
import software.wings.helpers.ext.gcb.models.BuildOperationDetails;
import software.wings.helpers.ext.gcb.models.GcbBuildDetails;
import software.wings.helpers.ext.gcb.models.GcbBuildStatus;
import software.wings.helpers.ext.gcb.models.OperationMeta;
import software.wings.helpers.ext.gcb.models.RepoSource;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.service.intfc.yaml.GitClient;
import software.wings.sm.states.gcbconfigs.GcbOptions;
import software.wings.sm.states.gcbconfigs.GcbRemoteBuildSpec;
import software.wings.sm.states.gcbconfigs.GcbTriggerBuildSpec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.lang3.NotImplementedException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class GcbTaskTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock private GcbService gcbService;
  @Mock private DelegateLogService logService;
  @Mock private GitClient gitClient;
  @Mock private EncryptionService encryptionService;

  private final GcpConfig gcpConfig = GcpConfig.builder().build();

  @InjectMocks
  private final GcbTask task = spy(new GcbTask(
      DelegateTaskPackage.builder()
          .delegateId("delid1")
          .data(TaskData.builder().async(true).taskType(GCB.name()).timeout(DEFAULT_ASYNC_CALL_TIMEOUT).build())
          .build(),
      null, Functions::doNothing, Functions::staticTruth));

  @Test
  @Owner(developers = VGLIJIN)
  @Category(UnitTests.class)
  public void shouldThrowNotImplementedException() {
    assertThatThrownBy(() -> task.run(Mockito.mock(TaskParameters.class))).isInstanceOf(NotImplementedException.class);
  }

  @Test
  @Owner(developers = VGLIJIN)
  @Category(UnitTests.class)
  public void shouldDelegateToRunGcbTaskParams() {
    GcbTaskParams gcbTaskParams = mock(GcbTaskParams.class);
    doReturn(gcbDelegateResponseOf(
                 GcbTaskParams.builder().build(), GcbBuildDetails.builder().status(GcbBuildStatus.SUCCESS).build()))
        .when(task)
        .run(gcbTaskParams);
    task.run(new Object[] {gcbTaskParams});
    verify(task).run(gcbTaskParams);
  }

  @Test
  @Owner(developers = VGLIJIN)
  @Category(UnitTests.class)
  public void shouldCallStart() {
    GcbTaskParams gcbTaskParams = GcbTaskParams.builder().type(START).build();
    GcbDelegateResponse delegateResponse = gcbDelegateResponseOf(
        GcbTaskParams.builder().build(), GcbBuildDetails.builder().status(GcbBuildStatus.WORKING).build());
    doReturn(delegateResponse).when(task).startGcbBuild(gcbTaskParams);
    GcbDelegateResponse actual = task.run(gcbTaskParams);
    verify(task).startGcbBuild(gcbTaskParams);
    assertThat(actual).isEqualTo(delegateResponse);
  }

  @SuppressWarnings("checkstyle:RepetitiveName")
  @Test
  @Owner(developers = VGLIJIN)
  @Category(UnitTests.class)
  public void shouldCallPollGcbTask() {
    GcbTaskParams gcbTaskParams = GcbTaskParams.builder().type(POLL).build();
    GcbDelegateResponse delegateResponse = gcbDelegateResponseOf(
        GcbTaskParams.builder().build(), GcbBuildDetails.builder().status(GcbBuildStatus.WORKING).build());
    doReturn(delegateResponse).when(task).pollGcbBuild(gcbTaskParams);
    GcbDelegateResponse actual = task.run(gcbTaskParams);
    verify(task).pollGcbBuild(gcbTaskParams);
    assertThat(actual).isEqualTo(delegateResponse);
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldExecuteTriggerSpecForBSpecifiedBranch() {
    testTriggerSpecExecution(GcbTriggerBuildSpec.GcbTriggerSource.BRANCH, BRANCH_NAME);
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldExecuteTriggerSpecForBSpecifiedTag() {
    testTriggerSpecExecution(GcbTriggerBuildSpec.GcbTriggerSource.TAG, TAG_NAME);
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldExecuteTriggerSpecForBSpecifiedCommit() {
    testTriggerSpecExecution(GcbTriggerBuildSpec.GcbTriggerSource.COMMIT, COMMIT_SHA);
  }

  @Test
  @Owner(developers = VGLIJIN)
  @Category(UnitTests.class)
  public void shouldCallSaveLogs() {
    AtomicInteger logsCount = new AtomicInteger(0);
    task.saveConsoleLogs(
        logsCount, "activityId", "stateName", CommandExecutionStatus.SUCCESS, "appId", "line1\r\nline1\nline1");
    verify(logService, times(3))
        .save(task.getAccountId(),
            aLog()
                .activityId("activityId")
                .commandUnitName("stateName")
                .appId("appId")
                .logLevel(INFO)
                .logLine("line1")
                .executionResult(CommandExecutionStatus.SUCCESS)
                .build());
    assertThat(logsCount.get()).isEqualTo(3);
  }

  @Test
  @Owner(developers = VGLIJIN)
  @Category(UnitTests.class)
  public void shouldWaitForTheBuildToComplete() {
    List<EncryptedDataDetail> encryptedDataDetails = Collections.emptyList();
    GcbOptions gcbOptions = new GcbOptions();
    GcbTriggerBuildSpec triggerBuildSpec = new GcbTriggerBuildSpec();
    triggerBuildSpec.setSource(GcbTriggerBuildSpec.GcbTriggerSource.BRANCH);
    triggerBuildSpec.setSourceId(BRANCH_NAME);
    triggerBuildSpec.setName(TRIGGER_ID);
    gcbOptions.setTriggerSpec(triggerBuildSpec);

    GcbTaskParams taskParams = GcbTaskParams.builder()
                                   .type(POLL)
                                   .activityId(ACTIVITY_ID)
                                   .gcpConfig(gcpConfig)
                                   .encryptedDataDetails(encryptedDataDetails)
                                   .gcbOptions(gcbOptions)
                                   .buildId(BUILD_ID)
                                   .pollFrequency(0)
                                   .build();

    GcbBuildDetails working =
        GcbBuildDetails.builder().status(GcbBuildStatus.WORKING).id(BUILD_ID).logsBucket("logsBucket").build();
    GcbBuildDetails success =
        GcbBuildDetails.builder().status(GcbBuildStatus.SUCCESS).id(BUILD_ID).logsBucket("logsBucket").build();

    when(gcbService.getBuild(gcpConfig, encryptedDataDetails, BUILD_ID))
        .thenReturn(working)
        .thenReturn(working)
        .thenReturn(success);
    when(gcbService.fetchBuildLogs(gcpConfig, encryptedDataDetails, success.getLogsBucket(), success.getId()))
        .thenReturn("working")
        .thenReturn("working")
        .thenReturn("success");

    doNothing().when(task).saveConsoleLogs(any(), anyString(), anyString(), any(), anyString(), anyString());

    GcbDelegateResponse response = task.pollGcbBuild(taskParams);
    verify(gcbService, times(3)).getBuild(gcpConfig, encryptedDataDetails, BUILD_ID);
    verify(gcbService, times(3))
        .fetchBuildLogs(gcpConfig, encryptedDataDetails, success.getLogsBucket(), success.getId());
    verify(task, times(3)).saveConsoleLogs(any(), anyString(), any(), any(), any(), anyString());
    assertThat(response).isNotNull();
    assertThat(response.getBuild()).isEqualTo(success);
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldExecuteInlineSpec() {
    List<EncryptedDataDetail> encryptedDataDetails = new ArrayList<>();
    GcbBuildDetails gcbBuildDetails = JsonUtils.asObject(INLINE_SPEC, GcbBuildDetails.class);
    GcbOptions gcbOptions = new GcbOptions();
    BuildOperationDetails buildOperationDetails = new BuildOperationDetails();
    OperationMeta operationMeta = new OperationMeta();

    gcbOptions.setInlineSpec(INLINE_SPEC);
    gcbOptions.setSpecSource(GcbOptions.GcbSpecSource.INLINE);
    GcbTaskParams taskParams = GcbTaskParams.builder()
                                   .type(START)
                                   .activityId(ACTIVITY_ID)
                                   .gcpConfig(gcpConfig)
                                   .encryptedDataDetails(encryptedDataDetails)
                                   .gcbOptions(gcbOptions)
                                   .build();

    operationMeta.setBuild(GcbBuildDetails.builder().status(GcbBuildStatus.SUCCESS).build());
    buildOperationDetails.setOperationMeta(operationMeta);

    when(gcbService.createBuild(gcpConfig, encryptedDataDetails, gcbBuildDetails)).thenReturn(buildOperationDetails);

    GcbDelegateResponse response = task.run(taskParams);

    verify(gcbService).createBuild(gcpConfig, encryptedDataDetails, gcbBuildDetails);
    verify(task).fromJsonSpec(INLINE_SPEC);
    assertThat(response).isNotNull();
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldExecuteRemoteSpec() {
    List<EncryptedDataDetail> encryptedDataDetails = new ArrayList<>();
    GcbOptions gcbOptions = new GcbOptions();
    GcbRemoteBuildSpec remoteBuildSpec = new GcbRemoteBuildSpec();
    GitFetchFilesResult gitResult = new GitFetchFilesResult();
    BuildOperationDetails buildOperationDetails = new BuildOperationDetails();
    OperationMeta operationMeta = new OperationMeta();
    GcbBuildDetails gcbBuildDetails = JsonUtils.asObject(INLINE_SPEC, GcbBuildDetails.class);

    gitResult.setFiles(Collections.singletonList(GitFile.builder().fileContent(INLINE_SPEC).build()));
    gcbOptions.setRepositorySpec(remoteBuildSpec);
    gcbOptions.setSpecSource(GcbOptions.GcbSpecSource.REMOTE);
    GcbTaskParams taskParams = GcbTaskParams.builder()
                                   .type(START)
                                   .activityId(ACTIVITY_ID)
                                   .gcpConfig(gcpConfig)
                                   .encryptedDataDetails(encryptedDataDetails)
                                   .gcbOptions(gcbOptions)
                                   .build();

    operationMeta.setBuild(GcbBuildDetails.builder().status(GcbBuildStatus.SUCCESS).build());
    buildOperationDetails.setOperationMeta(operationMeta);

    doReturn(gitResult).when(gitClient).fetchFilesByPath(any(), any(), eq(false));
    when(gcbService.createBuild(gcpConfig, encryptedDataDetails, gcbBuildDetails)).thenReturn(buildOperationDetails);

    GcbDelegateResponse response = task.run(taskParams);

    verify(gcbService).createBuild(gcpConfig, encryptedDataDetails, gcbBuildDetails);
    verify(task).fetchSpecFromGit(taskParams);
    assertThat(response).isNotNull();
  }

  private void testTriggerSpecExecution(GcbTriggerBuildSpec.GcbTriggerSource source, String sourceId) {
    List<EncryptedDataDetail> encryptedDataDetails = new ArrayList<>();
    RepoSource repoSource = new RepoSource();
    GcbOptions gcbOptions = new GcbOptions();
    GcbTriggerBuildSpec triggerConfig = new GcbTriggerBuildSpec();
    BuildOperationDetails buildOperationDetails = new BuildOperationDetails();
    OperationMeta operationMeta = new OperationMeta();

    triggerConfig.setName(TRIGGER_ID);
    triggerConfig.setSource(source);
    triggerConfig.setSourceId(sourceId);
    gcbOptions.setTriggerSpec(triggerConfig);
    gcbOptions.setSpecSource(GcbOptions.GcbSpecSource.TRIGGER);
    if (source.equals(GcbTriggerBuildSpec.GcbTriggerSource.BRANCH)) {
      repoSource.setBranchName(sourceId);
    } else if (source.equals(GcbTriggerBuildSpec.GcbTriggerSource.TAG)) {
      repoSource.setTagName(sourceId);
    } else {
      repoSource.setCommitSha(sourceId);
    }
    GcbTaskParams taskParams = GcbTaskParams.builder()
                                   .type(START)
                                   .activityId(ACTIVITY_ID)
                                   .gcpConfig(gcpConfig)
                                   .encryptedDataDetails(encryptedDataDetails)
                                   .gcbOptions(gcbOptions)
                                   .build();

    operationMeta.setBuild(GcbBuildDetails.builder().status(GcbBuildStatus.SUCCESS).build());
    buildOperationDetails.setOperationMeta(operationMeta);

    when(gcbService.runTrigger(gcpConfig, encryptedDataDetails, TRIGGER_ID, repoSource))
        .thenReturn(buildOperationDetails);

    GcbDelegateResponse response = task.run(taskParams);

    verify(gcbService).runTrigger(gcpConfig, encryptedDataDetails, TRIGGER_ID, repoSource);
    assertThat(response).isNotNull();
  }
}
