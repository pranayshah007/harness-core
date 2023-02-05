/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.terraformcloud;

import static io.harness.cdng.provision.terraformcloud.TerraformCloudTestStepUtils.TFC_SWEEPING_OUTPUT_IDENTIFIER;
import static io.harness.rule.OwnerRule.BUHA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.provision.terraform.executions.TerraformPlanExectionDetailsService;
import io.harness.cdng.provision.terraformcloud.output.TerraformCloudPlanOutput;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.terraformcloudconnector.TerraformCloudConnectorDTO;
import io.harness.delegate.task.terraformcloud.response.TerraformCloudRunTaskResponse;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.rule.Owner;

import java.io.IOException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.MockitoRule;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(HarnessTeam.CDP)
public class TerraformCloudStepHelperTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private CDStepHelper cdStepHelper;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;
  @Mock TerraformPlanExectionDetailsService terraformPlanExectionDetailsService;

  @Mock TerraformCloudConnectorDTO mockConnectorDTO;

  @InjectMocks private TerraformCloudStepHelper helper;
  private TerraformCloudTestStepUtils utils = new TerraformCloudTestStepUtils();

  @Before
  public void setup() throws IOException {}

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testGenerateFullIdentifier() {
    String provisionerId = "provisionerId";
    String identifier = helper.generateFullIdentifier(provisionerId, utils.getAmbiance());
    assertThat(identifier).isEqualTo("test-account/test-org/test-project/provisionerId");
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testGenerateFullIdentifierWithSpace() {
    String provisionerId = "provisioner id";
    assertThatThrownBy(() -> helper.generateFullIdentifier(provisionerId, utils.getAmbiance()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Provisioner Identifier cannot contain special characters or spaces:");
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testGetTerraformCloudConnectorWhenPlan() {
    when(cdStepHelper.getConnector("tcConnectorRef", utils.getAmbiance()))
        .thenReturn(ConnectorInfoDTO.builder()
                        .connectorType(ConnectorType.TERRAFORM_CLOUD)
                        .connectorConfig(mockConnectorDTO)
                        .identifier("tcConnectorRef")
                        .build());
    TerraformCloudConnectorDTO terraformCloudConnector =
        helper.getTerraformCloudConnector(utils.getPlanSpecParameters(), utils.getAmbiance());
    assertThat(terraformCloudConnector).isEqualTo(mockConnectorDTO);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testGetTerraformCloudConnectorWhenApply() {
    when(executionSweepingOutputService.resolveOptional(
             utils.getAmbiance(), RefObjectUtils.getSweepingOutputRefObject(TFC_SWEEPING_OUTPUT_IDENTIFIER)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(TerraformCloudPlanOutput.builder()
                                    .terraformCloudConnectorRef("connectorPlanRef")
                                    .runId("run-123")
                                    .build())
                        .build());
    when(cdStepHelper.getConnector("connectorPlanRef", utils.getAmbiance()))
        .thenReturn(ConnectorInfoDTO.builder()
                        .connectorType(ConnectorType.TERRAFORM_CLOUD)
                        .connectorConfig(mockConnectorDTO)
                        .identifier("tcConnectorRef")
                        .build());

    TerraformCloudConnectorDTO terraformCloudConnector =
        helper.getTerraformCloudConnector(utils.getApplySpecParameters(), utils.getAmbiance());
    assertThat(terraformCloudConnector).isEqualTo(mockConnectorDTO);
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testSaveTerraformCloudPlanOutput() {
    ArgumentCaptor<TerraformCloudPlanOutput> planOutputArgumentCaptor =
        ArgumentCaptor.forClass(TerraformCloudPlanOutput.class);
    TerraformCloudRunTaskResponse response = TerraformCloudRunTaskResponse.builder().runId("run-123").build();
    helper.saveTerraformCloudPlanOutput(utils.getPlanSpecParameters(), response, utils.getAmbiance());
    verify(executionSweepingOutputService, times(1))
        .consume(any(), eq(TFC_SWEEPING_OUTPUT_IDENTIFIER), planOutputArgumentCaptor.capture(),
            eq(StepOutcomeGroup.STAGE.name()));
    assertThat(planOutputArgumentCaptor.getValue().getTerraformCloudConnectorRef()).isEqualTo("tcConnectorRef");
    assertThat(planOutputArgumentCaptor.getValue().getRunId()).isEqualTo("run-123");
  }

  @Test
  @Owner(developers = BUHA)
  @Category(UnitTests.class)
  public void testGetPlanRunIdFromSweepingOutput() {
    when(executionSweepingOutputService.resolveOptional(
             utils.getAmbiance(), RefObjectUtils.getSweepingOutputRefObject(TFC_SWEEPING_OUTPUT_IDENTIFIER)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(TerraformCloudPlanOutput.builder()
                                    .terraformCloudConnectorRef("connectorPlanRef")
                                    .runId("run-123")
                                    .build())
                        .build());
    String runId = helper.getPlanRunId("provisionerId", utils.getAmbiance());
    assertThat(runId).isEqualTo("run-123");
  }
}
