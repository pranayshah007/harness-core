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
import io.harness.cdng.aws.sam.AwsSamBuildStepInfo;
import io.harness.cdng.aws.sam.AwsSamStepHelper;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
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
import io.harness.delegate.beans.connector.docker.DockerAuthType;
import io.harness.delegate.beans.connector.docker.DockerAuthenticationDTO;
import io.harness.delegate.beans.connector.docker.DockerConnectorDTO;
import io.harness.delegate.beans.connector.docker.DockerUserNamePasswordDTO;
import io.harness.encryption.SecretRefData;
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
public class AwsSamBuildPluginInfoProviderTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private OutcomeService outcomeService;
  @Mock private KryoSerializer kryoSerializer;
  @Mock private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Mock private InstanceInfoService instanceInfoService;
  @Mock private CDFeatureFlagHelper cdFeatureFlagHelper;
  @Mock private CDStepHelper cdStepHelper;
  @Mock private ServerlessEntityHelper serverlessEntityHelper;
  @Mock ExecutionSweepingOutputService executionSweepingOutputService;

  @Mock AwsSamStepHelper awsSamStepHelper;

  @Mock PluginInfoProviderUtils pluginInfoProviderUtils;
  @Named(DEFAULT_CONNECTOR_SERVICE) @Mock private ConnectorService connectorService;
  @InjectMocks @Spy private AwsSamBuildPluginInfoProvider awsSamBuildPluginInfoProvider;

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testGetPluginInfo() throws IOException {
    String accountId = "accountId";
    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions("accountId", accountId).build();

    String jsonNode = "jsonNdod";
    PluginCreationRequest pluginCreationRequest = PluginCreationRequest.newBuilder().setStepJsonNode(jsonNode).build();
    CdAbstractStepNode cdAbstractStepNode = mock(CdAbstractStepNode.class);
    Mockito.mockStatic(YamlUtils.class);
    when(YamlUtils.read(anyString(), (Class<Object>) any())).thenReturn(cdAbstractStepNode);
    doReturn("identifier").when(cdAbstractStepNode).getIdentifier();
    doReturn("name").when(cdAbstractStepNode).getName();
    doReturn("uuid").when(cdAbstractStepNode).getUuid();
    String connectorRef = "ref";

    AwsSamBuildStepInfo awsSamBuildStepInfo =
        AwsSamBuildStepInfo.infoBuilder()
            .resources(ContainerResource.builder().build())
            .runAsUser(ParameterField.<Integer>builder().value(1).build())
            .samBuildDockerRegistryConnectorRef(ParameterField.createValueField(connectorRef))
            .connectorRef(ParameterField.createValueField("ref"))
            .build();
    doReturn(awsSamBuildStepInfo).when(cdAbstractStepNode).getStepSpecType();

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
    when(PluginInfoProviderHelper.buildPluginDetails(any(), any(), any())).thenReturn(pluginDetailsBuilder);
    when(PluginInfoProviderHelper.getImageDetails(any(), any(), any())).thenReturn(imageDetails);

    ConnectorConfigDTO connectorConfigDTO =
        DockerConnectorDTO.builder()
            .dockerRegistryUrl("url")
            .auth(DockerAuthenticationDTO.builder()
                      .authType(DockerAuthType.ANONYMOUS)
                      .credentials(DockerUserNamePasswordDTO.builder()
                                       .username("username")
                                       .passwordRef(SecretRefData.builder().decryptedValue(new char[] {'a'}).build())
                                       .build())
                      .build())
            .build();
    ConnectorInfoDTO connectorInfoDTO = mock(ConnectorInfoDTO.class);
    ConnectorResponseDTO connectorResponseDTO = mock(ConnectorResponseDTO.class);
    when(connectorService.get(any(), any(), any(), any())).thenReturn(Optional.of(connectorResponseDTO));
    when(connectorResponseDTO.getConnector()).thenReturn(connectorInfoDTO);
    when(connectorInfoDTO.getConnectorConfig()).thenReturn(connectorConfigDTO);

    AwsSamDirectoryManifestOutcome awsSamDirectoryManifestOutcome = AwsSamDirectoryManifestOutcome.builder().build();
    HashMap<String, ManifestOutcome> manifestOutcomeHashMap = new HashMap<>();
    manifestOutcomeHashMap.put("manifest", awsSamDirectoryManifestOutcome);
    ManifestsOutcome manifestsOutcome = new ManifestsOutcome(manifestOutcomeHashMap);
    when(outcomeService.resolveOptional(any(), any()))
        .thenReturn(OptionalOutcome.builder().outcome(manifestsOutcome).build());

    String samDir = "samDir";
    doReturn(awsSamDirectoryManifestOutcome).when(awsSamStepHelper).getAwsSamDirectoryManifestOutcome(any());
    doReturn(samDir).when(awsSamStepHelper).getSamDirectoryPathFromAwsSamDirectoryManifestOutcome(any());

    PluginCreationResponseWrapper pluginCreationResponseWrapper =
        awsSamBuildPluginInfoProvider.getPluginInfo(pluginCreationRequest, Collections.emptySet(), ambiance);

    assertThat(pluginCreationResponseWrapper.getStepInfo().getIdentifier()).isEqualTo("identifier");
    assertThat(pluginCreationResponseWrapper.getStepInfo().getName()).isEqualTo("name");
    assertThat(pluginCreationResponseWrapper.getStepInfo().getUuid()).isEqualTo("uuid");
  }
}
