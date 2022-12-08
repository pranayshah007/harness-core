package io.harness.cdng.tas;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.infra.beans.TanzuApplicationServiceInfrastructureOutcome;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.manifest.yaml.TasManifestOutcome;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.cdng.tas.beans.TasSetupDataOutcome;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.tasconnector.TasConnectorDTO;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.task.pcf.response.TasInfraConfig;
import io.harness.ng.core.BaseNGAccess;
import io.harness.pcf.model.CfCliVersionNG;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.sdk.core.data.OptionalOutcome;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
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
import java.util.Arrays;
import java.util.List;
import java.util.Map;

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

    @Mock
    private CDStepHelper cdStepHelper;
    @Mock private TasEntityHelper tasEntityHelper;
    @Mock private OutcomeService outcomeService;


    @Spy
    @InjectMocks
    private TasCommandStep tasCommandStep;

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
                .build();
        StepElementParameters stepElementParameters =
                StepElementParameters.builder().spec(tasSwapRollbackStepParameters).timeout(ParameterField.createValueField("10m")).build();
        TasSetupDataOutcome tasSetupDataOutcome = TasSetupDataOutcome.builder().build();
        OptionalSweepingOutput optionalSweepingOutput = OptionalSweepingOutput.builder().output(tasSetupDataOutcome).build();
        doReturn(optionalSweepingOutput).when(tasEntityHelper).getSetupOutcome(any(), any(), any(), any(), any(), any());

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



        doReturn(infrastructureOutcome).when(cdStepHelper).getInfrastructureOutcome(ambiance);
        String key = "key";
        String value = "value";
        Map<String,String> allFilesFetch = Map.of(key, value);
        doReturn(tasInfraConfig).when(cdStepHelper).getTasInfraConfig(infrastructureOutcome, ambiance);
        List<String> pathsFromScript = Arrays.asList();
        String rawScript = "rawScript";
        String repoRoot = "repoRoot";
        TasExecutionPassThroughData tasExecutionPassThroughData =
                TasExecutionPassThroughData.builder()
                        .allFilesFetched(allFilesFetch)
                        .cfCliVersion(CfCliVersionNG.V7)
                        .pathsFromScript(pathsFromScript)
                        .rawScript(rawScript)
                        .repoRoot(repoRoot)
                        .build();
        ManifestOutcome manifestOutcome = TasManifestOutcome.builder().build();
        UnitProgressData unitProgressData = UnitProgressData.builder().build();

        Mockito.mockStatic(StepUtils.class);
        PowerMockito.when(StepUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(TaskRequest.newBuilder().build());

        TaskChainResponse taskChainResponse = tasCommandStep.executeTasTask(manifestOutcome, ambiance, stepElementParameters, tasExecutionPassThroughData, true,
                unitProgressData);

        PowerMockito.verifyStatic(StepUtils.class, times(1));
        StepUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any());

        assertThat(taskChainResponse.isChainEnd()).isEqualTo(true);
        assertThat(taskChainResponse.getPassThroughData()).isInstanceOf(TasExecutionPassThroughData.class);
        assertThat(taskChainResponse.getPassThroughData()).isEqualTo(tasExecutionPassThroughData);
        assertThat(taskChainResponse.getTaskRequest()).isEqualTo(TaskRequest.newBuilder().build());
    }
}
