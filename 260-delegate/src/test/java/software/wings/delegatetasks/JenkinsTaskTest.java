/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks;

import static io.harness.delegate.beans.TaskData.DEFAULT_ASYNC_CALL_TIMEOUT;
import static io.harness.eraro.ErrorCode.INVALID_ARTIFACT_SERVER;
import static io.harness.exception.WingsException.USER;
import static io.harness.logging.CommandExecutionStatus.RUNNING;
import static io.harness.rule.OwnerRule.AGORODETKI;
import static io.harness.rule.OwnerRule.BRETT;
import static io.harness.rule.OwnerRule.GARVIT;
import static io.harness.rule.OwnerRule.ROHITKARELIA;
import static io.harness.rule.OwnerRule.SRINIVAS;

import static software.wings.beans.dto.Log.Builder.aLog;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionStatus;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.TaskData;
import io.harness.exception.GeneralException;
import io.harness.exception.WingsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogLevel;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.JenkinsConfig;
import software.wings.beans.JenkinsSubTaskType;
import software.wings.beans.command.JenkinsTaskParams;
import software.wings.beans.dto.Log;
import software.wings.helpers.ext.jenkins.Jenkins;
import software.wings.helpers.ext.jenkins.JenkinsFactory;
import software.wings.helpers.ext.jenkins.model.CustomBuildWithDetails;
import software.wings.service.impl.jenkins.JenkinsUtils;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.sm.states.JenkinsExecutionResponse;

