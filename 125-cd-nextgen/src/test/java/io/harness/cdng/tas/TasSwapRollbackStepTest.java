package io.harness.cdng.tas;

import io.harness.CategoryTest;
import io.harness.beans.EnvironmentType;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.cdng.infra.beans.TanzuApplicationServiceInfrastructureOutcome;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.cdng.tas.beans.TasSetupDataOutcome;
import io.harness.cdng.tas.beans.TasSwapRouteDataOutcome;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.tasconnector.TasConnectorDTO;
import io.harness.delegate.task.pcf.response.TasInfraConfig;
import io.harness.ng.core.BaseNGAccess;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.steps.StepHelper;
import io.harness.steps.StepUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.powermock.api.mockito.PowerMockito;

import java.util.ArrayList;
import java.util.List;

import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;

public class TasSwapRollbackStepTest extends CategoryTest {
    @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

    private final Ambiance ambiance = Ambiance.newBuilder()
            .putSetupAbstractions(SetupAbstractionKeys.accountId, "test-account")
            .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "test-org")
            .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "test-project")
            .build();

    @Mock private TasEntityHelper tasEntityHelper;
    @Mock private OutcomeService outcomeService;
    @Mock private StepHelper stepHelper;
    @Mock private ExecutionSweepingOutputService executionSweepingOutputService;


    @Spy @InjectMocks private TasSwapRollbackStep tasSwapRollbackStep;

    @Test
    @Owner(developers = PIYUSH_BHUWALKA)
    @Category(UnitTests.class)
    public void obtainTaskAfterRbacTest() {
        String tasBasicSetupFqn = "bs";
        String tasRollbackFqn = "rollbackFqn";
        String tasBGSetupFqn = "tasBGSetupFqn";
        String tasSwapRoutesFqn = "tasSwapRoutesFqn";
        String tasCanarySetupFqn = "tasCanarySetupFqn";
        TasSwapRollbackStepParameters tasSwapRollbackStepParameters = TasSwapRollbackStepParameters.infoBuilder()
                .tasBasicSetupFqn(tasBasicSetupFqn)
                .tasRollbackFqn(tasRollbackFqn)
                .tasBGSetupFqn(tasBGSetupFqn)
                .tasSwapRoutesFqn(tasSwapRoutesFqn)
                .tasCanarySetupFqn(tasCanarySetupFqn)
                .upsizeInActiveApp(ParameterField.createValueField(Boolean.TRUE))
                .build();
        StepElementParameters stepElementParameters =
                StepElementParameters.builder().spec(tasSwapRollbackStepParameters).timeout(ParameterField.createValueField("10m")).build();
        TasSetupDataOutcome tasSetupDataOutcome = TasSetupDataOutcome.builder().build();
        OptionalSweepingOutput optionalSweepingOutput = OptionalSweepingOutput.builder().found(true).output(tasSetupDataOutcome).build();
        doReturn(optionalSweepingOutput).when(tasEntityHelper).getSetupOutcome(ambiance, tasBGSetupFqn, tasBasicSetupFqn, tasCanarySetupFqn, OutcomeExpressionConstants.TAS_APP_SETUP_OUTCOME, executionSweepingOutputService);

        String organization = "org";
        String space = "space";
        TanzuApplicationServiceInfrastructureOutcome infrastructureOutcome = TanzuApplicationServiceInfrastructureOutcome.builder()
                .organization(organization)
                .space(space)
                .build();
        doReturn(infrastructureOutcome).when(outcomeService).resolve(
                ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));
        BaseNGAccess baseNGAccess = BaseNGAccess.builder().build();
        doReturn(baseNGAccess).when(tasEntityHelper).getBaseNGAccess("test-account", "test-org", "test-project");

        TasConnectorDTO tasConnectorDTO = TasConnectorDTO.builder().build();
        ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder().connectorConfig(tasConnectorDTO).build();
        doReturn(connectorInfoDTO).when(tasEntityHelper).getConnectorInfoDTO(infrastructureOutcome.getConnectorRef(), "test-account", "test-org", "test-project");

        List<EncryptedDataDetail> encryptedDataDetailList = new ArrayList<>();
        doReturn(encryptedDataDetailList).when(tasEntityHelper).getEncryptionDataDetails(connectorInfoDTO, baseNGAccess);

        TasInfraConfig tasInfraConfig = TasInfraConfig.builder()
                .organization(organization)
                .space(space)
                .encryptionDataDetails(encryptedDataDetailList)
                .tasConnectorDTO(tasConnectorDTO)
                .build();

        TasSwapRouteDataOutcome tasSwapRouteDataOutcome = TasSwapRouteDataOutcome.builder().swapRouteOccurred(true).build();
        OptionalSweepingOutput optionalSweepingOutput1 = OptionalSweepingOutput.builder().found(true).output(tasSwapRouteDataOutcome).build();
        doReturn(optionalSweepingOutput1).when(executionSweepingOutputService).resolveOptional(ambiance,
                RefObjectUtils.getSweepingOutputRefObject(tasSwapRollbackStepParameters.getTasSwapRoutesFqn() + "."
                        + OutcomeExpressionConstants.TAS_SWAP_ROUTES_OUTCOME));

        doReturn(EnvironmentType.ALL).when(stepHelper).getEnvironmentType(ambiance);

        Mockito.mockStatic(StepUtils.class);
        TaskRequest expectedTaskRequest = TaskRequest.newBuilder().build();
        PowerMockito.when(StepUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(TaskRequest.newBuilder().build());

        StepInputPackage stepInputPackage = StepInputPackage.builder().build();
        TaskRequest taskRequest = tasSwapRollbackStep.obtainTaskAfterRbac(ambiance, stepElementParameters, stepInputPackage);

        PowerMockito.verifyStatic(StepUtils.class, times(1));
        StepUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any());

        assertThat(taskRequest).isEqualTo(expectedTaskRequest);
    }
}
