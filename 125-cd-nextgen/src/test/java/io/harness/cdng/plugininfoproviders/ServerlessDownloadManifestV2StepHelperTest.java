/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.plugininfoproviders;

import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.nodes.GitCloneStepNode;
import io.harness.beans.steps.stepinfo.GitCloneStepInfo;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.aws.sam.DownloadManifestsCommonHelper;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.manifest.steps.outcome.ManifestsOutcome;
import io.harness.cdng.manifest.yaml.ServerlessAwsLambdaManifestOutcome;
import io.harness.cdng.manifest.yaml.ValuesManifestOutcome;
import io.harness.cdng.pipeline.steps.CdAbstractStepNode;
import io.harness.cdng.serverless.ServerlessEntityHelper;
import io.harness.cdng.serverless.container.steps.ServerlessDownloadManifestsStepHelper;
import io.harness.connector.services.ConnectorService;
import io.harness.logstreaming.LogStreamingStepClientFactory;
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
import io.harness.pms.contracts.refobjects.RefObject;
import io.harness.pms.expression.EngineExpressionService;
import io.harness.pms.sdk.core.data.OptionalOutcome;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.tasks.ResponseData;

import com.google.inject.name.Named;
import com.google.protobuf.LazyStringArrayList;
import com.google.protobuf.ProtocolStringList;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
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
public class ServerlessDownloadManifestV2StepHelperTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private OutcomeService outcomeService;
  @Mock private KryoSerializer kryoSerializer;
  @Mock private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Mock private InstanceInfoService instanceInfoService;
  @Mock private CDFeatureFlagHelper cdFeatureFlagHelper;
  @Mock private CDStepHelper cdStepHelper;
  @Mock private ServerlessEntityHelper serverlessEntityHelper;
  @Mock ExecutionSweepingOutputService executionSweepingOutputService;

  @Mock GitCloneStep gitCloneStep;

  @Mock private EngineExpressionService engineExpressionService;
  @Mock DownloadManifestsCommonHelper downloadManifestsCommonHelper;

  @Mock private GitClonePluginInfoProvider gitClonePluginInfoProvider;
  @Named(DEFAULT_CONNECTOR_SERVICE) @Mock private ConnectorService connectorService;

  @Mock private ServerlessV2PluginInfoProviderHelper serverlessV2PluginInfoProviderHelper;
  @InjectMocks @Spy private ServerlessDownloadManifestsStepHelper serverlessDownloadManifestsV2StepHelper;

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testGetPluginInfoTestWhenValuesYamlAbsent() throws IOException {
    String accountId = "accountId";
    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions("accountId", accountId).build();

    String jsonNode = "jsonNdod";
    PluginCreationRequest pluginCreationRequest = PluginCreationRequest.newBuilder().setStepJsonNode(jsonNode).build();
    CdAbstractStepNode cdAbstractStepNode = mock(CdAbstractStepNode.class);
    doReturn("identifier").when(cdAbstractStepNode).getIdentifier();
    doReturn("name").when(cdAbstractStepNode).getName();
    doReturn("uuid").when(cdAbstractStepNode).getUuid();

    doReturn(cdAbstractStepNode).when(serverlessDownloadManifestsV2StepHelper).getCdAbstractStepNode(any());

    ManifestsOutcome manifestsOutcome = new ManifestsOutcome();
    OptionalOutcome optionalManifestsOutcome = OptionalOutcome.builder().found(true).outcome(manifestsOutcome).build();
    doReturn(optionalManifestsOutcome).when(outcomeService).resolveOptional(any(), any());

    ServerlessAwsLambdaManifestOutcome serverlessAwsLambdaManifestOutcome =
        mock(ServerlessAwsLambdaManifestOutcome.class);
    doReturn(serverlessAwsLambdaManifestOutcome)
        .when(serverlessV2PluginInfoProviderHelper)
        .getServerlessAwsLambdaDirectoryManifestOutcome(any());

    GitCloneStepInfo gitCloneStepInfo = mock(GitCloneStepInfo.class);
    doReturn(gitCloneStepInfo).when(downloadManifestsCommonHelper).getGitCloneStepInfoFromManifestOutcome(any());

    GitCloneStepNode gitCloneStepNode = mock(GitCloneStepNode.class);
    doReturn(gitCloneStepNode).when(downloadManifestsCommonHelper).getGitCloneStepNode(any(), any(), any());

    doReturn("node").when(serverlessDownloadManifestsV2StepHelper).getStepJsonNode(any());

    List<Integer> portList = new ArrayList<>(Arrays.asList(1));
    PluginDetails pluginDetails = mock(PluginDetails.class);
    doReturn(portList).when(pluginDetails).getPortUsedList();
    PluginCreationResponse pluginCreationResponse =
        PluginCreationResponse.newBuilder().setPluginDetails(pluginDetails).build();
    PluginCreationResponseWrapper pluginCreationResponseWrapper =
        PluginCreationResponseWrapper.newBuilder().setResponse(pluginCreationResponse).build();
    doReturn(pluginCreationResponseWrapper).when(gitClonePluginInfoProvider).getPluginInfo(any(), any(), any());

    doReturn(null).when(serverlessV2PluginInfoProviderHelper).getServerlessAwsLambdaValuesManifestOutcome(any());
    doReturn(manifestsOutcome).when(serverlessV2PluginInfoProviderHelper).fetchManifestsOutcome(any());

    PluginCreationResponseList pluginCreationResponseList = serverlessDownloadManifestsV2StepHelper.getPluginInfoList(
        pluginCreationRequest, new HashSet<Integer>(), ambiance);
    assertThat(pluginCreationResponseList.getResponseList().size()).isEqualTo(1);
    assertThat(
        pluginCreationResponseList.getResponseList().get(0).getResponse().getPluginDetails().getPortUsedList().size())
        .isEqualTo(1);
    assertThat(
        pluginCreationResponseList.getResponseList().get(0).getResponse().getPluginDetails().getPortUsedList().get(0))
        .isEqualTo(1);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testGetPluginInfoTestWhenValuesYamlPresent() throws IOException {
    String accountId = "accountId";
    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions("accountId", accountId).build();

    String jsonNode = "jsonNdod";
    PluginCreationRequest pluginCreationRequest = PluginCreationRequest.newBuilder().setStepJsonNode(jsonNode).build();
    CdAbstractStepNode cdAbstractStepNode = mock(CdAbstractStepNode.class);
    doReturn("identifier").when(cdAbstractStepNode).getIdentifier();
    doReturn("name").when(cdAbstractStepNode).getName();
    doReturn("uuid").when(cdAbstractStepNode).getUuid();

    doReturn(cdAbstractStepNode).when(serverlessDownloadManifestsV2StepHelper).getCdAbstractStepNode(any());

    ManifestsOutcome manifestsOutcome = new ManifestsOutcome();
    OptionalOutcome optionalManifestsOutcome = OptionalOutcome.builder().found(true).outcome(manifestsOutcome).build();
    doReturn(optionalManifestsOutcome).when(outcomeService).resolveOptional(any(), any());

    ServerlessAwsLambdaManifestOutcome serverlessAwsLambdaManifestOutcome =
        mock(ServerlessAwsLambdaManifestOutcome.class);
    doReturn(serverlessAwsLambdaManifestOutcome)
        .when(serverlessV2PluginInfoProviderHelper)
        .getServerlessAwsLambdaDirectoryManifestOutcome(any());

    GitCloneStepInfo gitCloneStepInfo = mock(GitCloneStepInfo.class);
    doReturn(gitCloneStepInfo).when(downloadManifestsCommonHelper).getGitCloneStepInfoFromManifestOutcome(any());
    doReturn(gitCloneStepInfo)
        .when(downloadManifestsCommonHelper)
        .getGitCloneStepInfoFromManifestOutcomeWithOutputFilePathContents(any(), any());

    GitCloneStepNode gitCloneStepNode = mock(GitCloneStepNode.class);
    doReturn(gitCloneStepNode).when(downloadManifestsCommonHelper).getGitCloneStepNode(any(), any(), any());

    doReturn("node").when(serverlessDownloadManifestsV2StepHelper).getStepJsonNode(any());

    List<Integer> portList = new ArrayList<>(Arrays.asList(1));
    PluginDetails pluginDetails = mock(PluginDetails.class);
    doReturn(portList).when(pluginDetails).getPortUsedList();
    PluginCreationResponse pluginCreationResponse =
        PluginCreationResponse.newBuilder().setPluginDetails(pluginDetails).build();
    PluginCreationResponseWrapper pluginCreationResponseWrapper =
        PluginCreationResponseWrapper.newBuilder().setResponse(pluginCreationResponse).build();
    doReturn(pluginCreationResponseWrapper).when(gitClonePluginInfoProvider).getPluginInfo(any(), any(), any());

    ValuesManifestOutcome valuesManifestOutcome = mock(ValuesManifestOutcome.class);
    doReturn("path").when(serverlessV2PluginInfoProviderHelper).getValuesPathFromValuesManifestOutcome(any());
    doReturn(valuesManifestOutcome)
        .when(serverlessV2PluginInfoProviderHelper)
        .getServerlessAwsLambdaValuesManifestOutcome(any());
    doReturn(manifestsOutcome).when(serverlessV2PluginInfoProviderHelper).fetchManifestsOutcome(any());

    PluginCreationResponseList pluginCreationResponseList = serverlessDownloadManifestsV2StepHelper.getPluginInfoList(
        pluginCreationRequest, new HashSet<Integer>(), ambiance);
    assertThat(pluginCreationResponseList.getResponseList().size()).isEqualTo(2);
    assertThat(
        pluginCreationResponseList.getResponseList().get(0).getResponse().getPluginDetails().getPortUsedList().size())
        .isEqualTo(1);
    assertThat(
        pluginCreationResponseList.getResponseList().get(1).getResponse().getPluginDetails().getPortUsedList().size())
        .isEqualTo(1);
  }

  @SneakyThrows
  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void executeAsyncAfterRbacWhenValuesYamlPresentTest() {
    String accountId = "accountId";
    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions("accountId", accountId).build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().build();

    ManifestsOutcome manifestsOutcome = mock(ManifestsOutcome.class);
    doReturn(manifestsOutcome).when(serverlessV2PluginInfoProviderHelper).fetchManifestsOutcome(any());

    ServerlessAwsLambdaManifestOutcome serverlessAwsLambdaManifestOutcome =
        mock(ServerlessAwsLambdaManifestOutcome.class);
    doReturn(serverlessAwsLambdaManifestOutcome)
        .when(serverlessV2PluginInfoProviderHelper)
        .getServerlessAwsLambdaDirectoryManifestOutcome(any());

    doReturn("path").when(serverlessV2PluginInfoProviderHelper).getValuesPathFromValuesManifestOutcome(any());

    GitCloneStepInfo gitCloneStepInfo = mock(GitCloneStepInfo.class);
    doReturn(gitCloneStepInfo).when(downloadManifestsCommonHelper).getGitCloneStepInfoFromManifestOutcome(any());

    doReturn(gitCloneStepInfo)
        .when(downloadManifestsCommonHelper)
        .getGitCloneStepInfoFromManifestOutcomeWithOutputFilePathContents(any(), any());

    StepElementParameters stepElementParameters1 = mock(StepElementParameters.class);
    doReturn(stepElementParameters1).when(downloadManifestsCommonHelper).getGitStepElementParameters(any(), any());

    String identifier = "identifier";
    doReturn(identifier).when(downloadManifestsCommonHelper).getGitCloneStepIdentifier(any());

    Ambiance ambiance1 = mock(Ambiance.class);
    doReturn(ambiance1).when(downloadManifestsCommonHelper).buildAmbianceForGitClone(any(), any());

    AsyncExecutableResponse asyncExecutableResponse = mock(AsyncExecutableResponse.class);
    doReturn(Status.SUCCEEDED).when(asyncExecutableResponse).getStatus();
    doReturn(asyncExecutableResponse).when(gitCloneStep).executeAsyncAfterRbac(any(), any(), any());

    ProtocolStringList callbackIdList = new LazyStringArrayList();
    callbackIdList.add("1");

    ProtocolStringList logKeysList = new LazyStringArrayList();
    logKeysList.add("2");

    doReturn(callbackIdList).when(asyncExecutableResponse).getCallbackIdsList();
    doReturn(logKeysList).when(asyncExecutableResponse).getLogKeysList();

    ValuesManifestOutcome valuesManifestOutcome = mock(ValuesManifestOutcome.class);
    doReturn(valuesManifestOutcome)
        .when(serverlessV2PluginInfoProviderHelper)
        .getServerlessAwsLambdaValuesManifestOutcome(any());

    AsyncExecutableResponse asyncExecutableResponse1 = serverlessDownloadManifestsV2StepHelper.executeAsyncAfterRbac(
        ambiance, StepInputPackage.builder().build(), gitCloneStep);
    assertThat(asyncExecutableResponse1.getCallbackIdsList().size()).isEqualTo(2);
    assertThat(asyncExecutableResponse1.getLogKeysList().size()).isEqualTo(2);
    assertThat(asyncExecutableResponse1.getStatus()).isEqualTo(Status.SUCCEEDED);
  }

  @SneakyThrows
  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void executeAsyncAfterRbacWhenValuesYamlAbsentTest() {
    String accountId = "accountId";
    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions("accountId", accountId).build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().build();

    ManifestsOutcome manifestsOutcome = mock(ManifestsOutcome.class);
    doReturn(manifestsOutcome).when(serverlessV2PluginInfoProviderHelper).fetchManifestsOutcome(any());

    ServerlessAwsLambdaManifestOutcome serverlessAwsLambdaManifestOutcome =
        mock(ServerlessAwsLambdaManifestOutcome.class);
    doReturn(serverlessAwsLambdaManifestOutcome)
        .when(serverlessV2PluginInfoProviderHelper)
        .getServerlessAwsLambdaDirectoryManifestOutcome(any());

    GitCloneStepInfo gitCloneStepInfo = mock(GitCloneStepInfo.class);
    doReturn(gitCloneStepInfo).when(downloadManifestsCommonHelper).getGitCloneStepInfoFromManifestOutcome(any());

    StepElementParameters stepElementParameters1 = mock(StepElementParameters.class);
    doReturn(stepElementParameters1).when(downloadManifestsCommonHelper).getGitStepElementParameters(any(), any());

    String identifier = "identifier";
    doReturn(identifier).when(downloadManifestsCommonHelper).getGitCloneStepIdentifier(any());

    Ambiance ambiance1 = mock(Ambiance.class);
    doReturn(ambiance1).when(downloadManifestsCommonHelper).buildAmbianceForGitClone(any(), any());

    AsyncExecutableResponse asyncExecutableResponse = mock(AsyncExecutableResponse.class);
    doReturn(Status.SUCCEEDED).when(asyncExecutableResponse).getStatus();
    doReturn(asyncExecutableResponse).when(gitCloneStep).executeAsyncAfterRbac(any(), any(), any());

    ProtocolStringList callbackIdList = new LazyStringArrayList();
    callbackIdList.add("1");

    ProtocolStringList logKeysList = new LazyStringArrayList();
    logKeysList.add("2");

    doReturn(callbackIdList).when(asyncExecutableResponse).getCallbackIdsList();
    doReturn(logKeysList).when(asyncExecutableResponse).getLogKeysList();

    ValuesManifestOutcome valuesManifestOutcome = mock(ValuesManifestOutcome.class);
    doReturn(null).when(serverlessV2PluginInfoProviderHelper).getServerlessAwsLambdaValuesManifestOutcome(any());

    AsyncExecutableResponse asyncExecutableResponse1 = serverlessDownloadManifestsV2StepHelper.executeAsyncAfterRbac(
        ambiance, StepInputPackage.builder().build(), gitCloneStep);
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
    doReturn(refObject).when(serverlessDownloadManifestsV2StepHelper).getOutcomeRefObject();

    ManifestsOutcome manifestsOutcome = mock(ManifestsOutcome.class);
    OptionalOutcome optionalManifestsOutcome = OptionalOutcome.builder().found(true).outcome(manifestsOutcome).build();
    doReturn(optionalManifestsOutcome).when(outcomeService).resolveOptional(any(), any());
    doReturn(manifestsOutcome).when(serverlessV2PluginInfoProviderHelper).fetchManifestsOutcome(any());

    ValuesManifestOutcome valuesManifestOutcome = mock(ValuesManifestOutcome.class);
    doReturn(valuesManifestOutcome)
        .when(serverlessV2PluginInfoProviderHelper)
        .getServerlessAwsLambdaValuesManifestOutcome(any());

    String valuesYamlPath = "path";
    doReturn(valuesYamlPath).when(serverlessV2PluginInfoProviderHelper).getValuesPathFromValuesManifestOutcome(any());

    Map<String, ResponseData> responseDataMap = new HashMap<>();
    Map<String, String> resultMap = new HashMap<>();
    String valuesYamlBase64 = "content64";
    String valuesYamlContent = "content";
    resultMap.put(valuesYamlPath, valuesYamlBase64);

    doReturn(valuesYamlContent).when(engineExpressionService).renderExpression(any(), any());

    StepResponse stepOutcome = serverlessDownloadManifestsV2StepHelper.handleAsyncResponse(ambiance, responseDataMap);
    assertThat(stepOutcome.getStatus()).isEqualTo(Status.SUCCEEDED);
  }

  @SneakyThrows
  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void handleAsyncResponseTestWhenValuesYamlAbsent() {
    String accountId = "accoun  tId";
    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions("accountId", accountId).build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().build();

    RefObject refObject = mock(RefObject.class);
    doReturn(refObject).when(serverlessDownloadManifestsV2StepHelper).getOutcomeRefObject();

    ManifestsOutcome manifestsOutcome = mock(ManifestsOutcome.class);
    OptionalOutcome optionalManifestsOutcome = OptionalOutcome.builder().found(true).outcome(manifestsOutcome).build();
    doReturn(optionalManifestsOutcome).when(outcomeService).resolveOptional(any(), any());
    doReturn(manifestsOutcome).when(serverlessV2PluginInfoProviderHelper).fetchManifestsOutcome(any());

    ValuesManifestOutcome valuesManifestOutcome = mock(ValuesManifestOutcome.class);
    doReturn(null).when(serverlessV2PluginInfoProviderHelper).getServerlessAwsLambdaValuesManifestOutcome(any());

    String valuesYamlPath = "path";
    doReturn(valuesYamlPath).when(serverlessV2PluginInfoProviderHelper).getValuesPathFromValuesManifestOutcome(any());

    Map<String, ResponseData> responseDataMap = new HashMap<>();
    Map<String, String> resultMap = new HashMap<>();
    String valuesYamlBase64 = "content64";
    String valuesYamlContent = "content";
    resultMap.put(valuesYamlPath, valuesYamlBase64);

    doReturn(valuesYamlContent).when(engineExpressionService).renderExpression(any(), any());

    StepResponse stepOutcome = serverlessDownloadManifestsV2StepHelper.handleAsyncResponse(ambiance, responseDataMap);
    assertThat(stepOutcome.getStatus()).isEqualTo(Status.SUCCEEDED);
  }
}
