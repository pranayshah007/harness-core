/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.plugininfoproviders;

import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.rule.OwnerRule.IVAN;
import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.aws.sam.AwsSamDeployStepInfo;
import io.harness.cdng.aws.sam.AwsSamStepHelper;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.infra.beans.AwsSamInfrastructureOutcome;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.manifest.steps.outcome.ManifestsOutcome;
import io.harness.cdng.manifest.yaml.AwsSamDirectoryManifestOutcome;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.pipeline.steps.CdAbstractStepNode;
import io.harness.cdng.serverless.ServerlessEntityHelper;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidArgumentsException;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.ng.core.NGAccess;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ImageDetails;
import io.harness.pms.contracts.plan.PluginCreationRequest;
import io.harness.pms.contracts.plan.PluginCreationResponseWrapper;
import io.harness.pms.contracts.plan.PluginDetails;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalOutcome;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.utils.IdentifierRefHelper;
import io.harness.yaml.extended.ci.container.ContainerResource;

import com.google.inject.name.Named;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
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
public class AwsSamDeployPluginInfoProviderTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private OutcomeService outcomeService;
  @Mock private KryoSerializer kryoSerializer;
  @Mock private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Mock private InstanceInfoService instanceInfoService;
  @Mock private CDFeatureFlagHelper cdFeatureFlagHelper;
  @Mock private CDStepHelper cdStepHelper;
  @Mock private ServerlessEntityHelper serverlessEntityHelper;
  @Mock ExecutionSweepingOutputService executionSweepingOutputService;

  @Mock CDExpressionResolver cdExpressionResolver;

  @Mock AwsSamStepHelper awsSamStepHelper;

  @Mock PluginInfoProviderUtils pluginInfoProviderUtils;
  @Named(DEFAULT_CONNECTOR_SERVICE) @Mock private ConnectorService connectorService;
  @InjectMocks @Spy private AwsSamDeployPluginInfoProvider awsSamDeployPluginInfoProvider;

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testGetPluginInfo() throws IOException {
    String accountId = "accountId";
    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions("accountId", accountId).build();
    PluginCreationRequest pluginCreationRequest = getPluginCreationRequest();

    String samDir = "samDir";
    doReturn(samDir).when(awsSamStepHelper).getSamDirectoryPathFromAwsSamDirectoryManifestOutcome(any());

    PluginCreationResponseWrapper pluginCreationResponseWrapper =
        awsSamDeployPluginInfoProvider.getPluginInfo(pluginCreationRequest, Collections.emptySet(), ambiance);

    assertThat(pluginCreationResponseWrapper.getStepInfo().getIdentifier()).isEqualTo("identifier");
    assertThat(pluginCreationResponseWrapper.getStepInfo().getName()).isEqualTo("name");
    assertThat(pluginCreationResponseWrapper.getStepInfo().getUuid()).isEqualTo("uuid");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetPluginInfoWithSamDirNull() throws IOException {
    String accountId = "accountId";
    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions("accountId", accountId).build();
    PluginCreationRequest pluginCreationRequest = getPluginCreationRequest();

    doReturn(null).when(awsSamStepHelper).getSamDirectoryPathFromAwsSamDirectoryManifestOutcome(any());

    assertThatThrownBy(
        () -> awsSamDeployPluginInfoProvider.getPluginInfo(pluginCreationRequest, Collections.emptySet(), ambiance))
        .hasMessage("Not found value for environment variable: PLUGIN_SAM_DIR")
        .isInstanceOf(InvalidArgumentsException.class);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testValidateEnvVariables() {
    Map<String, String> environmentVariables =
        Map.of("PLUGIN_STACK_NAME", "plugin_stack_name", "PLUGIN_SAM_DIR", "sam/manifest/dir");
    Map<String, String> validatedEnvironmentVariables =
        awsSamDeployPluginInfoProvider.validateEnvVariables(environmentVariables);

    assertThat(validatedEnvironmentVariables).isEqualTo(environmentVariables);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testValidateEmptyEnvVariables() {
    Map<String, String> environmentVariables = Collections.emptyMap();
    Map<String, String> validatedEnvironmentVariables =
        awsSamDeployPluginInfoProvider.validateEnvVariables(environmentVariables);

    assertThat(validatedEnvironmentVariables).isEqualTo(environmentVariables);

    validatedEnvironmentVariables = awsSamDeployPluginInfoProvider.validateEnvVariables(null);

    assertThat(validatedEnvironmentVariables).isNull();
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testValidateEnvVariablesWithExceptionOneVariable() {
    Map<String, String> environmentVariables = new HashMap<>();
    environmentVariables.put("PLUGIN_STACK_NAME", null);
    environmentVariables.put("PLUGIN_SAM_DIR", "sam/manifest/dir");

    assertThatThrownBy(() -> awsSamDeployPluginInfoProvider.validateEnvVariables(environmentVariables))
        .hasMessage("Not found value for environment variable: PLUGIN_STACK_NAME")
        .isInstanceOf(InvalidArgumentsException.class);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testValidateEnvVariablesWithExceptionMoreVariables() {
    Map<String, String> environmentVariables = new HashMap<>();
    environmentVariables.put("PLUGIN_STACK_NAME", null);
    environmentVariables.put("PLUGIN_SAM_DIR", null);

    assertThatThrownBy(() -> awsSamDeployPluginInfoProvider.validateEnvVariables(environmentVariables))
        .hasMessage("Not found value for environment variables: PLUGIN_SAM_DIR,PLUGIN_STACK_NAME")
        .isInstanceOf(InvalidArgumentsException.class);
  }

  private PluginCreationRequest getPluginCreationRequest() throws IOException {
    String jsonNode = "jsonNdod";
    PluginCreationRequest pluginCreationRequest = PluginCreationRequest.newBuilder().setStepJsonNode(jsonNode).build();
    CdAbstractStepNode cdAbstractStepNode = mock(CdAbstractStepNode.class);
    Mockito.mockStatic(YamlUtils.class);
    when(YamlUtils.read(anyString(), (Class<Object>) any())).thenReturn(cdAbstractStepNode);
    doReturn("identifier").when(cdAbstractStepNode).getIdentifier();
    doReturn("name").when(cdAbstractStepNode).getName();
    doReturn("uuid").when(cdAbstractStepNode).getUuid();
    String connectorRef = "ref";
    String stackName = "stackName";

    AwsSamDeployStepInfo awsSamDeployStepInfo = AwsSamDeployStepInfo.infoBuilder()
                                                    .resources(ContainerResource.builder().build())
                                                    .runAsUser(ParameterField.<Integer>builder().value(1).build())
                                                    .connectorRef(ParameterField.createValueField("ref"))
                                                    .stackName(ParameterField.createValueField(stackName))
                                                    .build();
    doReturn(awsSamDeployStepInfo).when(cdAbstractStepNode).getStepSpecType();

    Mockito.mockStatic(AmbianceUtils.class);
    NGAccess ngAccess = mock(NGAccess.class);
    when(AmbianceUtils.getNgAccess(any())).thenReturn(ngAccess);

    Mockito.mockStatic(IdentifierRefHelper.class);
    IdentifierRef identifierRef = mock(IdentifierRef.class);
    when(ngAccess.getAccountIdentifier()).thenReturn("account");
    when(ngAccess.getOrgIdentifier()).thenReturn("account");
    when(ngAccess.getProjectIdentifier()).thenReturn("account");
    when(IdentifierRefHelper.getIdentifierRef(any(), any(), any(), any())).thenReturn(identifierRef);

    when(identifierRef.getAccountIdentifier()).thenReturn("account");
    when(identifierRef.getOrgIdentifier()).thenReturn("account");
    when(identifierRef.getProjectIdentifier()).thenReturn("account");
    when(identifierRef.getIdentifier()).thenReturn("account");

    PluginDetails.Builder pluginDetailsBuilder = PluginDetails.newBuilder();
    ImageDetails imageDetails = mock(ImageDetails.class);
    Mockito.mockStatic(PluginInfoProviderHelper.class);
    when(PluginInfoProviderHelper.buildPluginDetails(any(), any(), any(), anyBoolean()))
        .thenReturn(pluginDetailsBuilder);
    when(PluginInfoProviderHelper.getImageDetails(any(), any(), any())).thenReturn(imageDetails);

    ConnectorConfigDTO connectorConfigDTO =
        AwsConnectorDTO.builder()
            .credential(
                AwsCredentialDTO.builder()
                    .awsCredentialType(AwsCredentialType.MANUAL_CREDENTIALS)
                    .config(AwsManualConfigSpecDTO.builder()
                                .accessKey("accessKey")
                                .secretKeyRef(SecretRefData.builder().decryptedValue(new char[] {'a', 'b'}).build())
                                .build())
                    .build())
            .build();
    ConnectorInfoDTO connectorInfoDTO = mock(ConnectorInfoDTO.class);
    ConnectorResponseDTO connectorResponseDTO = mock(ConnectorResponseDTO.class);
    when(connectorService.get(any(), any(), any(), any())).thenReturn(Optional.of(connectorResponseDTO));
    when(connectorResponseDTO.getConnector()).thenReturn(connectorInfoDTO);
    when(connectorInfoDTO.getConnectorConfig()).thenReturn(connectorConfigDTO);

    AwsSamInfrastructureOutcome awsSamInfrastructureOutcome =
        AwsSamInfrastructureOutcome.builder().connectorRef(connectorRef).region("region").build();
    when(outcomeService.resolve(any(), any())).thenReturn(awsSamInfrastructureOutcome);

    AwsSamDirectoryManifestOutcome awsSamDirectoryManifestOutcome = AwsSamDirectoryManifestOutcome.builder().build();
    HashMap<String, ManifestOutcome> manifestOutcomeHashMap = new HashMap<>();
    manifestOutcomeHashMap.put("manifest", awsSamDirectoryManifestOutcome);
    ManifestsOutcome manifestsOutcome = new ManifestsOutcome(manifestOutcomeHashMap);
    when(outcomeService.resolveOptional(any(), any()))
        .thenReturn(OptionalOutcome.builder().outcome(manifestsOutcome).build());
    doReturn(awsSamDirectoryManifestOutcome).when(awsSamStepHelper).getAwsSamDirectoryManifestOutcome(any());

    return pluginCreationRequest;
  }
}
