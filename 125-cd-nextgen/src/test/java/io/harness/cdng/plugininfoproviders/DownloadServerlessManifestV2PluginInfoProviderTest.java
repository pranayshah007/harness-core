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
import io.harness.beans.IdentifierRef;
import io.harness.beans.steps.nodes.GitCloneStepNode;
import io.harness.beans.steps.stepinfo.GitCloneStepInfo;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.infra.beans.ServerlessAwsLambdaInfrastructureOutcome;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.manifest.steps.outcome.ManifestsOutcome;
import io.harness.cdng.manifest.yaml.GithubStore;
import io.harness.cdng.manifest.yaml.ServerlessAwsLambdaManifestOutcome;
import io.harness.cdng.manifest.yaml.ValuesManifestOutcome;
import io.harness.cdng.pipeline.steps.CdAbstractStepNode;
import io.harness.cdng.serverless.ServerlessEntityHelper;
import io.harness.cdng.serverless.container.steps.DownloadManifestsStepHelper;
import io.harness.cdng.serverless.container.steps.DownloadServerlessManifestsV2StepInfo;
import io.harness.cdng.serverless.container.steps.ServerlessAwsLambdaDeployV2StepInfo;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.task.serverless.ServerlessAwsLambdaInfraConfig;
import io.harness.encryption.SecretRefData;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.ng.core.NGAccess;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ImageDetails;
import io.harness.pms.contracts.plan.PluginCreationRequest;
import io.harness.pms.contracts.plan.PluginCreationResponse;
import io.harness.pms.contracts.plan.PluginCreationResponseList;
import io.harness.pms.contracts.plan.PluginCreationResponseWrapper;
import io.harness.pms.contracts.plan.PluginDetails;
import io.harness.pms.contracts.plan.PortDetails;
import io.harness.pms.sdk.core.data.OptionalOutcome;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.yaml.extended.ci.container.ContainerResource;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
public class DownloadServerlessManifestV2PluginInfoProviderTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private OutcomeService outcomeService;
  @Mock private KryoSerializer kryoSerializer;
  @Mock private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Mock private InstanceInfoService instanceInfoService;
  @Mock private CDFeatureFlagHelper cdFeatureFlagHelper;
  @Mock private CDStepHelper cdStepHelper;
  @Mock private ServerlessEntityHelper serverlessEntityHelper;
  @Mock ExecutionSweepingOutputService executionSweepingOutputService;

  @Mock private GitClonePluginInfoProvider gitClonePluginInfoProvider;
  @Named(DEFAULT_CONNECTOR_SERVICE) @Mock private ConnectorService connectorService;

  @Mock private ServerlessV2PluginInfoProviderHelper serverlessV2PluginInfoProviderHelper;

  @Mock private DownloadManifestsStepHelper downloadManifestsStepHelper;
  @InjectMocks @Spy private DownloadServerlessManifestsV2PluginInfoProvider downloadManifestsPluginInfoProvider;

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

    doReturn(cdAbstractStepNode).when(downloadManifestsPluginInfoProvider).getCdAbstractStepNode(any());

    ManifestsOutcome manifestsOutcome = new ManifestsOutcome();
    OptionalOutcome optionalManifestsOutcome = OptionalOutcome.builder().found(true).outcome(manifestsOutcome).build();
    doReturn(optionalManifestsOutcome).when(outcomeService).resolveOptional(any(), any());

    ServerlessAwsLambdaManifestOutcome serverlessAwsLambdaManifestOutcome =
        mock(ServerlessAwsLambdaManifestOutcome.class);
    doReturn(serverlessAwsLambdaManifestOutcome)
        .when(serverlessV2PluginInfoProviderHelper)
        .getServerlessAwsLambdaDirectoryManifestOutcome(any());

    GitCloneStepInfo gitCloneStepInfo = mock(GitCloneStepInfo.class);
    doReturn(gitCloneStepInfo).when(downloadManifestsStepHelper).getGitCloneStepInfoFromManifestOutcome(any());

    GitCloneStepNode gitCloneStepNode = mock(GitCloneStepNode.class);
    doReturn(gitCloneStepNode).when(downloadManifestsStepHelper).getGitCloneStepNode(any(), any(), any());

    doReturn("node").when(downloadManifestsPluginInfoProvider).getStepJsonNode(any());

    List<Integer> portList = new ArrayList<Integer>(Arrays.asList(1));
    PluginDetails pluginDetails = mock(PluginDetails.class);
    doReturn(portList).when(pluginDetails).getPortUsedList();
    PluginCreationResponse pluginCreationResponse =
        PluginCreationResponse.newBuilder().setPluginDetails(pluginDetails).build();
    PluginCreationResponseWrapper pluginCreationResponseWrapper =
        PluginCreationResponseWrapper.newBuilder().setResponse(pluginCreationResponse).build();
    doReturn(pluginCreationResponseWrapper).when(gitClonePluginInfoProvider).getPluginInfo(any(), any(), any());

    doReturn(null).when(serverlessV2PluginInfoProviderHelper).getServerlessAwsLambdaValuesManifestOutcome(any());

    PluginCreationResponseList pluginCreationResponseList =
        downloadManifestsPluginInfoProvider.getPluginInfoList(pluginCreationRequest, new HashSet<Integer>(), ambiance);
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

    doReturn(cdAbstractStepNode).when(downloadManifestsPluginInfoProvider).getCdAbstractStepNode(any());

    ManifestsOutcome manifestsOutcome = new ManifestsOutcome();
    OptionalOutcome optionalManifestsOutcome = OptionalOutcome.builder().found(true).outcome(manifestsOutcome).build();
    doReturn(optionalManifestsOutcome).when(outcomeService).resolveOptional(any(), any());

    ServerlessAwsLambdaManifestOutcome serverlessAwsLambdaManifestOutcome =
        mock(ServerlessAwsLambdaManifestOutcome.class);
    doReturn(serverlessAwsLambdaManifestOutcome)
        .when(serverlessV2PluginInfoProviderHelper)
        .getServerlessAwsLambdaDirectoryManifestOutcome(any());

    GitCloneStepInfo gitCloneStepInfo = mock(GitCloneStepInfo.class);
    doReturn(gitCloneStepInfo).when(downloadManifestsStepHelper).getGitCloneStepInfoFromManifestOutcome(any());

    GitCloneStepNode gitCloneStepNode = mock(GitCloneStepNode.class);
    doReturn(gitCloneStepNode).when(downloadManifestsStepHelper).getGitCloneStepNode(any(), any(), any());

    doReturn("node").when(downloadManifestsPluginInfoProvider).getStepJsonNode(any());

    List<Integer> portList = new ArrayList<Integer>(Arrays.asList(1));
    PluginDetails pluginDetails = mock(PluginDetails.class);
    doReturn(portList).when(pluginDetails).getPortUsedList();
    PluginCreationResponse pluginCreationResponse =
        PluginCreationResponse.newBuilder().setPluginDetails(pluginDetails).build();
    PluginCreationResponseWrapper pluginCreationResponseWrapper =
        PluginCreationResponseWrapper.newBuilder().setResponse(pluginCreationResponse).build();
    doReturn(pluginCreationResponseWrapper).when(gitClonePluginInfoProvider).getPluginInfo(any(), any(), any());

    ValuesManifestOutcome valuesManifestOutcome = mock(ValuesManifestOutcome.class);
    doReturn(valuesManifestOutcome)
        .when(serverlessV2PluginInfoProviderHelper)
        .getServerlessAwsLambdaValuesManifestOutcome(any());

    PluginCreationResponseList pluginCreationResponseList =
        downloadManifestsPluginInfoProvider.getPluginInfoList(pluginCreationRequest, new HashSet<Integer>(), ambiance);
    assertThat(pluginCreationResponseList.getResponseList().size()).isEqualTo(2);
    assertThat(
        pluginCreationResponseList.getResponseList().get(0).getResponse().getPluginDetails().getPortUsedList().size())
        .isEqualTo(1);
    assertThat(
        pluginCreationResponseList.getResponseList().get(1).getResponse().getPluginDetails().getPortUsedList().size())
        .isEqualTo(1);
  }
}
