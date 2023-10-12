/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.execution;

import static io.harness.beans.sweepingoutputs.StageInfraDetails.STAGE_INFRA_DETAILS;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.LITE_ENGINE_PORT;
import static io.harness.rule.OwnerRule.HARSH;
import static io.harness.rule.OwnerRule.SHUBHAM;
import static io.harness.rule.OwnerRule.SOUMYAJIT;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.beans.outcomes.LiteEnginePodDetailsOutcome;
import io.harness.beans.sweepingoutputs.ContextElement;
import io.harness.beans.sweepingoutputs.K8StageInfraDetails;
import io.harness.beans.sweepingoutputs.StageDetails;
import io.harness.beans.sweepingoutputs.StageInfraDetails;
import io.harness.beans.sweepingoutputs.VmStageInfraDetails;
import io.harness.category.element.UnitTests;
import io.harness.ci.execution.buildstate.ConnectorUtils;
import io.harness.ci.executionplan.CIExecutionPlanTestHelper;
import io.harness.ci.executionplan.CIExecutionTestBase;
import io.harness.delegate.beans.ci.CICleanupTaskParams;
import io.harness.delegate.beans.ci.k8s.CIK8CleanupTaskParams;
import io.harness.delegate.beans.ci.vm.CIVmInitializeTaskParams;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.groovy.util.Maps;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class StageCleanupUtilityTest extends CIExecutionTestBase {
  @Mock private ConnectorUtils connectorUtils;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputResolver;
  @Mock private OutcomeService outcomeService;

  @Inject private CIExecutionPlanTestHelper ciExecutionPlanTestHelper;

  @InjectMocks private StageCleanupUtility stageCleanupUtility;
  // private VmInfraInfo vmInfraInfo = VmInfraInfo.builder().poolId("test").build();
  private static final CIVmInitializeTaskParams.Type vmInfraInfo = CIVmInitializeTaskParams.Type.VM;

  private Ambiance ambiance = Ambiance.newBuilder()
                                  .putAllSetupAbstractions(Maps.of("accountId", "accountId", "projectIdentifier",
                                      "projectIdentfier", "orgIdentifier", "orgIdentifier"))
                                  .build();
  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  @Ignore("Recreate test object after pms integration")
  public void testHandleEventForRunning() throws IOException {
    when(connectorUtils.getConnectorDetails(any(), any())).thenReturn(ciExecutionPlanTestHelper.getGitConnector());
    when(executionSweepingOutputResolver.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(K8StageInfraDetails.builder()
                                    .podName("podName")
                                    .containerNames(new ArrayList<>())
                                    .infrastructure(ciExecutionPlanTestHelper.getInfrastructure())
                                    .build())
                        .build());

    Pair<CICleanupTaskParams, StageInfraDetails> params = stageCleanupUtility.buildAndfetchCleanUpParameters(ambiance);
    assertThat(params.getLeft()).isNotNull();
    assertThat(params.getRight()).isNotNull();
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void testBuildAndfetchAwsCleanUpParameters() throws IOException {
    String poolId = "test";
    String stageRuntimeId = "test";
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(STAGE_INFRA_DETAILS)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(VmStageInfraDetails.builder().poolId(poolId).infraInfo(vmInfraInfo).build())
                        .build());
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(StageDetails.builder().stageRuntimeID(stageRuntimeId).build())
                        .build());

    Pair<CICleanupTaskParams, StageInfraDetails> params = stageCleanupUtility.buildAndfetchCleanUpParameters(ambiance);

    assertThat(params.getLeft()).isNotNull();
    assertThat(params.getRight()).isNotNull();
    assertEquals(params.getLeft().getType(), CICleanupTaskParams.Type.VM);
  }

  @Test
  @Owner(developers = SOUMYAJIT)
  @Category(UnitTests.class)
  @Ignore("Recreate test object after pms integration")
  public void testHandleEventForRunningLiteEngineCapability() throws IOException {
    String ip = "1.2.3.4";
    when(connectorUtils.getConnectorDetails(any(), any())).thenReturn(ciExecutionPlanTestHelper.getGitConnector());
    when(executionSweepingOutputResolver.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(K8StageInfraDetails.builder()
                                    .podName("podName")
                                    .containerNames(new ArrayList<>())
                                    .infrastructure(ciExecutionPlanTestHelper.getInfrastructure())
                                    .build())
                        .build());
    when(outcomeService.resolve(any(), any())).thenReturn(LiteEnginePodDetailsOutcome.builder().ipAddress(ip).build());

    Pair<CICleanupTaskParams, StageInfraDetails> params = stageCleanupUtility.buildAndfetchCleanUpParameters(ambiance);

    assertThat(params.getLeft()).isNotNull();
    assertThat(params.getRight()).isNotNull();
    assertThat(((CIK8CleanupTaskParams) params.getLeft()).getLiteEngineIP()).isEqualTo(ip);
    assertThat(((CIK8CleanupTaskParams) params.getLeft()).getLiteEnginePort()).isEqualTo(LITE_ENGINE_PORT);
  }
}
