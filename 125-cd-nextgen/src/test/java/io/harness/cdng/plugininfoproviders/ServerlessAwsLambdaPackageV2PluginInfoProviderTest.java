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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.yaml.extended.ImagePullPolicy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.pipeline.steps.CdAbstractStepNode;
import io.harness.cdng.serverless.ServerlessEntityHelper;
import io.harness.cdng.serverless.container.steps.ServerlessAwsLambdaDeployV2StepInfo;
import io.harness.cdng.serverless.container.steps.ServerlessAwsLambdaPackageV2StepInfo;
import io.harness.cdng.serverless.container.steps.ServerlessAwsLambdaV2BaseStepInfo;
import io.harness.connector.services.ConnectorService;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ImageDetails;
import io.harness.pms.contracts.plan.PluginCreationRequest;
import io.harness.pms.contracts.plan.PluginCreationResponse;
import io.harness.pms.contracts.plan.PluginCreationResponseWrapper;
import io.harness.pms.contracts.plan.PluginDetails;
import io.harness.pms.contracts.plan.PluginDetails.Builder;
import io.harness.pms.contracts.plan.StepInfoProto;
import io.harness.pms.sdk.core.plugin.ContainerPluginParseException;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.yaml.extended.ci.container.ContainerResource;

import com.google.inject.name.Named;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
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
public class ServerlessAwsLambdaPackageV2PluginInfoProviderTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private OutcomeService outcomeService;
  @Mock private KryoSerializer kryoSerializer;
  @Mock private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Mock private InstanceInfoService instanceInfoService;
  @Mock private CDFeatureFlagHelper cdFeatureFlagHelper;
  @Mock private CDStepHelper cdStepHelper;
  @Mock private ServerlessEntityHelper serverlessEntityHelper;

  @Mock private PluginInfoProviderUtils pluginInfoProviderUtils;
  @Mock ExecutionSweepingOutputService executionSweepingOutputService;

  @Mock ServerlessV2PluginInfoProviderHelper serverlessV2PluginInfoProviderHelper;
  @Named(DEFAULT_CONNECTOR_SERVICE) @Mock private ConnectorService connectorService;
  @InjectMocks
  @Spy
  private ServerlessAwsLambdaPackageV2PluginInfoProvider serverlessAwsLambdaPackageV2PluginInfoProvider;

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testGetPluginInfo() throws IOException {
    String accountId = "accountId";
    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions("accountId", accountId).build();

    String jsonNode = "jsonNdod";
    PluginCreationRequest pluginCreationRequest = PluginCreationRequest.newBuilder().setStepJsonNode(jsonNode).build();
    CdAbstractStepNode cdAbstractStepNode = mock(CdAbstractStepNode.class);
    doReturn("identifier").when(cdAbstractStepNode).getIdentifier();
    doReturn("name").when(cdAbstractStepNode).getName();
    doReturn("uuid").when(cdAbstractStepNode).getUuid();

    ServerlessAwsLambdaPackageV2StepInfo serverlessAwsLambdaPackageV2StepInfo =
        ServerlessAwsLambdaPackageV2StepInfo.infoBuilder()
            .resources(ContainerResource.builder().build())
            .runAsUser(ParameterField.<Integer>builder().value(1).build())
            .connectorRef(ParameterField.<String>builder().value("connectorRef").build())
            .build();
    doReturn(serverlessAwsLambdaPackageV2StepInfo).when(cdAbstractStepNode).getStepSpecType();
    doReturn(Collections.emptyMap()).when(serverlessV2PluginInfoProviderHelper).getEnvironmentVariables(any(), any());
    PluginCreationResponseWrapper pluginCreationResponseWrapper = mock(PluginCreationResponseWrapper.class);
    doReturn(pluginCreationResponseWrapper)
        .when(serverlessAwsLambdaPackageV2PluginInfoProvider)
        .getPluginCreationResponseWrapper(any(), any());

    doReturn(mock(ImageDetails.class)).when(serverlessAwsLambdaPackageV2PluginInfoProvider).getImageDetails(any());

    PluginDetails.Builder pluginBuilder = PluginDetails.newBuilder();
    doReturn(pluginBuilder)
        .when(serverlessAwsLambdaPackageV2PluginInfoProvider)
        .getPluginDetailsBuilder(any(), any(), any());
    doReturn(cdAbstractStepNode).when(serverlessAwsLambdaPackageV2PluginInfoProvider).getRead(jsonNode);

    assertThat(serverlessAwsLambdaPackageV2PluginInfoProvider.getPluginInfo(
                   pluginCreationRequest, Collections.emptySet(), ambiance))
        .isEqualTo(pluginCreationResponseWrapper);
  }

  @Test(expected = ContainerPluginParseException.class)
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testGetPluginInfoWhenException() throws IOException {
    String accountId = "accountId";
    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions("accountId", accountId).build();

    String jsonNode = "jsonNdod";
    PluginCreationRequest pluginCreationRequest = PluginCreationRequest.newBuilder().setStepJsonNode(jsonNode).build();
    CdAbstractStepNode cdAbstractStepNode = mock(CdAbstractStepNode.class);
    doReturn("identifier").when(cdAbstractStepNode).getIdentifier();
    doReturn("name").when(cdAbstractStepNode).getName();
    doReturn("uuid").when(cdAbstractStepNode).getUuid();

    ServerlessAwsLambdaPackageV2StepInfo serverlessAwsLambdaPackageV2StepInfo =
        ServerlessAwsLambdaPackageV2StepInfo.infoBuilder()
            .resources(ContainerResource.builder().build())
            .runAsUser(ParameterField.<Integer>builder().value(1).build())
            .connectorRef(ParameterField.<String>builder().value("connectorRef").build())
            .build();
    doReturn(serverlessAwsLambdaPackageV2StepInfo).when(cdAbstractStepNode).getStepSpecType();
    doReturn(Collections.emptyMap()).when(serverlessV2PluginInfoProviderHelper).getEnvironmentVariables(any(), any());
    PluginCreationResponseWrapper pluginCreationResponseWrapper = mock(PluginCreationResponseWrapper.class);
    doReturn(pluginCreationResponseWrapper)
        .when(serverlessAwsLambdaPackageV2PluginInfoProvider)
        .getPluginCreationResponseWrapper(any(), any());

    doReturn(mock(ImageDetails.class)).when(serverlessAwsLambdaPackageV2PluginInfoProvider).getImageDetails(any());

    PluginDetails.Builder pluginBuilder = PluginDetails.newBuilder();
    doReturn(pluginBuilder)
        .when(serverlessAwsLambdaPackageV2PluginInfoProvider)
        .getPluginDetailsBuilder(any(), any(), any());
    doThrow(IOException.class).when(serverlessAwsLambdaPackageV2PluginInfoProvider).getRead(jsonNode);

    serverlessAwsLambdaPackageV2PluginInfoProvider.getPluginInfo(
        pluginCreationRequest, Collections.emptySet(), ambiance);
  }

  @Test()
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testGetPluginCreationResponseWrapper() {
    PluginCreationResponse pluginCreationResponse = mock(PluginCreationResponse.class);
    StepInfoProto stepInfoProto = mock(StepInfoProto.class);
    PluginCreationResponseWrapper pluginCreationResponseWrapper =
        serverlessAwsLambdaPackageV2PluginInfoProvider.getPluginCreationResponseWrapper(
            pluginCreationResponse, stepInfoProto);
    assertThat(pluginCreationResponseWrapper.getResponse()).isEqualTo(pluginCreationResponse);
    assertThat(pluginCreationResponseWrapper.getStepInfo()).isEqualTo(stepInfoProto);
  }

  @Test()
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testGetPluginCreationResponse() {
    Builder builder = mock(Builder.class);
    PluginDetails pluginDetails = mock(PluginDetails.class);
    doReturn(pluginDetails).when(builder).build();
    PluginCreationResponse pluginCreationResponse =
        serverlessAwsLambdaPackageV2PluginInfoProvider.getPluginCreationResponse(builder);
    assertThat(pluginCreationResponse.getPluginDetails()).isEqualTo(pluginDetails);
  }

  @Test()
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testGetImageDetails() {
    ServerlessAwsLambdaV2BaseStepInfo serverlessAwsLambdaV2BaseStepInfo =
        ServerlessAwsLambdaDeployV2StepInfo.infoBuilder()
            .connectorRef(ParameterField.createValueField("ref"))
            .image(ParameterField.createValueField("ref"))
            .imagePullPolicy(ParameterField.createValueField(ImagePullPolicy.ALWAYS))
            .build();
    Mockito.mockStatic(PluginInfoProviderHelper.class);
    ImageDetails imageDetails = mock(ImageDetails.class);
    when(PluginInfoProviderHelper.getImageDetails(any(), any(), any())).thenReturn(imageDetails);
    assertThat(serverlessAwsLambdaPackageV2PluginInfoProvider.getImageDetails(serverlessAwsLambdaV2BaseStepInfo))
        .isEqualTo(imageDetails);
  }

  @Test()
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testGetPluginDetailsBuilder() {
    ContainerResource containerResource = mock(ContainerResource.class);
    Mockito.mockStatic(PluginInfoProviderHelper.class);
    ImageDetails imageDetails = mock(ImageDetails.class);
    Builder builder = mock(Builder.class);
    when(PluginInfoProviderHelper.buildPluginDetails(any(), any(), any(), anyBoolean())).thenReturn(builder);
    Set<Integer> usedPorts = new HashSet<>();
    usedPorts.add(1);
    assertThat(serverlessAwsLambdaPackageV2PluginInfoProvider.getPluginDetailsBuilder(
                   containerResource, ParameterField.createValueField(1), usedPorts))
        .isEqualTo(builder);
  }

  @Test()
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testIsSupportedWhenTrue() {
    assertThat(serverlessAwsLambdaPackageV2PluginInfoProvider.isSupported(
                   StepSpecTypeConstants.SERVERLESS_AWS_LAMBDA_PACKAGE_V2))
        .isTrue();
  }

  @Test()
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testIsSupportedWhenFalse() {
    assertThat(serverlessAwsLambdaPackageV2PluginInfoProvider.isSupported("asdf")).isFalse();
  }
}
