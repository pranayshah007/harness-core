/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.aws.sam;

import static io.harness.rule.OwnerRule.IVAN;
import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.nodes.GitCloneStepNode;
import io.harness.beans.steps.stepinfo.GitCloneStepInfo;
import io.harness.category.element.UnitTests;
import io.harness.cdng.manifest.steps.outcome.ManifestsOutcome;
import io.harness.cdng.manifest.yaml.AwsSamDirectoryManifestOutcome;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.manifest.yaml.ValuesManifestOutcome;
import io.harness.cdng.pipeline.steps.CdAbstractStepNode;
import io.harness.cdng.plugininfoproviders.GitClonePluginInfoProvider;
import io.harness.delegate.task.stepstatus.StepExecutionStatus;
import io.harness.delegate.task.stepstatus.StepMapOutput;
import io.harness.delegate.task.stepstatus.StepOutput;
import io.harness.delegate.task.stepstatus.StepStatus;
import io.harness.delegate.task.stepstatus.StepStatusTaskResponseData;
import io.harness.exception.InvalidRequestException;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.plugin.GitCloneStep;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.PluginCreationRequest;
import io.harness.pms.contracts.plan.PluginCreationResponse;
import io.harness.pms.contracts.plan.PluginCreationResponseList;
import io.harness.pms.contracts.plan.PluginCreationResponseWrapper;
import io.harness.pms.contracts.plan.PluginDetails;
import io.harness.pms.expression.EngineExpressionService;
import io.harness.pms.sdk.core.data.OptionalOutcome;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.tasks.ResponseData;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.groovy.util.Maps;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class AwsSamDownloadManifestsStepHelperTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private OutcomeService outcomeService;

  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;

  @Mock private EngineExpressionService engineExpressionService;

  @Mock private GitClonePluginInfoProvider gitClonePluginInfoProvider;

  @Mock private DownloadManifestsCommonHelper downloadManifestsCommonHelper;
  @Mock private AwsSamStepHelper awsSamStepHelper;
  @InjectMocks @Spy private AwsSamDownloadManifestsStepHelper awsSamDownloadManifestsStepHelper;

  @Before
  public void setup() {}

  @SneakyThrows
  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testGetPluginInfoList() {
    String stepJsonNode = "json";
    PluginCreationRequest pluginCreationRequest =
        PluginCreationRequest.newBuilder().setStepJsonNode(stepJsonNode).build();

    CdAbstractStepNode cdAbstractStepNode = mock(CdAbstractStepNode.class);

    Mockito.mockStatic(YamlUtils.class);
    when(YamlUtils.read(stepJsonNode, CdAbstractStepNode.class)).thenReturn(cdAbstractStepNode);
    when(YamlUtils.writeYamlString(any(Object.class))).thenReturn("yaml1");

    ManifestsOutcome manifestsOutcome = mock(ManifestsOutcome.class);
    doReturn(OptionalOutcome.builder().outcome(manifestsOutcome).build())
        .when(outcomeService)
        .resolveOptional(any(), any());

    ManifestOutcome valuesManifestOutcome = mock(ManifestOutcome.class);
    doReturn(Arrays.asList(valuesManifestOutcome)).when(manifestsOutcome).values();

    AwsSamDirectoryManifestOutcome awsSamDirectoryManifestOutcome = AwsSamDirectoryManifestOutcome.builder().build();
    doReturn(awsSamDirectoryManifestOutcome).when(awsSamStepHelper).getAwsSamDirectoryManifestOutcome(any());

    GitCloneStepInfo gitCloneStepInfo = mock(GitCloneStepInfo.class);
    doReturn(gitCloneStepInfo).when(downloadManifestsCommonHelper).getGitCloneStepInfoFromManifestOutcome(any());

    GitCloneStepNode gitCloneStepNode = mock(GitCloneStepNode.class);
    doReturn(gitCloneStepNode).when(downloadManifestsCommonHelper).getGitCloneStepNode(any(), any(), any());

    ValuesManifestOutcome valuesManifestOutcome1 = ValuesManifestOutcome.builder().build();
    doReturn(valuesManifestOutcome1).when(awsSamStepHelper).getAwsSamValuesManifestOutcome(any());

    PluginCreationResponseWrapper pluginCreationResponseWrapper = mock(PluginCreationResponseWrapper.class);
    doReturn(pluginCreationResponseWrapper).when(gitClonePluginInfoProvider).getPluginInfo(any(), any(), any());

    PluginCreationResponse pluginCreationResponse = mock(PluginCreationResponse.class);
    doReturn(pluginCreationResponse).when(pluginCreationResponseWrapper).getResponse();

    PluginDetails pluginDetails = mock(PluginDetails.class);
    doReturn(pluginDetails).when(pluginCreationResponse).getPluginDetails();

    doReturn(Arrays.asList(32)).when(pluginDetails).getPortUsedList();

    String accountId = "accountId";
    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions("accountId", accountId).build();

    PluginCreationResponseList pluginCreationResponseList =
        awsSamDownloadManifestsStepHelper.getPluginInfoList(pluginCreationRequest, new HashSet<>(), ambiance);
    assertThat(pluginCreationResponseList.getResponseList().size()).isEqualTo(2);
  }

  @SneakyThrows
  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testHandleAsyncResponse() {
    String accountId = "accountId";
    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions("accountId", accountId).build();

    ManifestsOutcome manifestsOutcome = mock(ManifestsOutcome.class);
    doReturn(OptionalOutcome.builder().outcome(manifestsOutcome).build())
        .when(outcomeService)
        .resolveOptional(any(), any());

    ValuesManifestOutcome valuesManifestOutcome1 = ValuesManifestOutcome.builder().build();
    doReturn(valuesManifestOutcome1).when(awsSamStepHelper).getAwsSamValuesManifestOutcome(any());

    Map<String, ResponseData> responseDataMap = new HashMap<>();

    String valuesYamlPath = "yaml";
    HashMap<String, String> map = new HashMap<>();
    map.put(valuesYamlPath, "content");
    StepOutput stepOutput = StepMapOutput.builder().map(map).build();
    ResponseData responseData =
        StepStatusTaskResponseData.builder()
            .stepStatus(
                StepStatus.builder().output(stepOutput).stepExecutionStatus(StepExecutionStatus.SUCCESS).build())
            .build();
    responseDataMap.put("key", responseData);

    doReturn(valuesYamlPath).when(awsSamStepHelper).getValuesPathFromValuesManifestOutcome(any());
    String valuesYamlContent = "content";
    doReturn(valuesYamlContent).when(engineExpressionService).renderExpression(any(), any());

    StepResponse stepResponse = awsSamDownloadManifestsStepHelper.handleAsyncResponse(ambiance, responseDataMap);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
  }

  @SneakyThrows
  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testExecuteAsyncAfterRbac() {
    String accountId = "accountId";
    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions("accountId", accountId).build();

    ManifestsOutcome manifestsOutcome = mock(ManifestsOutcome.class);
    doReturn(manifestsOutcome).when(downloadManifestsCommonHelper).fetchManifestsOutcome(ambiance);

    AwsSamDirectoryManifestOutcome awsSamDirectoryManifestOutcome = AwsSamDirectoryManifestOutcome.builder().build();
    doReturn(awsSamDirectoryManifestOutcome).when(awsSamStepHelper).getAwsSamDirectoryManifestOutcome(any());

    GitCloneStepInfo gitCloneStepInfo = mock(GitCloneStepInfo.class);
    doReturn(gitCloneStepInfo).when(downloadManifestsCommonHelper).getGitCloneStepInfoFromManifestOutcome(any());

    StepElementParameters stepElementParameters = StepElementParameters.builder().build();
    doReturn(stepElementParameters).when(downloadManifestsCommonHelper).getGitStepElementParameters(any(), any());

    doReturn("iden").when(downloadManifestsCommonHelper).getGitCloneStepIdentifier(any());

    doReturn(ambiance).when(downloadManifestsCommonHelper).buildAmbianceForGitClone(any(), any());

    GitCloneStep gitCloneStep = mock(GitCloneStep.class);

    AsyncExecutableResponse asyncExecutableResponse =
        AsyncExecutableResponse.newBuilder().addCallbackIds("1").addLogKeys("1").setStatus(Status.RUNNING).build();
    doReturn(asyncExecutableResponse).when(gitCloneStep).executeAsyncAfterRbac(any(), any(), any());

    ManifestOutcome valuesManifestOutcome = mock(ManifestOutcome.class);
    doReturn(Arrays.asList(valuesManifestOutcome)).when(manifestsOutcome).values();

    ValuesManifestOutcome valuesManifestOutcome1 = ValuesManifestOutcome.builder().build();
    doReturn(valuesManifestOutcome1).when(awsSamStepHelper).getAwsSamValuesManifestOutcome(any());

    AsyncExecutableResponse asyncExecutableResponse1 = awsSamDownloadManifestsStepHelper.executeAsyncAfterRbac(
        ambiance, StepInputPackage.builder().build(), gitCloneStep);

    assertThat(asyncExecutableResponse1.getCallbackIdsList().size()).isEqualTo(2);
    assertThat(asyncExecutableResponse1.getLogKeysList().size()).isEqualTo(2);
    assertThat(asyncExecutableResponse1.getStatus()).isEqualTo(Status.RUNNING);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetValuesManifestContent() {
    String valuesYamlPath = "harness/path/to/values.yaml";
    String valuesManifestContent = awsSamDownloadManifestsStepHelper.getValuesManifestContent(
        StepMapOutput.builder().map(Maps.of(valuesYamlPath, "dmFsdWVzIHlhbW wgY29udGVudA--")).build(), valuesYamlPath);

    assertThat(valuesManifestContent).isEqualTo("values yaml content");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetValuesManifestContentEmpty() {
    String valuesYamlPath = "harness/path/to/values.yaml";
    String valuesManifestContent = awsSamDownloadManifestsStepHelper.getValuesManifestContent(
        StepMapOutput.builder().map(Maps.of(valuesYamlPath, null)).build(), valuesYamlPath);

    assertThat(valuesManifestContent).isEmpty();
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetValuesManifestContentWithIllegalBase64Character() {
    String valuesYamlPath = "harness/path/to/values.yaml";
    assertThatThrownBy(
        ()
            -> awsSamDownloadManifestsStepHelper.getValuesManifestContent(
                StepMapOutput.builder().map(Maps.of(valuesYamlPath, "$dmFsdWVzIHlhbWwgY29udGVudA--")).build(),
                valuesYamlPath))
        .hasMessage("Unable to fetch values YAML, valuesYamlPath: harness/path/to/values.yaml")
        .isInstanceOf(InvalidRequestException.class);
  }
}