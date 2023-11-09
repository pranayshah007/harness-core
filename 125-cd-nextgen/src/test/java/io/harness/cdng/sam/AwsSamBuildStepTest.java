/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.sam;

import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.callback.DelegateCallbackToken;
import io.harness.category.element.UnitTests;
import io.harness.cdng.aws.sam.AwsSamBuildStep;
import io.harness.cdng.aws.sam.AwsSamBuildStepParameters;
import io.harness.cdng.aws.sam.AwsSamStepHelper;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.manifest.steps.outcome.ManifestsOutcome;
import io.harness.cdng.manifest.yaml.AwsSamDirectoryManifestOutcome;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.docker.DockerAuthType;
import io.harness.delegate.beans.connector.docker.DockerAuthenticationDTO;
import io.harness.delegate.beans.connector.docker.DockerConnectorDTO;
import io.harness.delegate.beans.connector.docker.DockerUserNamePasswordDTO;
import io.harness.encryption.SecretRefData;
import io.harness.ng.core.NGAccess;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalOutcome;
import io.harness.pms.sdk.core.plugin.ContainerStepExecutionResponseHelper;
import io.harness.pms.sdk.core.plugin.ContainerUnitStepUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.ParameterField;
import io.harness.product.ci.engine.proto.UnitStep;
import io.harness.rule.Owner;
import io.harness.tasks.ResponseData;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.name.Named;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
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
public class AwsSamBuildStepTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;
  @Mock private InstanceInfoService instanceInfoService;

  @Mock private AwsSamStepHelper awsSamStepHelper;

  @Mock private OutcomeService outcomeService;

  @Named(DEFAULT_CONNECTOR_SERVICE) @Mock private ConnectorService connectorService;

  @Mock private CDExpressionResolver cdExpressionResolver;

  @Mock private ContainerStepExecutionResponseHelper containerStepExecutionResponseHelper;

  @Mock Supplier<DelegateCallbackToken> delegateCallbackTokenSupplier;

  @InjectMocks @Spy private AwsSamBuildStep awsSamBuildStep;

  @Before
  public void setup() {}

  @SneakyThrows
  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testGetAnyOutComeForStep() {
    String accountId = "accountId";
    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions("accountId", accountId).build();
    AwsSamBuildStepParameters stepParameters =
        AwsSamBuildStepParameters.infoBuilder().image(ParameterField.<String>builder().value("sdaf").build()).build();
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(stepParameters).build();

    Map<String, ResponseData> responseDataMap = new HashMap<>();
    assertThat(awsSamBuildStep.getAnyOutComeForStep(ambiance, stepElementParameters, responseDataMap)).isNull();
  }

  @SneakyThrows
  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testGetSerialisedStep() {
    String accountId = "accountId";
    int port = 1;
    String callbackToken = "token";
    String displayName = "name";
    String id = "id";
    String logKey = "logKey";
    Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions("accountId", accountId).build();

    doReturn(1).when(awsSamBuildStep).getPort(any(), any());
    doReturn(122L).when(awsSamBuildStep).getTimeout(any(), any());
    UnitStep unitStep = mock(UnitStep.class);
    doReturn(accountId).when(unitStep).getAccountId();
    doReturn(port).when(unitStep).getContainerPort();
    doReturn(callbackToken).when(unitStep).getCallbackToken();
    doReturn(displayName).when(unitStep).getDisplayName();
    doReturn(id).when(unitStep).getId();
    doReturn(logKey).when(unitStep).getLogKey();
    doReturn(ParameterField.createValueField("image")).when(awsSamStepHelper).getImage(any());

    Mockito.mockStatic(ContainerUnitStepUtils.class);
    when(ContainerUnitStepUtils.serializeStepWithStepParameters(anyInt(), anyString(), anyString(), anyString(),
             anyLong(), anyString(), anyString(), any(), any(), any(), anyString(), any()))
        .thenReturn(unitStep);

    String connectorRef = "ref";

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
    doReturn(new HashMap<>()).when(awsSamStepHelper).validateEnvVariables(any());

    AwsSamBuildStepParameters stepParameters =
        AwsSamBuildStepParameters.infoBuilder()
            .image(ParameterField.<String>builder().value("sdaf").build())
            .samBuildDockerRegistryConnectorRef(ParameterField.createValueField(connectorRef))
            .connectorRef(ParameterField.createValueField("ref"))
            .build();
    StepElementParameters stepElementParameters =
        StepElementParameters.builder().identifier("identifier").name("name").spec(stepParameters).build();
    long timeout = 1000;
    String parkedTaskId = "parkedTaskId";
    UnitStep unit =
        awsSamBuildStep.getSerialisedStep(ambiance, stepElementParameters, accountId, logKey, timeout, parkedTaskId);
    assertThat(unit.getContainerPort()).isEqualTo(port);
    assertThat(unit.getAccountId()).isEqualTo(accountId);
    assertThat(unit.getCallbackToken()).isEqualTo(callbackToken);
    assertThat(unit.getDisplayName()).isEqualTo(displayName);
    assertThat(unit.getId()).isEqualTo(id);
    assertThat(unit.getLogKey()).isEqualTo(logKey);
  }
}