import com.google.inject.Inject;
import com.offbytwo.jenkins.client.JenkinsHttpConnection;
import com.offbytwo.jenkins.model.Build;
import com.offbytwo.jenkins.model.BuildResult;
import com.offbytwo.jenkins.model.BuildWithDetails;
import com.offbytwo.jenkins.model.QueueReference;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import org.apache.http.client.HttpResponseException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/**
 * Created by rishi on 12/16/16.
 */
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class JenkinsTaskTest extends WingsBaseTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @Mock private JenkinsFactory jenkinsFactory;
  @Mock private Jenkins jenkins;
  @Mock private JenkinsHttpConnection jenkinsHttpConnection;
  @Mock private Build build;
  @Mock private CustomBuildWithDetails customBuildWithDetails;
  @Mock private BuildWithDetails buildWithDetails;
  @Mock private QueueReference queueReference;
  @Mock private EncryptionService encryptionService;
  @Mock private DelegateLogService logService;
  @Inject @InjectMocks JenkinsUtils jenkinsUtil;
  @Inject @InjectMocks ExecutorService jenkinsExecutor;
  @Captor private ArgumentCaptor<Log> logsCaptor;
  @Captor private ArgumentCaptor<String> activityCaptor;

  private String jenkinsUrl = "http://jenkins";
  private String buildUrl = "http://jenkins/job/TestJob/job/111";
  private String queueItemUrlPart = "http://jenkins/queue/item/111";
  private String userName = "user1";
  private char[] password = "pass1".toCharArray();
  private String jobName = "job1";
  private String activityId = "activityId";
  private String stateName = "jenkins_state";
  private String appId = "testAppId";
  private JenkinsConfig jenkinsConfig =
      JenkinsConfig.builder().jenkinsUrl(jenkinsUrl).username(userName).password(password).build();
  private Map<String, String> parameters = new HashMap<>();
  private Map<String, String> assertions = new HashMap<>();

  @InjectMocks
  private JenkinsTask jenkinsTask =
      new JenkinsTask(DelegateTaskPackage.builder()
                          .delegateId("delid1")
                          .data(TaskData.builder().async(true).timeout(DEFAULT_ASYNC_CALL_TIMEOUT).build())
                          .build(),
          null, notifyResponseData -> {}, () -> true);

  @Before
  public void setUp() throws Exception {
    on(jenkinsTask).set("jenkinsUtil", jenkinsUtil);
    when(jenkinsFactory.create(anyString(), anyString(), any(char[].class))).thenReturn(jenkins);
    when(jenkins.getBuild(any(QueueReference.class), any(JenkinsConfig.class))).thenReturn(build);
    when(build.getUrl()).thenReturn(buildUrl);
    when(build.details()).thenReturn(buildWithDetails);
    when(buildWithDetails.getClient()).thenReturn(jenkinsHttpConnection);
    when(customBuildWithDetails.details()).thenReturn(customBuildWithDetails);
    when(jenkinsHttpConnection.get(any(), any())).thenReturn(customBuildWithDetails);
    when(buildWithDetails.isBuilding()).thenReturn(false);
    when(buildWithDetails.getConsoleOutputText()).thenReturn("console output");
    on(jenkinsTask).set("jenkinsExecutor", jenkinsExecutor);
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldExecuteSuccessfullyWhenBuildPasses() throws Exception {
    when(customBuildWithDetails.getResult()).thenReturn(BuildResult.SUCCESS);
    when(customBuildWithDetails.getDescription()).thenReturn("test-description");

    JenkinsTaskParams params = buildJenkinsTaskParams();
    params.setParameters(Collections.emptyMap());
    params.setSubTaskType(JenkinsSubTaskType.START_TASK);
    params.setQueuedBuildUrl(jenkinsUrl);

    // Invoke Jenkins Start Task
    when(jenkins.trigger(jobName, params)).thenReturn(new QueueReference(jenkinsUrl));
    JenkinsExecutionResponse response = jenkinsTask.run(params);
    verify(jenkinsFactory).create(jenkinsUrl, userName, password);
    verify(jenkins).trigger(jobName, params);
    assertThat(response.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);

    // Invoke Jenkins Poll Task
    params.setSubTaskType(JenkinsSubTaskType.POLL_TASK);
    response = jenkinsTask.run(params);
    assertThat(response.getDescription()).isEqualTo("test-description");
    assertThat(response.getEnvVars()).isNullOrEmpty();
    assertThat(response.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldFailWhenBuildFails() throws Exception {
    when(customBuildWithDetails.getResult()).thenReturn(BuildResult.FAILURE);

    JenkinsTaskParams params = buildJenkinsTaskParams();
    params.setParameters(Collections.emptyMap());
    params.setSubTaskType(JenkinsSubTaskType.START_TASK);
    params.setQueuedBuildUrl(jenkinsUrl);

    // Jenkins Start Task
    when(jenkins.trigger(jobName, params)).thenReturn(new QueueReference(jenkinsUrl));
    JenkinsExecutionResponse response = jenkinsTask.run(params);
    verify(jenkinsFactory).create(jenkinsUrl, userName, password);
    verify(jenkins).trigger(jobName, params);
    assertThat(response.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);

    // Jenkins Poll Task
    params.setSubTaskType(JenkinsSubTaskType.POLL_TASK);
    response = jenkinsTask.run(params);
    assertThat(response.getEnvVars()).isNullOrEmpty();
    assertThat(response.getExecutionStatus()).isEqualTo(ExecutionStatus.FAILED);
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldFailWhenBuildUnstable() throws Exception {
    when(customBuildWithDetails.getResult()).thenReturn(BuildResult.UNSTABLE);

    JenkinsTaskParams params = buildJenkinsTaskParams();
    params.setParameters(Collections.emptyMap());
    params.setSubTaskType(JenkinsSubTaskType.START_TASK);
    params.setQueuedBuildUrl(jenkinsUrl);

    // Jenkins Start Task
    when(jenkins.trigger(jobName, params)).thenReturn(new QueueReference(jenkinsUrl));
    JenkinsExecutionResponse response = jenkinsTask.run(params);
    verify(jenkinsFactory).create(jenkinsUrl, userName, password);
    verify(jenkins).trigger(jobName, params);
    assertThat(response.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);

    // Jenkins Poll Task
    params.setSubTaskType(JenkinsSubTaskType.POLL_TASK);
    response = jenkinsTask.run(params);
    assertThat(response.getExecutionStatus()).isEqualTo(ExecutionStatus.FAILED);
  }

  @Test
  @Owner(developers = SRINIVAS)
  @Category(UnitTests.class)
  public void shouldFailWhenNoJobFound() throws Exception {
    JenkinsTaskParams params = buildJenkinsTaskParams();
    params.setParameters(Collections.emptyMap());
    params.setSubTaskType(JenkinsSubTaskType.START_TASK);
    params.setQueuedBuildUrl(jenkinsUrl);

    when(customBuildWithDetails.details()).thenThrow(new HttpResponseException(404, "Job Not found"));

    JenkinsExecutionResponse response = jenkinsTask.run(params);
    verify(jenkinsFactory).create(jenkinsUrl, userName, password);
    verify(jenkins).trigger(jobName, params);
    assertThat(response.getExecutionStatus()).isEqualTo(ExecutionStatus.FAILED);
    assertThat(response.getErrorMessage()).isNotEmpty();
  }

  @Test
  @Owner(developers = BRETT)
  @Category(UnitTests.class)
  public void shouldPassWhenBuildUnstableAndUnstableSuccessSet() throws Exception {
    when(customBuildWithDetails.getResult()).thenReturn(BuildResult.UNSTABLE);

    JenkinsTaskParams params = buildJenkinsTaskParams();
    params.setParameters(Collections.emptyMap());
    params.setSubTaskType(JenkinsSubTaskType.START_TASK);
    params.setQueuedBuildUrl(jenkinsUrl);
    params.setUnstableSuccess(true);

    when(jenkins.trigger(jobName, params)).thenReturn(new QueueReference(jenkinsUrl));
    JenkinsExecutionResponse response = jenkinsTask.run(params);
    verify(jenkinsFactory).create(jenkinsUrl, userName, password);
    verify(jenkins).trigger(jobName, params);
    verify(jenkins).getBuild(any(QueueReference.class), any(JenkinsConfig.class));
    assertThat(response.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);

    // Jenkins Poll Task
    params.setSubTaskType(JenkinsSubTaskType.POLL_TASK);
    response = jenkinsTask.run(params);
    assertThat(response.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldInjectEnvVarsWhenInjectEnvVarsSet() throws Exception {
    when(customBuildWithDetails.getResult()).thenReturn(BuildResult.SUCCESS);
    when(customBuildWithDetails.getDescription()).thenReturn("test-description");

    JenkinsTaskParams params = buildJenkinsTaskParams();
    params.setParameters(Collections.emptyMap());
    params.setSubTaskType(JenkinsSubTaskType.START_TASK);
    params.setQueuedBuildUrl(jenkinsUrl);
    params.setInjectEnvVars(true);

    // Invoke Jenkins Start Task
    when(jenkins.trigger(jobName, params)).thenReturn(new QueueReference(jenkinsUrl));
    JenkinsExecutionResponse response = jenkinsTask.run(params);
    verify(jenkinsFactory).create(jenkinsUrl, userName, password);
    verify(jenkins).trigger(jobName, params);
    assertThat(response.getEnvVars()).isNullOrEmpty();
    assertThat(response.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);

    // Invoke Jenkins Poll Task
    Map<String, String> envVars = new HashMap<>();
    String env1 = "ENV1";
    envVars.put(env1, env1 + "_VAL");
    when(jenkins.getEnvVars(any())).thenReturn(envVars);
    params.setSubTaskType(JenkinsSubTaskType.POLL_TASK);
    response = jenkinsTask.run(params);
    assertThat(response.getDescription()).isEqualTo("test-description");
    assertThat(response.getEnvVars()).isNotEmpty().containsOnly(entry(env1, envVars.get(env1)));
    assertThat(response.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void shouldFailWhenGetEnvVarsThrows() throws Exception {
    when(customBuildWithDetails.getResult()).thenReturn(BuildResult.SUCCESS);
    when(customBuildWithDetails.getDescription()).thenReturn("test-description");

    JenkinsTaskParams params = buildJenkinsTaskParams();
    params.setParameters(Collections.emptyMap());
    params.setSubTaskType(JenkinsSubTaskType.START_TASK);
    params.setQueuedBuildUrl(jenkinsUrl);
    params.setInjectEnvVars(true);

    // Invoke Jenkins Start Task
    when(jenkins.trigger(jobName, params)).thenReturn(new QueueReference(jenkinsUrl));
    JenkinsExecutionResponse response = jenkinsTask.run(params);
    verify(jenkinsFactory).create(jenkinsUrl, userName, password);
    verify(jenkins).trigger(jobName, params);
    assertThat(response.getEnvVars()).isNullOrEmpty();
    assertThat(response.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);

    // Invoke Jenkins Poll Task
    Map<String, String> envVars = new HashMap<>();
    String env1 = "ENV1";
    envVars.put(env1, env1 + "_VAL");
    when(jenkins.getEnvVars(any())).thenThrow(new WingsException(INVALID_ARTIFACT_SERVER, USER));
    params.setSubTaskType(JenkinsSubTaskType.POLL_TASK);
    response = jenkinsTask.run(params);
    assertThat(response.getErrorMessage()).isNotBlank();
    assertThat(response.getExecutionStatus()).isEqualTo(ExecutionStatus.FAILED);
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void shouldFailWhenTriggerJobAPIFails() throws Exception {
    JenkinsTaskParams params = buildJenkinsTaskParams();
    params.setParameters(Collections.emptyMap());
    params.setSubTaskType(JenkinsSubTaskType.START_TASK);
    params.setQueuedBuildUrl(jenkinsUrl);

    // Jenkins Start Task
    when(jenkins.trigger(jobName, params)).thenThrow(new GeneralException("Exception"));
    JenkinsExecutionResponse response = jenkinsTask.run(params);
    assertThat(response.getExecutionStatus()).isEqualTo(ExecutionStatus.FAILED);
    assertThat(response.getErrorMessage()).isNotEmpty();
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void shouldFailWhenTriggerJobAPIFailsWithUnknownException() throws Exception {
    JenkinsTaskParams params = buildJenkinsTaskParams();
    params.setParameters(Collections.emptyMap());
    params.setSubTaskType(JenkinsSubTaskType.START_TASK);
    params.setQueuedBuildUrl(jenkinsUrl);

    // Jenkins Start Task
    when(jenkins.trigger(jobName, params)).then(invocationOnMock -> { throw new Exception(); });
    JenkinsExecutionResponse response = jenkinsTask.run(params);
    assertThat(response.getExecutionStatus()).isEqualTo(ExecutionStatus.FAILED);
    assertThat(response.getErrorMessage()).isNotEmpty();
  }

  @Test
  @Owner(developers = AGORODETKI)
  @Category(UnitTests.class)
  public void shouldPassAppIdForLoggingScopeTroughJenkinsTaskParams() {
    JenkinsTaskParams jenkinsTaskParams = buildJenkinsTaskParams();
    Log logObject =
        constructLog(jenkinsTaskParams.getActivityId(), jenkinsTaskParams.getUnitName(), jenkinsTaskParams.getAppId(),
            LogLevel.INFO, "Triggering Jenkins Job : " + jenkinsTaskParams.getJobName(), RUNNING);

    logService.save(jenkinsTaskParams.getActivityId(), logObject);
    verify(logService).save(activityCaptor.capture(), logsCaptor.capture());
    assertThat(logsCaptor.getValue().getAppId()).isEqualTo(appId);
  }

  // Helper functions
  private JenkinsTaskParams buildJenkinsTaskParams() {
    return JenkinsTaskParams.builder()
        .jenkinsConfig(jenkinsConfig)
        .encryptedDataDetails(emptyList())
        .jobName(jobName)
        .parameters(parameters)
        .activityId(activityId)
        .filePathsForAssertion(assertions)
        .unitName(stateName)
        .appId(appId)
        .build();
  }

  private Log constructLog(String activityId, String stateName, String appId, LogLevel logLevel, String logLine,
      CommandExecutionStatus commandExecutionStatus) {
    return aLog()
        .activityId(activityId)
        .commandUnitName(stateName)
        .appId(appId)
        .logLevel(logLevel)
        .logLine(logLine)
        .executionResult(commandExecutionStatus)
        .build();
  }
}
