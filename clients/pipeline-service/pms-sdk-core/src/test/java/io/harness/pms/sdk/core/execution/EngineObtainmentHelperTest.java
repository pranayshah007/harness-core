/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.execution;

import static io.harness.rule.OwnerRule.SAHIL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.refobjects.RefObject;
import io.harness.pms.contracts.refobjects.RefType;
import io.harness.pms.data.OrchestrationRefType;
import io.harness.pms.sdk.core.AmbianceTestUtils;
import io.harness.pms.sdk.core.PmsSdkCoreTestBase;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;
import io.harness.pms.sdk.core.data.Outcome;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.rule.Owner;

import io.fabric8.utils.Lists;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.PIPELINE)
public class EngineObtainmentHelperTest extends PmsSdkCoreTestBase {
  @Mock ExecutionSweepingOutputService executionSweepingOutputService;
  @Mock OutcomeService outcomeService;

  @InjectMocks EngineObtainmentHelper engineObtainmentHelper;

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testObtainInputPackage() {
    RefObject outcome = RefObject.newBuilder()
                            .setKey("outcome")
                            .setName("outcome")
                            .setRefType(RefType.newBuilder().setType(OrchestrationRefType.OUTCOME).build())
                            .build();
    RefObject output = RefObject.newBuilder()
                           .setKey("output")
                           .setName("output")
                           .setRefType(RefType.newBuilder().setType(OrchestrationRefType.SWEEPING_OUTPUT).build())
                           .build();
    List<RefObject> refObjects = Lists.newArrayList(outcome, output);
    Ambiance ambiance = AmbianceTestUtils.buildAmbiance();
    TestStepTransput testStepTransput = TestStepTransput.builder().build();
    when(outcomeService.resolve(ambiance, outcome)).thenReturn(testStepTransput);
    when(executionSweepingOutputService.resolve(ambiance, output)).thenReturn(testStepTransput);
    StepInputPackage stepInputPackage = engineObtainmentHelper.obtainInputPackage(ambiance, refObjects);
    assertThat(stepInputPackage.getInputs().size()).isEqualTo(2);
    assertThat(stepInputPackage.getInputs().get(0).getTransput()).isEqualTo(testStepTransput);
    assertThat(stepInputPackage.getInputs().get(1).getTransput()).isEqualTo(testStepTransput);

    verify(outcomeService).resolve(ambiance, outcome);
    verify(executionSweepingOutputService).resolve(ambiance, output);
  }

  @Data
  @Builder
  @RecasterAlias("io.harness.pms.sdk.core.execution.TestStepTransput")
  private static class TestStepTransput implements Outcome, ExecutionSweepingOutput {}
}
