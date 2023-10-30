/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.elastigroup.rollback;

import static io.harness.rule.OwnerRule.FILIP;
import static io.harness.rule.OwnerRule.SARTHAK_KASAT;
import static io.harness.rule.OwnerRule.VITALIE;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGTestBase;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.cdng.elastigroup.ElastigroupEntityHelper;
import io.harness.cdng.elastigroup.beans.ElastigroupPreFetchOutcome;
import io.harness.cdng.elastigroup.beans.ElastigroupSetupDataOutcome;
import io.harness.cdng.execution.service.StageExecutionInfoService;
import io.harness.cdng.infra.beans.ElastigroupInfrastructureOutcome;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.task.spot.elastigroup.rollback.ElastigroupRollbackTaskParameters;
import io.harness.delegate.task.spot.elastigroup.rollback.ElastigroupRollbackTaskResponse;
import io.harness.eraro.Level;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logstreaming.ILogStreamingStepClient;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.rule.Owner;
import io.harness.spotinst.model.ElastiGroup;
import io.harness.spotinst.model.ElastiGroupCapacity;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

public class ElastigroupRollbackStepHelperTest extends CDNGTestBase {
  @Mock private ElastigroupEntityHelper entityHelper;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;
  @Mock private StageExecutionInfoService stageExecutionInfoService;
  @Mock private LogStreamingStepClientFactory logStreamingStepClientFactory;
  @Mock private ILogStreamingStepClient logStreamingStepClient;

  @InjectMocks @Spy private ElastigroupRollbackStepHelper elastigroupRollbackStepHelper;

  String elastigroupNamePrefix = "test-app";
  String awsRegion = "us-east-1";

  @Before
  public void setup() {
    doReturn(ConnectorInfoDTO.builder().build()).when(entityHelper).getConnectorInfoDTO(anyString(), any());
    doReturn(Collections.emptyList()).when(entityHelper).getEncryptionDataDetails(any(), any());
    when(logStreamingStepClientFactory.getLogStreamingStepClient(any())).thenReturn(logStreamingStepClient);
  }

