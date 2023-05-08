/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ARCHIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.interrupts.InterruptPackage;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.advisers.InterventionWaitAdvise;
import io.harness.pms.contracts.commons.RepairActionCode;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class InterventionWaitTimeoutCallbackTest extends OrchestrationTestBase {
  InterventionWaitTimeoutCallback interventionWaitTimeoutCallback;

  private static final String PLAN_EXECUTION_ID = generateUuid();
  private static final String NODE_EXECUTION_ID = generateUuid();

  @Before
  public void setup() {
    interventionWaitTimeoutCallback = new InterventionWaitTimeoutCallback(PLAN_EXECUTION_ID, NODE_EXECUTION_ID);
  }

  @Test

  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void shouldTestGetInterruptPackage() {
    shouldTestGetInterruptPackageInternal(RepairActionCode.IGNORE, InterruptType.IGNORE);
    shouldTestGetInterruptPackageInternal(RepairActionCode.MARK_AS_SUCCESS, InterruptType.MARK_SUCCESS);
    shouldTestGetInterruptPackageInternal(RepairActionCode.RETRY, InterruptType.RETRY);
    shouldTestGetInterruptPackageInternal(RepairActionCode.ON_FAIL, InterruptType.MARK_FAILED);
    shouldTestGetInterruptPackageInternal(RepairActionCode.STAGE_ROLLBACK, InterruptType.CUSTOM_FAILURE);
    shouldTestGetInterruptPackageInternal(RepairActionCode.STEP_GROUP_ROLLBACK, InterruptType.CUSTOM_FAILURE);
    shouldTestGetInterruptPackageInternal(RepairActionCode.CUSTOM_FAILURE, InterruptType.CUSTOM_FAILURE);
    shouldTestGetInterruptPackageInternal(RepairActionCode.UNKNOWN, InterruptType.ABORT_ALL);
    shouldTestGetInterruptPackageInternal(RepairActionCode.END_EXECUTION, InterruptType.ABORT_ALL);
    shouldTestGetInterruptPackageInternal(RepairActionCode.MARK_AS_FAILURE, InterruptType.MARK_FAILED);

    assertThatThrownBy(
        () -> shouldTestGetInterruptPackageInternal(RepairActionCode.MANUAL_INTERVENTION, InterruptType.ABORT_ALL))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(
            "No Execution Type Available for RepairAction Code: " + RepairActionCode.MANUAL_INTERVENTION);
  }

  private void shouldTestGetInterruptPackageInternal(RepairActionCode repairActionCode, InterruptType interruptType) {
    InterventionWaitAdvise advise = InterventionWaitAdvise.newBuilder().setRepairActionCode(repairActionCode).build();
    InterruptPackage interruptPackage = interventionWaitTimeoutCallback.getInterruptPackage(advise, "");
    assertThat(interruptPackage).isNotNull();
    assertThat(interruptPackage.getInterruptType()).isEqualTo(interruptType);
    assertThat(interruptPackage.getNodeExecutionId()).isEqualTo(NODE_EXECUTION_ID);
    assertThat(interruptPackage.getPlanExecutionId()).isEqualTo(PLAN_EXECUTION_ID);
  }
}
