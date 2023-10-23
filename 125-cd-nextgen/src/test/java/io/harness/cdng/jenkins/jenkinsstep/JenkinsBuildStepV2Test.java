/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.jenkins.jenkinsstep;

import static io.harness.rule.OwnerRule.ABHISHEK;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.artifacts.jenkins.JenkinsArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.jenkins.JenkinsArtifactDelegateRequest.JenkinsArtifactDelegateRequestBuilder;
import io.harness.delegate.task.artifacts.response.ArtifactTaskResponse;
import io.harness.exception.InvalidArgumentsException;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.LogStreamingStepClientImpl;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class JenkinsBuildStepV2Test extends CategoryTest {
  private static final String CONNECTOR = "ConnectorDisconnectHandler";
  private static final String JOB_NAME = "jobName";
  private static final Ambiance AMBIANCE = Ambiance.newBuilder().build();

  @InjectMocks JenkinsBuildStepV2 jenkinsBuildStepV2;
  @Mock LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Mock JenkinsBuildStepHelperService jenkinsBuildStepHelperService;
  @Mock LogStreamingStepClientImpl logStreamingStepClient;

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testStartChainLinkAfterRbac() {
    ParameterField frequency = ParameterField.createValueField("1m");
    ParameterField<String> timeout = ParameterField.createValueField("2m");
    JenkinsBuildSpecParameters jenkinsBuildSpecParameters =
        JenkinsBuildSpecParameters.builder()
            .connectorRef(ParameterField.createValueField(CONNECTOR))
            .jobName(ParameterField.createValueField(JOB_NAME))
            .unstableStatusAsSuccess(true)
            .useConnectorUrlForJobExecution(true)
            .consoleLogPollFrequency(frequency)
            .build();
    mock();
    jenkinsBuildStepV2.startChainLinkAfterRbac(
        AMBIANCE, StepElementParameters.builder().spec(jenkinsBuildSpecParameters).timeout(timeout).build(), null);
    assertConsoleLogPollFrequency(60);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testStartChainLinkAfterRbac_Null() {
    JenkinsBuildSpecParameters jenkinsBuildSpecParameters =
        JenkinsBuildSpecParameters.builder()
            .connectorRef(ParameterField.createValueField(CONNECTOR))
            .jobName(ParameterField.createValueField(JOB_NAME))
            .unstableStatusAsSuccess(true)
            .useConnectorUrlForJobExecution(true)
            .consoleLogPollFrequency(null)
            .build();
    mock();
    jenkinsBuildStepV2.startChainLinkAfterRbac(
        AMBIANCE, StepElementParameters.builder().spec(jenkinsBuildSpecParameters).build(), null);
    assertConsoleLogPollFrequency(5);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testStartChainLinkAfterRbac_LessThanMinimum() {
    ParameterField frequency = ParameterField.createValueField("1s");

    JenkinsBuildSpecParameters jenkinsBuildSpecParameters =
        JenkinsBuildSpecParameters.builder()
            .connectorRef(ParameterField.createValueField(CONNECTOR))
            .jobName(ParameterField.createValueField(JOB_NAME))
            .unstableStatusAsSuccess(true)
            .useConnectorUrlForJobExecution(true)
            .consoleLogPollFrequency(frequency)
            .build();
    mock();
    jenkinsBuildStepV2.startChainLinkAfterRbac(
        AMBIANCE, StepElementParameters.builder().spec(jenkinsBuildSpecParameters).build(), null);
    assertConsoleLogPollFrequency(5);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testStartChainLinkAfterRbac_InvalidValue() {
    ParameterField frequency = ParameterField.createValueField("1");

    JenkinsBuildSpecParameters jenkinsBuildSpecParameters =
        JenkinsBuildSpecParameters.builder()
            .connectorRef(ParameterField.createValueField(CONNECTOR))
            .jobName(ParameterField.createValueField(JOB_NAME))
            .unstableStatusAsSuccess(true)
            .useConnectorUrlForJobExecution(true)
            .consoleLogPollFrequency(frequency)
            .build();
    mock();
    assertThatThrownBy(()
                           -> jenkinsBuildStepV2.startChainLinkAfterRbac(AMBIANCE,
                               StepElementParameters.builder().spec(jenkinsBuildSpecParameters).build(), null))
        .isInstanceOf(InvalidArgumentsException.class);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testStartChainLinkAfterRbac_ResolvedNull() {
    ParameterField frequency = ParameterField.createValueField("null");

    JenkinsBuildSpecParameters jenkinsBuildSpecParameters =
        JenkinsBuildSpecParameters.builder()
            .connectorRef(ParameterField.createValueField(CONNECTOR))
            .jobName(ParameterField.createValueField(JOB_NAME))
            .unstableStatusAsSuccess(true)
            .useConnectorUrlForJobExecution(true)
            .consoleLogPollFrequency(frequency)
            .build();
    mock();
    jenkinsBuildStepV2.startChainLinkAfterRbac(
        AMBIANCE, StepElementParameters.builder().spec(jenkinsBuildSpecParameters).build(), null);
    assertConsoleLogPollFrequency(5);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testStartChainLinkAfterRbac_Expression() {
    ParameterField<String> frequency = ParameterField.createExpressionField(true, "<+abc>", null, true);

    JenkinsBuildSpecParameters jenkinsBuildSpecParameters =
        JenkinsBuildSpecParameters.builder()
            .connectorRef(ParameterField.createValueField(CONNECTOR))
            .jobName(ParameterField.createValueField(JOB_NAME))
            .unstableStatusAsSuccess(true)
            .useConnectorUrlForJobExecution(true)
            .consoleLogPollFrequency(frequency)
            .build();
    mock();
    jenkinsBuildStepV2.startChainLinkAfterRbac(
        AMBIANCE, StepElementParameters.builder().spec(jenkinsBuildSpecParameters).build(), null);
    assertConsoleLogPollFrequency(5);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testStartChainLinkAfterRbac_ValueGreaterThanTimeout() {
    ParameterField<String> frequency = ParameterField.createValueField("1m");
    ParameterField<String> timeout = ParameterField.createValueField("30s");

    JenkinsBuildSpecParameters jenkinsBuildSpecParameters =
        JenkinsBuildSpecParameters.builder()
            .connectorRef(ParameterField.createValueField(CONNECTOR))
            .jobName(ParameterField.createValueField(JOB_NAME))
            .unstableStatusAsSuccess(true)
            .useConnectorUrlForJobExecution(true)
            .consoleLogPollFrequency(frequency)
            .build();
    mock();
    jenkinsBuildStepV2.startChainLinkAfterRbac(
        AMBIANCE, StepElementParameters.builder().spec(jenkinsBuildSpecParameters).timeout(timeout).build(), null);
    assertConsoleLogPollFrequency(5);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testExecuteNextLinkWithSecurityContextAndNodeInfo() throws Exception {
    ParameterField frequency = ParameterField.createValueField("1m");
    ParameterField<String> timeout = ParameterField.createValueField("2m");
    JenkinsBuildSpecParameters jenkinsBuildSpecParameters =
        JenkinsBuildSpecParameters.builder()
            .connectorRef(ParameterField.createValueField(CONNECTOR))
            .jobName(ParameterField.createValueField(JOB_NAME))
            .unstableStatusAsSuccess(true)
            .useConnectorUrlForJobExecution(true)
            .consoleLogPollFrequency(frequency)
            .build();
    mock();
    jenkinsBuildStepV2.executeNextLinkWithSecurityContextAndNodeInfo(AMBIANCE,
        StepElementParameters.builder().timeout(timeout).spec(jenkinsBuildSpecParameters).build(), null, null,
        () -> ArtifactTaskResponse.builder().build());
    assertConsoleLogPollFrequencyForExecuteNextLink(60);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testExecuteNextLinkWithSecurityContextAndNodeInfo_Null() throws Exception {
    JenkinsBuildSpecParameters jenkinsBuildSpecParameters =
        JenkinsBuildSpecParameters.builder()
            .connectorRef(ParameterField.createValueField(CONNECTOR))
            .jobName(ParameterField.createValueField(JOB_NAME))
            .unstableStatusAsSuccess(true)
            .useConnectorUrlForJobExecution(true)
            .consoleLogPollFrequency(null)
            .build();
    mock();
    jenkinsBuildStepV2.executeNextLinkWithSecurityContextAndNodeInfo(AMBIANCE,
        StepElementParameters.builder().spec(jenkinsBuildSpecParameters).build(), null, null,
        () -> ArtifactTaskResponse.builder().build());
    assertConsoleLogPollFrequencyForExecuteNextLink(5);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testExecuteNextLinkWithSecurityContextAndNodeInfo_LessThanMinimum() throws Exception {
    ParameterField frequency = ParameterField.createValueField("1s");

    JenkinsBuildSpecParameters jenkinsBuildSpecParameters =
        JenkinsBuildSpecParameters.builder()
            .connectorRef(ParameterField.createValueField(CONNECTOR))
            .jobName(ParameterField.createValueField(JOB_NAME))
            .unstableStatusAsSuccess(true)
            .useConnectorUrlForJobExecution(true)
            .consoleLogPollFrequency(frequency)
            .build();
    mock();
    jenkinsBuildStepV2.executeNextLinkWithSecurityContextAndNodeInfo(AMBIANCE,
        StepElementParameters.builder().spec(jenkinsBuildSpecParameters).build(), null, null,
        () -> ArtifactTaskResponse.builder().build());
    assertConsoleLogPollFrequencyForExecuteNextLink(5);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testExecuteNextLinkWithSecurityContextAndNodeInfo_InvalidValue() {
    ParameterField frequency = ParameterField.createValueField("1");

    JenkinsBuildSpecParameters jenkinsBuildSpecParameters =
        JenkinsBuildSpecParameters.builder()
            .connectorRef(ParameterField.createValueField(CONNECTOR))
            .jobName(ParameterField.createValueField(JOB_NAME))
            .unstableStatusAsSuccess(true)
            .useConnectorUrlForJobExecution(true)
            .consoleLogPollFrequency(frequency)
            .build();
    mock();
    assertThatThrownBy(()
                           -> jenkinsBuildStepV2.executeNextLinkWithSecurityContextAndNodeInfo(AMBIANCE,
                               StepElementParameters.builder().spec(jenkinsBuildSpecParameters).build(), null, null,
                               () -> ArtifactTaskResponse.builder().build()))
        .isInstanceOf(InvalidArgumentsException.class);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testExecuteNextLinkWithSecurityContextAndNodeInfo_ResolvedNull() throws Exception {
    ParameterField frequency = ParameterField.createValueField("null");

    JenkinsBuildSpecParameters jenkinsBuildSpecParameters =
        JenkinsBuildSpecParameters.builder()
            .connectorRef(ParameterField.createValueField(CONNECTOR))
            .jobName(ParameterField.createValueField(JOB_NAME))
            .unstableStatusAsSuccess(true)
            .useConnectorUrlForJobExecution(true)
            .consoleLogPollFrequency(frequency)
            .build();
    mock();
    jenkinsBuildStepV2.executeNextLinkWithSecurityContextAndNodeInfo(AMBIANCE,
        StepElementParameters.builder().spec(jenkinsBuildSpecParameters).build(), null, null,
        () -> ArtifactTaskResponse.builder().build());
    assertConsoleLogPollFrequencyForExecuteNextLink(5);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testExecuteNextLinkWithSecurityContextAndNodeInfo_Expression() throws Exception {
    ParameterField<String> frequency = ParameterField.createExpressionField(true, "<+abc>", null, true);

    JenkinsBuildSpecParameters jenkinsBuildSpecParameters =
        JenkinsBuildSpecParameters.builder()
            .connectorRef(ParameterField.createValueField(CONNECTOR))
            .jobName(ParameterField.createValueField(JOB_NAME))
            .unstableStatusAsSuccess(true)
            .useConnectorUrlForJobExecution(true)
            .consoleLogPollFrequency(frequency)
            .build();
    mock();
    jenkinsBuildStepV2.executeNextLinkWithSecurityContextAndNodeInfo(AMBIANCE,
        StepElementParameters.builder().spec(jenkinsBuildSpecParameters).build(), null, null,
        () -> ArtifactTaskResponse.builder().build());
    assertConsoleLogPollFrequencyForExecuteNextLink(5);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testExecuteNextLinkWithSecurityContextAndNodeInfo_ValueGreaterThanTimeout() throws Exception {
    ParameterField<String> frequency = ParameterField.createValueField("1m");
    ParameterField<String> timeout = ParameterField.createValueField("30s");

    JenkinsBuildSpecParameters jenkinsBuildSpecParameters =
        JenkinsBuildSpecParameters.builder()
            .connectorRef(ParameterField.createValueField(CONNECTOR))
            .jobName(ParameterField.createValueField(JOB_NAME))
            .unstableStatusAsSuccess(true)
            .useConnectorUrlForJobExecution(true)
            .consoleLogPollFrequency(frequency)
            .build();
    mock();
    jenkinsBuildStepV2.executeNextLinkWithSecurityContextAndNodeInfo(AMBIANCE,
        StepElementParameters.builder().timeout(timeout).spec(jenkinsBuildSpecParameters).build(), null, null,
        () -> ArtifactTaskResponse.builder().build());
    assertConsoleLogPollFrequencyForExecuteNextLink(5);
  }

  private void mock() {
    when(jenkinsBuildStepHelperService.queueJenkinsBuildTask(any(), any(), any())).thenReturn(null);
    when(jenkinsBuildStepHelperService.pollJenkinsJob(any(), any(), any(), any())).thenReturn(null);
    when(logStreamingStepClientFactory.getLogStreamingStepClient(any())).thenReturn(logStreamingStepClient);
  }

  private void assertConsoleLogPollFrequency(long value) {
    ArgumentCaptor<JenkinsArtifactDelegateRequestBuilder> argumentCaptor =
        ArgumentCaptor.forClass(JenkinsArtifactDelegateRequestBuilder.class);
    verify(jenkinsBuildStepHelperService).queueJenkinsBuildTask(argumentCaptor.capture(), eq(AMBIANCE), any());
    JenkinsArtifactDelegateRequestBuilder builder = argumentCaptor.getValue();
    JenkinsArtifactDelegateRequest request = builder.build();

    assertThat(request.getConsoleLogFrequency()).isEqualTo(value);
  }

  private void assertConsoleLogPollFrequencyForExecuteNextLink(long value) {
    ArgumentCaptor<JenkinsArtifactDelegateRequestBuilder> argumentCaptor =
        ArgumentCaptor.forClass(JenkinsArtifactDelegateRequestBuilder.class);
    verify(jenkinsBuildStepHelperService).pollJenkinsJob(argumentCaptor.capture(), eq(AMBIANCE), any(), any());
    JenkinsArtifactDelegateRequestBuilder builder = argumentCaptor.getValue();
    JenkinsArtifactDelegateRequest request = builder.build();

    assertThat(request.getConsoleLogFrequency()).isEqualTo(value);
  }
}