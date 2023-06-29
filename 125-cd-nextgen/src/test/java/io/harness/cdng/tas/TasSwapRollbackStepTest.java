/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.tas;

import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EnvironmentType;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGTestBase;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.infra.beans.TanzuApplicationServiceInfrastructureOutcome;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.manifest.yaml.TasManifestOutcome;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.cdng.tas.outcome.TasAppResizeDataOutcome;
import io.harness.cdng.tas.outcome.TasSetupDataOutcome;
import io.harness.cdng.tas.outcome.TasSwapRouteDataOutcome;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.tasconnector.TasConnectorDTO;
import io.harness.delegate.beans.instancesync.info.TasServerInstanceInfo;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.pcf.CfInternalInstanceElement;
import io.harness.delegate.beans.pcf.CfRollbackCommandResult;
import io.harness.delegate.task.pcf.response.CfDeployCommandResponseNG;
import io.harness.delegate.task.pcf.response.CfRollbackCommandResponseNG;
import io.harness.delegate.task.pcf.response.TasInfraConfig;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logstreaming.ILogStreamingStepClient;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.ng.core.BaseNGAccess;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepHelper;
import io.harness.steps.TaskRequestsUtils;
import io.harness.supplier.ThrowingSupplier;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.powermock.api.mockito.PowerMockito;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class TasSwapRollbackStepTest extends CDNGTestBase {
  @Spy private CDStepHelper cdStepHelper;
  @Mock private StepHelper stepHelper;
  @Mock private CDFeatureFlagHelper cdFeatureFlagHelper;
  @Mock private TasStepHelper tasStepHelper;
  @Mock private KryoSerializer kryoSerializer;
  @Mock private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Mock private InstanceInfoService instanceInfoService;
  @Mock private OutcomeService outcomeService;
  @Mock private TasEntityHelper tasEntityHelper;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;
  @InjectMocks private TasSwapRollbackStep tasSwapRollbackStep;

  private final TanzuApplicationServiceInfrastructureOutcome infrastructureOutcome =
      TanzuApplicationServiceInfrastructureOutcome.builder()
          .connectorRef("account.tas")
          .organization("dev-org")
          .space("dev-space")
          .build();
  private TasSwapRollbackStepParameters parameters =
      TasSwapRollbackStepParameters.infoBuilder()
          .delegateSelectors(ParameterField.createValueField(List.of(new TaskSelectorYaml("selector-1"))))
          .build();

  private final Ambiance ambiance = getAmbiance();

  @Before
  public void setup() {
    ILogStreamingStepClient logStreamingStepClient;
    logStreamingStepClient = mock(ILogStreamingStepClient.class);
    when(logStreamingStepClientFactory.getLogStreamingStepClient(any())).thenReturn(logStreamingStepClient);
    doReturn(infrastructureOutcome).when(cdStepHelper).getInfrastructureOutcome(ambiance);

    doReturn(EnvironmentType.PROD).when(stepHelper).getEnvironmentType(ambiance);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testValidateResourcesFFEnabled() {
    doReturn(true).when(cdFeatureFlagHelper).isEnabled(anyString(), eq(FeatureName.NG_SVC_ENV_REDESIGN));
    Ambiance ambiance = getAmbiance();
    StepElementParameters stepElementParameters =
        StepElementParameters.builder().type("BasicAppSetup").spec(parameters).build();
    tasSwapRollbackStep.validateResources(ambiance, stepElementParameters);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testValidateResourcesFFDisabled() {
    doReturn(false).when(cdFeatureFlagHelper).isEnabled(anyString(), eq(FeatureName.NG_SVC_ENV_REDESIGN));
    Ambiance ambiance = getAmbiance();
    StepElementParameters stepElementParameters =
        StepElementParameters.builder().type("BasicAppSetup").spec(parameters).build();
    assertThatThrownBy(() -> tasSwapRollbackStep.validateResources(ambiance, stepElementParameters))
        .hasMessage("CDS_TAS_NG FF is not enabled for this account. Please contact harness customer care.");
  }

  private Ambiance getAmbiance() {
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put(SetupAbstractionKeys.accountId, "account");
    setupAbstractions.put(SetupAbstractionKeys.orgIdentifier, "org");
    setupAbstractions.put(SetupAbstractionKeys.projectIdentifier, "project");

    return Ambiance.newBuilder()
        .putAllSetupAbstractions(setupAbstractions)
        .setStageExecutionId("stageExecutionId")
        .build();
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void testGetStepParametersClass() {
    assertThat(tasSwapRollbackStep.getStepParametersClass()).isEqualTo(StepElementParameters.class);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void obtainTaskAfterRbacTest() {
    String basicSetupFqn = "basicSetupFqn";
    String tasBGSetupFqn = "tasBGSetupFqn";
    String tasCanarySetupFqn = "tasCanarySetupFqn";
    String tasResizeFqn = "tasResizeFqn";
    String tasSwapRoutesFqn = "tasSwapRoutesFqn";
    TasSwapRollbackStepParameters tasSwapRollbackStepParameters =
        TasSwapRollbackStepParameters.infoBuilder()
            .tasBasicSetupFqn(basicSetupFqn)
            .tasBGSetupFqn(tasBGSetupFqn)
            .tasCanarySetupFqn(tasCanarySetupFqn)
            .tasResizeFqn(tasResizeFqn)
            .tasSwapRoutesFqn(tasSwapRoutesFqn)
            .upsizeInActiveApp(ParameterField.createValueField(Boolean.TRUE))
            .build();
    StepElementParameters stepElementParameters = StepElementParameters.builder()
                                                      .spec(tasSwapRollbackStepParameters)
                                                      .timeout(ParameterField.createValueField("10m"))
                                                      .build();

    TasSetupDataOutcome tasSetupDataOutcome = TasSetupDataOutcome.builder().build();
    OptionalSweepingOutput optionalSweepingOutput =
        OptionalSweepingOutput.builder().found(true).output(tasSetupDataOutcome).build();
    doReturn(optionalSweepingOutput)
        .when(tasEntityHelper)
        .getSetupOutcome(ambiance, tasSwapRollbackStepParameters.getTasBGSetupFqn(),
            tasSwapRollbackStepParameters.getTasBasicSetupFqn(), tasSwapRollbackStepParameters.getTasCanarySetupFqn(),
            OutcomeExpressionConstants.TAS_APP_SETUP_OUTCOME, executionSweepingOutputService);

    InfrastructureOutcome infrastructureOutcome = TanzuApplicationServiceInfrastructureOutcome.builder().build();
    doReturn(infrastructureOutcome)
        .when(outcomeService)
        .resolve(ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));

    BaseNGAccess baseNGAccess = BaseNGAccess.builder().build();
    doReturn(baseNGAccess).when(tasEntityHelper).getBaseNGAccess("account", "org", "project");
    TasConnectorDTO tasConnectorDTO = TasConnectorDTO.builder().build();
    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder().connectorConfig(tasConnectorDTO).build();
    doReturn(connectorInfoDTO)
        .when(tasEntityHelper)
        .getConnectorInfoDTO(infrastructureOutcome.getConnectorRef(), "account", "org", "project");

    List<EncryptedDataDetail> encryptedDataDetails = Collections.emptyList();
    doReturn(encryptedDataDetails).when(tasEntityHelper).getEncryptionDataDetails(connectorInfoDTO, baseNGAccess);

    String org = "org";
    String space = "space";
    TasInfraConfig tasInfraConfig = TasInfraConfig.builder()
                                        .organization(org)
                                        .space(space)
                                        .tasConnectorDTO(tasConnectorDTO)
                                        .encryptionDataDetails(encryptedDataDetails)
                                        .build();

    TasAppResizeDataOutcome tasAppResizeDataOutcome = TasAppResizeDataOutcome.builder().build();
    OptionalSweepingOutput tasAppResizeDataOptional =
        OptionalSweepingOutput.builder().found(true).output(tasAppResizeDataOutcome).build();
    doReturn(tasAppResizeDataOptional)
        .when(executionSweepingOutputService)
        .resolveOptional(ambiance,
            RefObjectUtils.getSweepingOutputRefObject(tasSwapRollbackStepParameters.getTasResizeFqn() + "."
                + OutcomeExpressionConstants.TAS_APP_RESIZE_OUTCOME));

    TasSwapRouteDataOutcome tasSwapRouteDataOutcome = TasSwapRouteDataOutcome.builder().build();
    OptionalSweepingOutput tasSwapRouteDataOptional =
        OptionalSweepingOutput.builder().found(true).output(tasSwapRouteDataOutcome).build();
    doReturn(tasSwapRouteDataOptional)
        .when(executionSweepingOutputService)
        .resolveOptional(ambiance,
            RefObjectUtils.getSweepingOutputRefObject(tasSwapRollbackStepParameters.getTasSwapRoutesFqn() + "."
                + OutcomeExpressionConstants.TAS_SWAP_ROUTES_OUTCOME));

    MockedStatic<TaskRequestsUtils> mockedStatic = Mockito.mockStatic(TaskRequestsUtils.class);
    PowerMockito.when(TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());
    doReturn(EnvironmentType.PROD).when(stepHelper).getEnvironmentType(ambiance);

    TasManifestOutcome tasManifestOutcome = TasManifestOutcome.builder().build();
    tasSwapRollbackStep.obtainTaskAfterRbac(ambiance, stepElementParameters, StepInputPackage.builder().build());

    PowerMockito.verifyStatic(TaskRequestsUtils.class, times(1));
    TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any());
    mockedStatic.close();
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void obtainTaskAfterRbacTestWhenSetupOutcomeNotPresent() {
    String basicSetupFqn = "basicSetupFqn";
    String tasBGSetupFqn = "tasBGSetupFqn";
    String tasCanarySetupFqn = "tasCanarySetupFqn";
    String tasResizeFqn = "tasResizeFqn";
    String tasSwapRoutesFqn = "tasSwapRoutesFqn";
    TasSwapRollbackStepParameters tasSwapRollbackStepParameters =
        TasSwapRollbackStepParameters.infoBuilder()
            .tasBasicSetupFqn(basicSetupFqn)
            .tasBGSetupFqn(tasBGSetupFqn)
            .tasCanarySetupFqn(tasCanarySetupFqn)
            .tasResizeFqn(tasResizeFqn)
            .tasSwapRoutesFqn(tasSwapRoutesFqn)
            .upsizeInActiveApp(ParameterField.createValueField(Boolean.TRUE))
            .build();
    StepElementParameters stepElementParameters = StepElementParameters.builder()
                                                      .spec(tasSwapRollbackStepParameters)
                                                      .timeout(ParameterField.createValueField("10m"))
                                                      .build();

    TasSetupDataOutcome tasSetupDataOutcome = TasSetupDataOutcome.builder().build();
    OptionalSweepingOutput optionalSweepingOutput =
        OptionalSweepingOutput.builder().found(false).output(tasSetupDataOutcome).build();
    doReturn(optionalSweepingOutput)
        .when(tasEntityHelper)
        .getSetupOutcome(ambiance, tasSwapRollbackStepParameters.getTasBGSetupFqn(),
            tasSwapRollbackStepParameters.getTasBasicSetupFqn(), tasSwapRollbackStepParameters.getTasCanarySetupFqn(),
            OutcomeExpressionConstants.TAS_APP_SETUP_OUTCOME, executionSweepingOutputService);

    InfrastructureOutcome infrastructureOutcome = TanzuApplicationServiceInfrastructureOutcome.builder().build();
    doReturn(infrastructureOutcome)
        .when(outcomeService)
        .resolve(ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));

    BaseNGAccess baseNGAccess = BaseNGAccess.builder().build();
    doReturn(baseNGAccess).when(tasEntityHelper).getBaseNGAccess("account", "org", "project");
    TasConnectorDTO tasConnectorDTO = TasConnectorDTO.builder().build();
    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder().connectorConfig(tasConnectorDTO).build();
    doReturn(connectorInfoDTO)
        .when(tasEntityHelper)
        .getConnectorInfoDTO(infrastructureOutcome.getConnectorRef(), "account", "org", "project");

    List<EncryptedDataDetail> encryptedDataDetails = Collections.emptyList();
    doReturn(encryptedDataDetails).when(tasEntityHelper).getEncryptionDataDetails(connectorInfoDTO, baseNGAccess);

    String org = "org";
    String space = "space";
    TasInfraConfig tasInfraConfig = TasInfraConfig.builder()
                                        .organization(org)
                                        .space(space)
                                        .tasConnectorDTO(tasConnectorDTO)
                                        .encryptionDataDetails(encryptedDataDetails)
                                        .build();

    TasAppResizeDataOutcome tasAppResizeDataOutcome = TasAppResizeDataOutcome.builder().build();
    OptionalSweepingOutput tasAppResizeDataOptional =
        OptionalSweepingOutput.builder().found(true).output(tasAppResizeDataOutcome).build();
    doReturn(tasAppResizeDataOptional)
        .when(executionSweepingOutputService)
        .resolveOptional(ambiance,
            RefObjectUtils.getSweepingOutputRefObject(tasSwapRollbackStepParameters.getTasResizeFqn() + "."
                + OutcomeExpressionConstants.TAS_APP_RESIZE_OUTCOME));

    TasSwapRouteDataOutcome tasSwapRouteDataOutcome = TasSwapRouteDataOutcome.builder().build();
    OptionalSweepingOutput tasSwapRouteDataOptional =
        OptionalSweepingOutput.builder().found(true).output(tasSwapRouteDataOutcome).build();
    doReturn(tasSwapRouteDataOptional)
        .when(executionSweepingOutputService)
        .resolveOptional(ambiance,
            RefObjectUtils.getSweepingOutputRefObject(tasSwapRollbackStepParameters.getTasSwapRoutesFqn() + "."
                + OutcomeExpressionConstants.TAS_SWAP_ROUTES_OUTCOME));
    MockedStatic<TaskRequestsUtils> mockedStatic = Mockito.mockStatic(TaskRequestsUtils.class);
    PowerMockito.when(TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());
    doReturn(EnvironmentType.PROD).when(stepHelper).getEnvironmentType(ambiance);

    TasManifestOutcome tasManifestOutcome = TasManifestOutcome.builder().build();
    tasSwapRollbackStep.obtainTaskAfterRbac(ambiance, stepElementParameters, StepInputPackage.builder().build());

    PowerMockito.verifyStatic(TaskRequestsUtils.class, times(0));
    TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any());
    mockedStatic.close();
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void obtainTaskAfterRbacTestWhenSwapRouteOutcomeNotPresent() {
    String basicSetupFqn = "basicSetupFqn";
    String tasBGSetupFqn = "tasBGSetupFqn";
    String tasCanarySetupFqn = "tasCanarySetupFqn";
    String tasResizeFqn = "tasResizeFqn";
    String tasSwapRoutesFqn = "tasSwapRoutesFqn";
    TasSwapRollbackStepParameters tasSwapRollbackStepParameters =
        TasSwapRollbackStepParameters.infoBuilder()
            .tasBasicSetupFqn(basicSetupFqn)
            .tasBGSetupFqn(tasBGSetupFqn)
            .tasCanarySetupFqn(tasCanarySetupFqn)
            .tasResizeFqn(tasResizeFqn)
            .tasSwapRoutesFqn(tasSwapRoutesFqn)
            .upsizeInActiveApp(ParameterField.createValueField(Boolean.TRUE))
            .build();
    StepElementParameters stepElementParameters = StepElementParameters.builder()
                                                      .spec(tasSwapRollbackStepParameters)
                                                      .timeout(ParameterField.createValueField("10m"))
                                                      .build();

    TasSetupDataOutcome tasSetupDataOutcome = TasSetupDataOutcome.builder().build();
    OptionalSweepingOutput optionalSweepingOutput =
        OptionalSweepingOutput.builder().found(true).output(tasSetupDataOutcome).build();
    doReturn(optionalSweepingOutput)
        .when(tasEntityHelper)
        .getSetupOutcome(ambiance, tasSwapRollbackStepParameters.getTasBGSetupFqn(),
            tasSwapRollbackStepParameters.getTasBasicSetupFqn(), tasSwapRollbackStepParameters.getTasCanarySetupFqn(),
            OutcomeExpressionConstants.TAS_APP_SETUP_OUTCOME, executionSweepingOutputService);

    InfrastructureOutcome infrastructureOutcome = TanzuApplicationServiceInfrastructureOutcome.builder().build();
    doReturn(infrastructureOutcome)
        .when(outcomeService)
        .resolve(ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));

    BaseNGAccess baseNGAccess = BaseNGAccess.builder().build();
    doReturn(baseNGAccess).when(tasEntityHelper).getBaseNGAccess("account", "org", "project");
    TasConnectorDTO tasConnectorDTO = TasConnectorDTO.builder().build();
    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder().connectorConfig(tasConnectorDTO).build();
    doReturn(connectorInfoDTO)
        .when(tasEntityHelper)
        .getConnectorInfoDTO(infrastructureOutcome.getConnectorRef(), "account", "org", "project");

    List<EncryptedDataDetail> encryptedDataDetails = Collections.emptyList();
    doReturn(encryptedDataDetails).when(tasEntityHelper).getEncryptionDataDetails(connectorInfoDTO, baseNGAccess);

    String org = "org";
    String space = "space";
    TasInfraConfig tasInfraConfig = TasInfraConfig.builder()
                                        .organization(org)
                                        .space(space)
                                        .tasConnectorDTO(tasConnectorDTO)
                                        .encryptionDataDetails(encryptedDataDetails)
                                        .build();

    TasAppResizeDataOutcome tasAppResizeDataOutcome = TasAppResizeDataOutcome.builder().build();
    OptionalSweepingOutput tasAppResizeDataOptional =
        OptionalSweepingOutput.builder().found(true).output(tasAppResizeDataOutcome).build();
    doReturn(tasAppResizeDataOptional)
        .when(executionSweepingOutputService)
        .resolveOptional(ambiance,
            RefObjectUtils.getSweepingOutputRefObject(tasSwapRollbackStepParameters.getTasResizeFqn() + "."
                + OutcomeExpressionConstants.TAS_APP_RESIZE_OUTCOME));

    TasSwapRouteDataOutcome tasSwapRouteDataOutcome = TasSwapRouteDataOutcome.builder().build();
    OptionalSweepingOutput tasSwapRouteDataOptional =
        OptionalSweepingOutput.builder().found(false).output(tasSwapRouteDataOutcome).build();
    doReturn(tasSwapRouteDataOptional)
        .when(executionSweepingOutputService)
        .resolveOptional(ambiance,
            RefObjectUtils.getSweepingOutputRefObject(tasSwapRollbackStepParameters.getTasSwapRoutesFqn() + "."
                + OutcomeExpressionConstants.TAS_SWAP_ROUTES_OUTCOME));

    MockedStatic<TaskRequestsUtils> mockedStatic = Mockito.mockStatic(TaskRequestsUtils.class);
    PowerMockito.when(TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());
    doReturn(EnvironmentType.PROD).when(stepHelper).getEnvironmentType(ambiance);

    TasManifestOutcome tasManifestOutcome = TasManifestOutcome.builder().build();
    tasSwapRollbackStep.obtainTaskAfterRbac(ambiance, stepElementParameters, StepInputPackage.builder().build());

    PowerMockito.verifyStatic(TaskRequestsUtils.class, times(1));
    TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any());
    mockedStatic.close();
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void handleTaskResultWithSecurityContextTest() throws Exception {
    String basicSetupFqn = "basicSetupFqn";
    String tasBGSetupFqn = "tasBGSetupFqn";
    String tasCanarySetupFqn = "tasCanarySetupFqn";
    String tasResizeFqn = "tasResizeFqn";
    String tasSwapRoutesFqn = "tasSwapRoutesFqn";
    TasSwapRollbackStepParameters tasSwapRollbackStepParameters =
        TasSwapRollbackStepParameters.infoBuilder()
            .tasBasicSetupFqn(basicSetupFqn)
            .tasBGSetupFqn(tasBGSetupFqn)
            .tasCanarySetupFqn(tasCanarySetupFqn)
            .tasResizeFqn(tasResizeFqn)
            .tasSwapRoutesFqn(tasSwapRoutesFqn)
            .upsizeInActiveApp(ParameterField.createValueField(Boolean.TRUE))
            .build();
    StepElementParameters stepElementParameters = StepElementParameters.builder()
                                                      .spec(tasSwapRollbackStepParameters)
                                                      .timeout(ParameterField.createValueField("10m"))
                                                      .build();

    String instanceIndex = "1";
    String appId = "id";
    String displayName = "displayName";
    UnitProgressData unitProgressData = UnitProgressData.builder().build();
    CfInternalInstanceElement cfInternalInstanceElement = CfInternalInstanceElement.builder()
                                                              .instanceIndex(instanceIndex)
                                                              .applicationId(appId)
                                                              .displayName(displayName)
                                                              .build();
    CfRollbackCommandResult cfRollbackCommandResult =
        CfRollbackCommandResult.builder().newAppInstances(Arrays.asList(cfInternalInstanceElement)).build();
    CfRollbackCommandResponseNG responseData = CfRollbackCommandResponseNG.builder()
                                                   .unitProgressData(unitProgressData)
                                                   .cfRollbackCommandResult(cfRollbackCommandResult)
                                                   .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                   .build();

    ThrowingSupplier<CfRollbackCommandResponseNG> responseDataSupplier = () -> responseData;
    CfRollbackCommandResponseNG cfRollbackCommandResponseNG = (CfRollbackCommandResponseNG) responseDataSupplier.get();

    String org = "org";
    String space = "space";
    TanzuApplicationServiceInfrastructureOutcome tanzuApplicationServiceInfrastructureOutcome =
        TanzuApplicationServiceInfrastructureOutcome.builder().organization(org).space(space).build();
    doReturn(tanzuApplicationServiceInfrastructureOutcome)
        .when(outcomeService)
        .resolve(ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));

    TasServerInstanceInfo tasServerInstanceInfo = TasServerInstanceInfo.builder()
                                                      .id(appId + ":" + instanceIndex)
                                                      .instanceIndex(instanceIndex)
                                                      .tasApplicationName(displayName)
                                                      .tasApplicationGuid(appId)
                                                      .organization(org)
                                                      .space(space)
                                                      .build();

    StepResponse.StepOutcome stepOutcome = StepResponse.StepOutcome.builder().name("name").build();
    doReturn(stepOutcome)
        .when(instanceInfoService)
        .saveServerInstancesIntoSweepingOutput(ambiance, Arrays.asList(tasServerInstanceInfo));

    StepResponse stepResponse1 =
        tasSwapRollbackStep.handleTaskResultWithSecurityContext(ambiance, stepElementParameters, () -> responseData);
    assertThat(stepResponse1.getStatus()).isEqualTo(Status.SUCCEEDED);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void handleTaskResultWithSecurityContextTestWhenFailure() throws Exception {
    String basicSetupFqn = "basicSetupFqn";
    String tasBGSetupFqn = "tasBGSetupFqn";
    String tasCanarySetupFqn = "tasCanarySetupFqn";
    String tasResizeFqn = "tasResizeFqn";
    String tasSwapRoutesFqn = "tasSwapRoutesFqn";
    TasSwapRollbackStepParameters tasSwapRollbackStepParameters =
        TasSwapRollbackStepParameters.infoBuilder()
            .tasBasicSetupFqn(basicSetupFqn)
            .tasBGSetupFqn(tasBGSetupFqn)
            .tasCanarySetupFqn(tasCanarySetupFqn)
            .tasResizeFqn(tasResizeFqn)
            .tasSwapRoutesFqn(tasSwapRoutesFqn)
            .upsizeInActiveApp(ParameterField.createValueField(Boolean.TRUE))
            .build();
    StepElementParameters stepElementParameters = StepElementParameters.builder()
                                                      .spec(tasSwapRollbackStepParameters)
                                                      .timeout(ParameterField.createValueField("10m"))
                                                      .build();

    String instanceIndex = "1";
    String appId = "id";
    String displayName = "displayName";
    UnitProgressData unitProgressData = UnitProgressData.builder().build();
    CfInternalInstanceElement cfInternalInstanceElement = CfInternalInstanceElement.builder()
                                                              .instanceIndex(instanceIndex)
                                                              .applicationId(appId)
                                                              .displayName(displayName)
                                                              .build();
    CfRollbackCommandResult cfRollbackCommandResult =
        CfRollbackCommandResult.builder().newAppInstances(Arrays.asList(cfInternalInstanceElement)).build();
    CfRollbackCommandResponseNG responseData = CfRollbackCommandResponseNG.builder()
                                                   .unitProgressData(unitProgressData)
                                                   .cfRollbackCommandResult(cfRollbackCommandResult)
                                                   .errorMessage("error")
                                                   .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                                                   .build();

    ThrowingSupplier<CfRollbackCommandResponseNG> responseDataSupplier = () -> responseData;
    CfRollbackCommandResponseNG cfRollbackCommandResponseNG = (CfRollbackCommandResponseNG) responseDataSupplier.get();

    String org = "org";
    String space = "space";
    TanzuApplicationServiceInfrastructureOutcome tanzuApplicationServiceInfrastructureOutcome =
        TanzuApplicationServiceInfrastructureOutcome.builder().organization(org).space(space).build();
    doReturn(tanzuApplicationServiceInfrastructureOutcome)
        .when(outcomeService)
        .resolve(ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));

    doReturn(unitProgressData).when(tasStepHelper).completeUnitProgressData(any(), any(), any());
    TasServerInstanceInfo tasServerInstanceInfo = TasServerInstanceInfo.builder()
                                                      .id(appId + ":" + instanceIndex)
                                                      .instanceIndex(instanceIndex)
                                                      .tasApplicationName(displayName)
                                                      .tasApplicationGuid(appId)
                                                      .organization(org)
                                                      .space(space)
                                                      .build();

    StepResponse.StepOutcome stepOutcome = StepResponse.StepOutcome.builder().name("name").build();
    doReturn(stepOutcome)
        .when(instanceInfoService)
        .saveServerInstancesIntoSweepingOutput(ambiance, Arrays.asList(tasServerInstanceInfo));

    StepResponse stepResponse1 =
        tasSwapRollbackStep.handleTaskResultWithSecurityContext(ambiance, stepElementParameters, () -> responseData);
    assertThat(stepResponse1.getStatus()).isEqualTo(Status.FAILED);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void handleTaskResultWithSecurityContextCommandExecutionStatusWithNullResponseFromDelegate() {
    CfDeployCommandResponseNG cfDeployCommandResponseNG = CfDeployCommandResponseNG.builder().build();
    String basicSetupFqn = "basicSetupFqn";
    String tasBGSetupFqn = "tasBGSetupFqn";
    String tasCanarySetupFqn = "tasCanarySetupFqn";
    String tasResizeFqn = "tasResizeFqn";
    String tasSwapRoutesFqn = "tasSwapRoutesFqn";
    TasSwapRollbackStepParameters tasSwapRollbackStepParameters =
        TasSwapRollbackStepParameters.infoBuilder()
            .tasBasicSetupFqn(basicSetupFqn)
            .tasBGSetupFqn(tasBGSetupFqn)
            .tasCanarySetupFqn(tasCanarySetupFqn)
            .tasResizeFqn(tasResizeFqn)
            .tasSwapRoutesFqn(tasSwapRoutesFqn)
            .upsizeInActiveApp(ParameterField.createValueField(Boolean.TRUE))
            .build();
    StepElementParameters stepElementParameters = StepElementParameters.builder()
                                                      .spec(tasSwapRollbackStepParameters)
                                                      .timeout(ParameterField.createValueField("10m"))
                                                      .build();
    assertThatThrownBy(()
                           -> tasSwapRollbackStep.handleTaskResultWithSecurityContext(
                               ambiance, stepElementParameters, () -> cfDeployCommandResponseNG))
        .hasMessageContaining("CfDeployCommandResponseNG cannot be cast to class");
  }

  @Test(expected = Exception.class)
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void handleTaskResultWithSecurityContextTestWhenException() throws Exception {
    String basicSetupFqn = "basicSetupFqn";
    String tasBGSetupFqn = "tasBGSetupFqn";
    String tasCanarySetupFqn = "tasCanarySetupFqn";
    String tasResizeFqn = "tasResizeFqn";
    String tasSwapRoutesFqn = "tasSwapRoutesFqn";
    TasSwapRollbackStepParameters tasSwapRollbackStepParameters =
        TasSwapRollbackStepParameters.infoBuilder()
            .tasBasicSetupFqn(basicSetupFqn)
            .tasBGSetupFqn(tasBGSetupFqn)
            .tasCanarySetupFqn(tasCanarySetupFqn)
            .tasResizeFqn(tasResizeFqn)
            .tasSwapRoutesFqn(tasSwapRoutesFqn)
            .upsizeInActiveApp(ParameterField.createValueField(Boolean.TRUE))
            .build();
    StepElementParameters stepElementParameters = StepElementParameters.builder()
                                                      .spec(tasSwapRollbackStepParameters)
                                                      .timeout(ParameterField.createValueField("10m"))
                                                      .build();

    String instanceIndex = "1";
    String appId = "id";
    String displayName = "displayName";
    UnitProgressData unitProgressData = UnitProgressData.builder().build();
    CfInternalInstanceElement cfInternalInstanceElement = CfInternalInstanceElement.builder()
                                                              .instanceIndex(instanceIndex)
                                                              .applicationId(appId)
                                                              .displayName(displayName)
                                                              .build();
    CfRollbackCommandResult cfRollbackCommandResult =
        CfRollbackCommandResult.builder().newAppInstances(Arrays.asList(cfInternalInstanceElement)).build();
    CfRollbackCommandResponseNG responseData = CfRollbackCommandResponseNG.builder()
                                                   .unitProgressData(unitProgressData)
                                                   .cfRollbackCommandResult(cfRollbackCommandResult)
                                                   .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                   .build();

    ThrowingSupplier<CfRollbackCommandResponseNG> responseDataSupplier = () -> responseData;
    CfRollbackCommandResponseNG cfRollbackCommandResponseNG = (CfRollbackCommandResponseNG) responseDataSupplier.get();

    String org = "org";
    String space = "space";
    TanzuApplicationServiceInfrastructureOutcome tanzuApplicationServiceInfrastructureOutcome =
        TanzuApplicationServiceInfrastructureOutcome.builder().organization(org).space(space).build();
    doReturn(tanzuApplicationServiceInfrastructureOutcome)
        .when(outcomeService)
        .resolve(ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));

    TasServerInstanceInfo tasServerInstanceInfo = TasServerInstanceInfo.builder()
                                                      .id(appId + ":" + instanceIndex)
                                                      .instanceIndex(instanceIndex)
                                                      .tasApplicationName(displayName)
                                                      .tasApplicationGuid(appId)
                                                      .organization(org)
                                                      .space(space)
                                                      .build();

    StepResponse.StepOutcome stepOutcome = StepResponse.StepOutcome.builder().name("name").build();
    doReturn(stepOutcome)
        .when(instanceInfoService)
        .saveServerInstancesIntoSweepingOutput(ambiance, Arrays.asList(tasServerInstanceInfo));

    tasSwapRollbackStep.handleTaskResultWithSecurityContext(
        ambiance, stepElementParameters, () -> { throw new Exception("exception"); });
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void handleTaskResultWithSecurityContextTestWhenResultIsNull() throws Exception {
    String basicSetupFqn = "basicSetupFqn";
    String tasBGSetupFqn = "tasBGSetupFqn";
    String tasCanarySetupFqn = "tasCanarySetupFqn";
    String tasResizeFqn = "tasResizeFqn";
    String tasSwapRoutesFqn = "tasSwapRoutesFqn";
    TasSwapRollbackStepParameters tasSwapRollbackStepParameters =
        TasSwapRollbackStepParameters.infoBuilder()
            .tasBasicSetupFqn(basicSetupFqn)
            .tasBGSetupFqn(tasBGSetupFqn)
            .tasCanarySetupFqn(tasCanarySetupFqn)
            .tasResizeFqn(tasResizeFqn)
            .tasSwapRoutesFqn(tasSwapRoutesFqn)
            .upsizeInActiveApp(ParameterField.createValueField(Boolean.TRUE))
            .build();
    StepElementParameters stepElementParameters = StepElementParameters.builder()
                                                      .spec(tasSwapRollbackStepParameters)
                                                      .timeout(ParameterField.createValueField("10m"))
                                                      .build();

    String instanceIndex = "1";
    String appId = "id";
    String displayName = "displayName";
    UnitProgressData unitProgressData = UnitProgressData.builder().build();
    CfInternalInstanceElement cfInternalInstanceElement = CfInternalInstanceElement.builder()
                                                              .instanceIndex(instanceIndex)
                                                              .applicationId(appId)
                                                              .displayName(displayName)
                                                              .build();
    CfRollbackCommandResult cfRollbackCommandResult =
        CfRollbackCommandResult.builder().newAppInstances(Arrays.asList(cfInternalInstanceElement)).build();
    CfRollbackCommandResponseNG responseData = CfRollbackCommandResponseNG.builder()
                                                   .unitProgressData(unitProgressData)
                                                   .cfRollbackCommandResult(null)
                                                   .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                   .build();

    ThrowingSupplier<CfRollbackCommandResponseNG> responseDataSupplier = () -> responseData;
    CfRollbackCommandResponseNG cfRollbackCommandResponseNG = (CfRollbackCommandResponseNG) responseDataSupplier.get();

    String org = "org";
    String space = "space";
    TanzuApplicationServiceInfrastructureOutcome tanzuApplicationServiceInfrastructureOutcome =
        TanzuApplicationServiceInfrastructureOutcome.builder().organization(org).space(space).build();
    doReturn(tanzuApplicationServiceInfrastructureOutcome)
        .when(outcomeService)
        .resolve(ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));

    TasServerInstanceInfo tasServerInstanceInfo = TasServerInstanceInfo.builder()
                                                      .id(appId + ":" + instanceIndex)
                                                      .instanceIndex(instanceIndex)
                                                      .tasApplicationName(displayName)
                                                      .tasApplicationGuid(appId)
                                                      .organization(org)
                                                      .space(space)
                                                      .build();

    StepResponse.StepOutcome stepOutcome = StepResponse.StepOutcome.builder().name("name").build();
    doReturn(stepOutcome)
        .when(instanceInfoService)
        .saveServerInstancesIntoSweepingOutput(ambiance, Arrays.asList(tasServerInstanceInfo));

    StepResponse stepResponse1 =
        tasSwapRollbackStep.handleTaskResultWithSecurityContext(ambiance, stepElementParameters, () -> responseData);
    assertThat(stepResponse1.getStatus()).isEqualTo(Status.SUCCEEDED);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void obtainTaskAfterRbacTestWhenAppResizeOutcomeNotPresent() {
    String basicSetupFqn = "basicSetupFqn";
    String tasBGSetupFqn = "tasBGSetupFqn";
    String tasCanarySetupFqn = "tasCanarySetupFqn";
    String tasResizeFqn = "tasResizeFqn";
    String tasSwapRoutesFqn = "tasSwapRoutesFqn";
    TasSwapRollbackStepParameters tasSwapRollbackStepParameters =
        TasSwapRollbackStepParameters.infoBuilder()
            .tasBasicSetupFqn(basicSetupFqn)
            .tasBGSetupFqn(tasBGSetupFqn)
            .tasCanarySetupFqn(tasCanarySetupFqn)
            .tasResizeFqn(tasResizeFqn)
            .tasSwapRoutesFqn(tasSwapRoutesFqn)
            .upsizeInActiveApp(ParameterField.createValueField(Boolean.TRUE))
            .build();
    StepElementParameters stepElementParameters = StepElementParameters.builder()
                                                      .spec(tasSwapRollbackStepParameters)
                                                      .timeout(ParameterField.createValueField("10m"))
                                                      .build();

    TasSetupDataOutcome tasSetupDataOutcome = TasSetupDataOutcome.builder().build();
    OptionalSweepingOutput optionalSweepingOutput =
        OptionalSweepingOutput.builder().found(true).output(tasSetupDataOutcome).build();
    doReturn(optionalSweepingOutput)
        .when(tasEntityHelper)
        .getSetupOutcome(ambiance, tasSwapRollbackStepParameters.getTasBGSetupFqn(),
            tasSwapRollbackStepParameters.getTasBasicSetupFqn(), tasSwapRollbackStepParameters.getTasCanarySetupFqn(),
            OutcomeExpressionConstants.TAS_APP_SETUP_OUTCOME, executionSweepingOutputService);

    InfrastructureOutcome infrastructureOutcome = TanzuApplicationServiceInfrastructureOutcome.builder().build();
    doReturn(infrastructureOutcome)
        .when(outcomeService)
        .resolve(ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));

    BaseNGAccess baseNGAccess = BaseNGAccess.builder().build();
    doReturn(baseNGAccess).when(tasEntityHelper).getBaseNGAccess("account", "org", "project");
    TasConnectorDTO tasConnectorDTO = TasConnectorDTO.builder().build();
    ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder().connectorConfig(tasConnectorDTO).build();
    doReturn(connectorInfoDTO)
        .when(tasEntityHelper)
        .getConnectorInfoDTO(infrastructureOutcome.getConnectorRef(), "account", "org", "project");

    List<EncryptedDataDetail> encryptedDataDetails = Collections.emptyList();
    doReturn(encryptedDataDetails).when(tasEntityHelper).getEncryptionDataDetails(connectorInfoDTO, baseNGAccess);

    String org = "org";
    String space = "space";
    TasInfraConfig tasInfraConfig = TasInfraConfig.builder()
                                        .organization(org)
                                        .space(space)
                                        .tasConnectorDTO(tasConnectorDTO)
                                        .encryptionDataDetails(encryptedDataDetails)
                                        .build();

    TasAppResizeDataOutcome tasAppResizeDataOutcome = TasAppResizeDataOutcome.builder().build();
    OptionalSweepingOutput tasAppResizeDataOptional =
        OptionalSweepingOutput.builder().found(false).output(null).build();
    doReturn(tasAppResizeDataOptional)
        .when(executionSweepingOutputService)
        .resolveOptional(ambiance,
            RefObjectUtils.getSweepingOutputRefObject(tasSwapRollbackStepParameters.getTasResizeFqn() + "."
                + OutcomeExpressionConstants.TAS_APP_RESIZE_OUTCOME));

    TasSwapRouteDataOutcome tasSwapRouteDataOutcome = TasSwapRouteDataOutcome.builder().build();
    OptionalSweepingOutput tasSwapRouteDataOptional =
        OptionalSweepingOutput.builder().found(true).output(tasSwapRouteDataOutcome).build();
    doReturn(tasSwapRouteDataOptional)
        .when(executionSweepingOutputService)
        .resolveOptional(ambiance,
            RefObjectUtils.getSweepingOutputRefObject(tasSwapRollbackStepParameters.getTasSwapRoutesFqn() + "."
                + OutcomeExpressionConstants.TAS_SWAP_ROUTES_OUTCOME));

    MockedStatic<TaskRequestsUtils> mockedStatic = Mockito.mockStatic(TaskRequestsUtils.class);
    PowerMockito.when(TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());
    doReturn(EnvironmentType.PROD).when(stepHelper).getEnvironmentType(ambiance);

    TasManifestOutcome tasManifestOutcome = TasManifestOutcome.builder().build();
    tasSwapRollbackStep.obtainTaskAfterRbac(ambiance, stepElementParameters, StepInputPackage.builder().build());

    PowerMockito.verifyStatic(TaskRequestsUtils.class, times(1));
    TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any());
    mockedStatic.close();
  }
}
