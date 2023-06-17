/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.serverless.container.steps;

import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.stepinfo.GitCloneStepInfo;
import io.harness.category.element.UnitTests;
import io.harness.cdng.infra.beans.ServerlessAwsLambdaInfrastructureOutcome;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.manifest.steps.outcome.ManifestsOutcome;
import io.harness.cdng.manifest.yaml.ServerlessAwsLambdaManifestOutcome;
import io.harness.cdng.manifest.yaml.ValuesManifestOutcome;
import io.harness.cdng.serverless.ServerlessStepCommonHelper;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.serverless.ServerlessAwsLambdaFunction;
import io.harness.delegate.task.stepstatus.StepExecutionStatus;
import io.harness.delegate.task.stepstatus.StepMapOutput;
import io.harness.delegate.task.stepstatus.StepStatus;
import io.harness.delegate.task.stepstatus.StepStatusTaskResponseData;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.AsyncExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.refobjects.RefObject;
import io.harness.pms.expression.EngineExpressionService;
import io.harness.pms.sdk.core.data.OptionalOutcome;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepOutcome;
import io.harness.pms.yaml.ParameterField;
import io.harness.product.ci.engine.proto.UnitStep;
import io.harness.rule.Owner;
import io.harness.tasks.ResponseData;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class DownloadServerlessManifestsV2StepTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private ServerlessStepCommonHelper serverlessStepCommonHelper;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;
  @Mock private InstanceInfoService instanceInfoService;

  @Mock private DownloadManifestsStepHelper downloadManifestsStepHelper;

  @Mock private EngineExpressionService engineExpressionService;

  //  @Mock private GitCloneStep gitCloneStep;
  @Mock private OutcomeService outcomeService;

  @InjectMocks @Spy private DownloadServerlessManifestsV2Step downloadServerlessManifestsV2Step;

  @Before
  public void setup() {}

  @SneakyThrows
  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Ignore
  @Category(UnitTests.class)
  public void executeAsyncAfterRbacWhenValuesYamlPresentTest() {
    String accountId = "accountId";
    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions("accountId", accountId).build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().build();

    ManifestsOutcome manifestsOutcome = mock(ManifestsOutcome.class);
    doReturn(manifestsOutcome).when(downloadManifestsStepHelper).fetchManifestsOutcome(any());

    ServerlessAwsLambdaManifestOutcome serverlessAwsLambdaManifestOutcome =
        mock(ServerlessAwsLambdaManifestOutcome.class);
    doReturn(serverlessAwsLambdaManifestOutcome)
        .when(downloadManifestsStepHelper)
        .getServerlessAwsLambdaDirectoryManifestOutcome(any());

    GitCloneStepInfo gitCloneStepInfo = mock(GitCloneStepInfo.class);
    doReturn(gitCloneStepInfo).when(downloadManifestsStepHelper).getGitCloneStepInfoFromManifestOutcome(any());

    StepElementParameters stepElementParameters1 = mock(StepElementParameters.class);
    doReturn(stepElementParameters1).when(downloadManifestsStepHelper).getGitStepElementParameters(any(), any());

    String identifier = "identifier";
    doReturn(identifier).when(downloadManifestsStepHelper).getGitCloneStepIdentifier(any());

    Ambiance ambiance1 = mock(Ambiance.class);
    doReturn(ambiance1).when(downloadManifestsStepHelper).buildAmbianceForGitClone(any(), any());

    AsyncExecutableResponse asyncExecutableResponse = mock(AsyncExecutableResponse.class);
    doReturn(Status.SUCCEEDED).when(asyncExecutableResponse).getStatus();
    //    doReturn(asyncExecutableResponse).when(gitCloneStep).executeAsyncAfterRbac(any(), any(), any());

    doReturn(Arrays.asList("1")).when(asyncExecutableResponse).getCallbackIdsList();
    doReturn(Arrays.asList("2")).when(asyncExecutableResponse).getLogKeysList();

    ValuesManifestOutcome valuesManifestOutcome = mock(ValuesManifestOutcome.class);
    doReturn(valuesManifestOutcome)
        .when(downloadManifestsStepHelper)
        .getServerlessAwsLambdaValuesManifestOutcome(any());

    AsyncExecutableResponse asyncExecutableResponse1 = downloadServerlessManifestsV2Step.executeAsyncAfterRbac(
        ambiance, stepElementParameters, StepInputPackage.builder().build());
    assertThat(asyncExecutableResponse1.getCallbackIdsList().size()).isEqualTo(2);
    assertThat(asyncExecutableResponse1.getLogKeysList().size()).isEqualTo(2);
    assertThat(asyncExecutableResponse1.getStatus()).isEqualTo(Status.SUCCEEDED);
  }

  @SneakyThrows
  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Ignore
  @Category(UnitTests.class)
  public void executeAsyncAfterRbacWhenValuesYamlAbsentTest() {
    String accountId = "accountId";
    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions("accountId", accountId).build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().build();

    ManifestsOutcome manifestsOutcome = mock(ManifestsOutcome.class);
    doReturn(manifestsOutcome).when(downloadManifestsStepHelper).fetchManifestsOutcome(any());

    ServerlessAwsLambdaManifestOutcome serverlessAwsLambdaManifestOutcome =
        mock(ServerlessAwsLambdaManifestOutcome.class);
    doReturn(serverlessAwsLambdaManifestOutcome)
        .when(downloadManifestsStepHelper)
        .getServerlessAwsLambdaDirectoryManifestOutcome(any());

    GitCloneStepInfo gitCloneStepInfo = mock(GitCloneStepInfo.class);
    doReturn(gitCloneStepInfo).when(downloadManifestsStepHelper).getGitCloneStepInfoFromManifestOutcome(any());

    StepElementParameters stepElementParameters1 = mock(StepElementParameters.class);
    doReturn(stepElementParameters1).when(downloadManifestsStepHelper).getGitStepElementParameters(any(), any());

    String identifier = "identifier";
    doReturn(identifier).when(downloadManifestsStepHelper).getGitCloneStepIdentifier(any());

    Ambiance ambiance1 = mock(Ambiance.class);
    doReturn(ambiance1).when(downloadManifestsStepHelper).buildAmbianceForGitClone(any(), any());

    AsyncExecutableResponse asyncExecutableResponse = mock(AsyncExecutableResponse.class);
    doReturn(Status.SUCCEEDED).when(asyncExecutableResponse).getStatus();
    //    doReturn(asyncExecutableResponse).when(gitCloneStep).executeAsyncAfterRbac(any(), any(), any());

    doReturn(Arrays.asList("1")).when(asyncExecutableResponse).getCallbackIdsList();
    doReturn(Arrays.asList("2")).when(asyncExecutableResponse).getLogKeysList();

    ValuesManifestOutcome valuesManifestOutcome = mock(ValuesManifestOutcome.class);
    doReturn(null).when(downloadManifestsStepHelper).getServerlessAwsLambdaValuesManifestOutcome(any());

    AsyncExecutableResponse asyncExecutableResponse1 = downloadServerlessManifestsV2Step.executeAsyncAfterRbac(
        ambiance, stepElementParameters, StepInputPackage.builder().build());
    assertThat(asyncExecutableResponse1.getCallbackIdsList().size()).isEqualTo(1);
    assertThat(asyncExecutableResponse1.getLogKeysList().size()).isEqualTo(1);
    assertThat(asyncExecutableResponse1.getStatus()).isEqualTo(Status.SUCCEEDED);
  }

  @SneakyThrows
  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void handleAsyncResponseTestWhenValuesYamlPresent() {
    String accountId = "accountId";
    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions("accountId", accountId).build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().build();

    RefObject refObject = mock(RefObject.class);
    doReturn(refObject).when(downloadServerlessManifestsV2Step).getOutcomeRefObject();

    ManifestsOutcome manifestsOutcome = mock(ManifestsOutcome.class);
    OptionalOutcome optionalManifestsOutcome = OptionalOutcome.builder().found(true).outcome(manifestsOutcome).build();
    doReturn(optionalManifestsOutcome).when(outcomeService).resolveOptional(any(), any());

    ValuesManifestOutcome valuesManifestOutcome = mock(ValuesManifestOutcome.class);
    doReturn(valuesManifestOutcome)
        .when(downloadManifestsStepHelper)
        .getServerlessAwsLambdaValuesManifestOutcome(any());

    String valuesYamlPath = "path";
    doReturn(valuesYamlPath).when(downloadServerlessManifestsV2Step).getValuesPathFromValuesManifestOutcome(any());

    Map<String, ResponseData> responseDataMap = new HashMap<>();
    Map<String, String> resultMap = new HashMap<>();
    String valuesYamlBase64 = "content64";
    String valuesYamlContent = "content";
    resultMap.put(valuesYamlPath, valuesYamlBase64);

    when(serverlessStepCommonHelper.convertByte64ToString(valuesYamlBase64)).thenReturn(valuesYamlContent);

    doReturn(valuesYamlContent).when(engineExpressionService).renderExpression(any(), any());

    StepResponse stepOutcome =
        downloadServerlessManifestsV2Step.handleAsyncResponse(ambiance, stepElementParameters, responseDataMap);
    assertThat(stepOutcome.getStatus()).isEqualTo(Status.SUCCEEDED);
  }

  @SneakyThrows
  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void handleAsyncResponseTestWhenValuesYamlAbsent() {
    String accountId = "accountId";
    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions("accountId", accountId).build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().build();

    RefObject refObject = mock(RefObject.class);
    doReturn(refObject).when(downloadServerlessManifestsV2Step).getOutcomeRefObject();

    ManifestsOutcome manifestsOutcome = mock(ManifestsOutcome.class);
    OptionalOutcome optionalManifestsOutcome = OptionalOutcome.builder().found(true).outcome(manifestsOutcome).build();
    doReturn(optionalManifestsOutcome).when(outcomeService).resolveOptional(any(), any());

    ValuesManifestOutcome valuesManifestOutcome = mock(ValuesManifestOutcome.class);
    doReturn(null).when(downloadManifestsStepHelper).getServerlessAwsLambdaValuesManifestOutcome(any());

    String valuesYamlPath = "path";
    doReturn(valuesYamlPath).when(downloadServerlessManifestsV2Step).getValuesPathFromValuesManifestOutcome(any());

    Map<String, ResponseData> responseDataMap = new HashMap<>();
    Map<String, String> resultMap = new HashMap<>();
    String valuesYamlBase64 = "content64";
    String valuesYamlContent = "content";
    resultMap.put(valuesYamlPath, valuesYamlBase64);

    when(serverlessStepCommonHelper.convertByte64ToString(valuesYamlBase64)).thenReturn(valuesYamlContent);

    doReturn(valuesYamlContent).when(engineExpressionService).renderExpression(any(), any());

    StepResponse stepOutcome =
        downloadServerlessManifestsV2Step.handleAsyncResponse(ambiance, stepElementParameters, responseDataMap);
    assertThat(stepOutcome.getStatus()).isEqualTo(Status.SUCCEEDED);
  }
}