  @Test
  @Owner(developers = {VITALIE})
  @Category(UnitTests.class)
  public void getElastigroupRollbackTaskParametersTest() {
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions(SetupAbstractionKeys.accountId, "test-account")
                            .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, "test-org")
                            .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, "test-project")
                            .build();

    StepElementParameters stepElementParameters = StepElementParameters.builder().build();

    InfrastructureOutcome infrastructureOutcome =
        ElastigroupInfrastructureOutcome.builder().connectorRef("some-connector").build();
    doReturn(infrastructureOutcome).when(elastigroupRollbackStepHelper).getInfrastructureOutcome(any());
    doReturn(ConnectorInfoDTO.builder().build()).when(elastigroupRollbackStepHelper).getConnector(anyString(), any());

    final List<ElastiGroup> prevElastigroups = Arrays.asList(ElastiGroup.builder().name("eg").build());

    ElastigroupPreFetchOutcome elastigroupPreFetchOutcome = ElastigroupPreFetchOutcome.builder()
                                                                .blueGreen(true)
                                                                .elastigroups(prevElastigroups)
                                                                .elastigroupNamePrefix(elastigroupNamePrefix)
                                                                .build();
    doReturn(elastigroupPreFetchOutcome).when(elastigroupRollbackStepHelper).getElastigroupPreFetchOutcome(any());

    ElastigroupSetupDataOutcome elastigroupSetupDataOutcome =
        ElastigroupSetupDataOutcome.builder()
            .awsRegion(awsRegion)
            .awsConnectorRef("connector")
            .oldElastigroupOriginalConfig(ElastiGroup.builder().capacity(ElastiGroupCapacity.builder().build()).build())
            .newElastigroupOriginalConfig(ElastiGroup.builder().capacity(ElastiGroupCapacity.builder().build()).build())
            .build();
    doReturn(elastigroupSetupDataOutcome).when(elastigroupRollbackStepHelper).getElastigroupSetupOutcome(any());

    ElastigroupRollbackTaskParameters result =
        elastigroupRollbackStepHelper.getElastigroupRollbackTaskParameters(null, ambiance, stepElementParameters);

    assertThat(result.getElastigroupNamePrefix()).isEqualTo(elastigroupNamePrefix);
    assertThat(result.getAwsRegion()).isEqualTo(awsRegion);
    assertThat(result.getPrevElastigroups()).isEqualTo(prevElastigroups);
    assertThat(result.getOldElastigroup()).isNotNull();
    assertThat(result.getNewElastigroup()).isNotNull();
  }

  @Test
  @Owner(developers = {FILIP})
  @Category(UnitTests.class)
  public void getExecutionUnitsTestAll() {
    // Given

    // When
    Collection<String> result = elastigroupRollbackStepHelper.getExecutionUnits();

    // Then
    assertThat(result).isNotNull().containsExactly("Downscale wait for steady state", "Rename old Elastigroup",
        "Swap Routes", "Upscale wait for steady state", "Upscale Elastigroup", "Delete new Elastigroup",
        "Downscale Elastigroup");
  }

  @Test
  @Owner(developers = {FILIP})
  @Category(UnitTests.class)
  public void getExecutionUnitsTestBGSuccessfulSetup() {
    // Given
    ElastigroupRollbackTaskParameters params = ElastigroupRollbackTaskParameters.builder()
                                                   .blueGreen(true)
                                                   .newElastigroup(ElastiGroup.builder().build())
                                                   .build();

    // When
    Collection<String> result = elastigroupRollbackStepHelper.getExecutionUnits(params);

    // Then
    assertThat(result).isNotNull().containsExactly("Upscale Elastigroup", "Upscale wait for steady state",
        "Rename old Elastigroup", "Swap Routes", "Downscale Elastigroup", "Downscale wait for steady state",
        "Delete new Elastigroup");
  }

  @Test
  @Owner(developers = {FILIP})
  @Category(UnitTests.class)
  public void getExecutionUnitsTestBGUnsuccessfulSetup() {
    // Given
    ElastigroupRollbackTaskParameters params =
        ElastigroupRollbackTaskParameters.builder().blueGreen(true).newElastigroup(null).build();

    // When
    Collection<String> result = elastigroupRollbackStepHelper.getExecutionUnits(params);

    // Then
    assertThat(result).isNotNull().containsExactly(
        "Downscale Elastigroup", "Downscale wait for steady state", "Delete new Elastigroup");
  }

  @Test
  @Owner(developers = {FILIP})
  @Category(UnitTests.class)
  public void getExecutionUnitsTestBasicAndCanary() {
    // Given
    ElastigroupRollbackTaskParameters params = ElastigroupRollbackTaskParameters.builder()
                                                   .blueGreen(false)
                                                   .newElastigroup(ElastiGroup.builder().build())
                                                   .build();

    // When
    Collection<String> result = elastigroupRollbackStepHelper.getExecutionUnits(params);

    // Then
    assertThat(result).isNotNull().containsExactly("Upscale Elastigroup", "Upscale wait for steady state",
        "Downscale Elastigroup", "Downscale wait for steady state", "Delete new Elastigroup");
  }

  @Test
  @Owner(developers = SARTHAK_KASAT)
  @Category(UnitTests.class)
  public void getExecutionUnitsTestBasicAndCanarySetupUnsuccessful() {
    // Given
    ElastigroupRollbackTaskParameters params = ElastigroupRollbackTaskParameters.builder().blueGreen(false).build();

    // When
    Collection<String> result = elastigroupRollbackStepHelper.getExecutionUnits(params);

    // Then
    assertThat(result).isNotNull().containsExactly("Delete new Elastigroup");
  }

  @Test
  @Owner(developers = {FILIP})
  @Category(UnitTests.class)
  public void handleTaskFailureTest() throws Exception {
    // Given
    Exception exception = new Exception("Exception message");

    // When
    StepResponse result = elastigroupRollbackStepHelper.handleTaskFailure(null, null, exception);

    // Then
    assertThat(result).isNotNull().extracting(StepResponse::getStatus).isEqualTo(Status.FAILED);

    assertThat(result.getFailureInfo())
        .isNotNull()
        .extracting(FailureInfo::getErrorMessage, FailureInfo::getFailureTypesList)
        .containsExactly("Exception: Exception message", singletonList(FailureType.APPLICATION_FAILURE));
  }

  @Test
  @Owner(developers = {FILIP})
  @Category(UnitTests.class)
  public void handleTaskResultTest() throws Exception {
    // Given
    ElastigroupRollbackTaskResponse response = ElastigroupRollbackTaskResponse.builder()
                                                   .status(CommandExecutionStatus.FAILURE)
                                                   .errorMessage("Error msg")
                                                   .build();

    // When
    StepResponse result = elastigroupRollbackStepHelper.handleTaskResult(null, null, response);

    // Then
    assertThat(result).isNotNull().extracting(StepResponse::getStatus).isEqualTo(Status.FAILED);

    assertThat(result.getFailureInfo()).isNotNull();

    assertThat(result.getFailureInfo().getFailureData(0))
        .isNotNull()
        .extracting(FailureData::getMessage, FailureData::getFailureTypesList, FailureData::getLevel)
        .containsExactly("Error msg", singletonList(FailureType.APPLICATION_FAILURE), Level.ERROR.name());
  }
}
