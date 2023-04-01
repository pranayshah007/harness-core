/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.instance.info;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.IVAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.instance.info.InstanceInfoServiceImpl;
import io.harness.cdng.instance.outcome.DeploymentInfoOutcome;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.K8sServerInstanceInfo;
import io.harness.exception.InvalidRequestException;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.rule.Owner;

import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(CDP)
@RunWith(MockitoJUnitRunner.class)
public class InstanceInfoServiceImplTest extends CategoryTest {
  private static final String POD_IP = "podId";
  private static final String POD_NAME = "podName";
  private static final String NAMESPACE = "namespace";
  private static final String RELEASE_NAME = "releaseName";
  private static final String BLUE_GREEN_COLOR = "blueGreenColor";
  private static final String IDENTIFIER = "identifier";
  private static final String SETUP_ID = "setupId";
  @Mock ExecutionSweepingOutputService executionSweepingOutputService;

  @InjectMocks private InstanceInfoServiceImpl instanceInfoService;

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testListServerInstances() {
    when(executionSweepingOutputService.resolveOptional(any(), any())).thenReturn(getDeploymentInfoOutcome(true));

    List<ServerInstanceInfo> serverInstanceInfos =
        instanceInfoService.listServerInstances(getAmbiance(), getStepType());

    assertThat(serverInstanceInfos.size()).isEqualTo(1);
    assertThat(serverInstanceInfos.get(0)).isInstanceOf(K8sServerInstanceInfo.class);
    K8sServerInstanceInfo serverInstanceInfo = (K8sServerInstanceInfo) serverInstanceInfos.get(0);
    assertThat(serverInstanceInfo.getPodIP()).isEqualTo(POD_IP);
    assertThat(serverInstanceInfo.getName()).isEqualTo(POD_NAME);
    assertThat(serverInstanceInfo.getNamespace()).isEqualTo(NAMESPACE);
    assertThat(serverInstanceInfo.getReleaseName()).isEqualTo(RELEASE_NAME);
    assertThat(serverInstanceInfo.getBlueGreenColor()).isEqualTo(BLUE_GREEN_COLOR);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testListServerInstancesWithInvalidRequestException() {
    when(executionSweepingOutputService.resolveOptional(any(), any())).thenReturn(getDeploymentInfoOutcome(false));

    assertThatThrownBy(() -> instanceInfoService.listServerInstances(getAmbiance(), getStepType()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Not found sweeping output for step type: K8sRollingDeploy");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testSaveServerInstancesIntoSweepingOutput() {
    when(executionSweepingOutputService.resolveOptional(any(), any())).thenReturn(getDeploymentInfoOutcome(true));
    Ambiance ambiance = getAmbiance();
    doReturn("sweepingOutput")
        .when(executionSweepingOutputService)
        .consume(
            any(), eq(OutcomeExpressionConstants.DEPLOYMENT_INFO_OUTCOME), any(), eq(StepOutcomeGroup.STEP.name()));

    StepResponse.StepOutcome stepOutcome =
        instanceInfoService.saveServerInstancesIntoSweepingOutput(ambiance, getServiceInstanceInfos());

    assertThat(stepOutcome).isNotNull();
    assertThat(stepOutcome.getOutcome()).isInstanceOf(DeploymentInfoOutcome.class);

    DeploymentInfoOutcome deploymentInfoOutcome = (DeploymentInfoOutcome) stepOutcome.getOutcome();
    List<ServerInstanceInfo> serverInstanceInfoList = deploymentInfoOutcome.getServerInstanceInfoList();
    assertThat(serverInstanceInfoList.size()).isEqualTo(1);
    assertThat(serverInstanceInfoList.get(0)).isInstanceOf(K8sServerInstanceInfo.class);

    K8sServerInstanceInfo serverInstanceInfo = (K8sServerInstanceInfo) serverInstanceInfoList.get(0);
    assertThat(serverInstanceInfo.getPodIP()).isEqualTo(POD_IP);
    assertThat(serverInstanceInfo.getName()).isEqualTo(POD_NAME);
    assertThat(serverInstanceInfo.getNamespace()).isEqualTo(NAMESPACE);
    assertThat(serverInstanceInfo.getReleaseName()).isEqualTo(RELEASE_NAME);
    assertThat(serverInstanceInfo.getBlueGreenColor()).isEqualTo(BLUE_GREEN_COLOR);
  }

  private OptionalSweepingOutput getDeploymentInfoOutcome(boolean found) {
    return OptionalSweepingOutput.builder()
        .found(found)
        .output(DeploymentInfoOutcome.builder().serverInstanceInfoList(getServiceInstanceInfos()).build())
        .build();
  }

  private List<ServerInstanceInfo> getServiceInstanceInfos() {
    return Collections.singletonList(K8sServerInstanceInfo.builder()
                                         .podIP(POD_IP)
                                         .name(POD_NAME)
                                         .namespace(NAMESPACE)
                                         .releaseName(RELEASE_NAME)
                                         .blueGreenColor(BLUE_GREEN_COLOR)
                                         .build());
  }

  private Ambiance getAmbiance() {
    return Ambiance.newBuilder()
        .addLevels(Level.newBuilder().setIdentifier(IDENTIFIER).setSetupId(SETUP_ID).build())
        .build();
  }

  private StepType getStepType() {
    return StepType.newBuilder()
        .setType(ExecutionNodeType.K8S_ROLLING.getYamlType())
        .setStepCategory(StepCategory.STEP)
        .build();
  }
}
