/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer.kryo;

import io.harness.plancreator.steps.http.HttpStepInfo;
import io.harness.serializer.KryoRegistrar;
import io.harness.steps.approval.step.custom.CustomApprovalOutcome;
import io.harness.steps.approval.step.custom.CustomApprovalStepInfo;
import io.harness.steps.approval.step.harness.HarnessApprovalOutcome;
import io.harness.steps.approval.step.jira.JiraApprovalOutcome;
import io.harness.steps.barriers.BarrierSpecParameters;
import io.harness.steps.barriers.beans.BarrierOutcome;
import io.harness.steps.cf.FlagConfigurationStepParameters;
import io.harness.steps.http.HttpOutcome;
import io.harness.steps.jira.JiraIssueOutcome;
import io.harness.steps.resourcerestraint.ResourceRestraintSpecParameters;
import io.harness.steps.resourcerestraint.beans.AcquireMode;
import io.harness.steps.resourcerestraint.beans.HoldingScope;
import io.harness.steps.resourcerestraint.beans.ResourceRestraintOutcome;
import io.harness.steps.shellscript.ShellScriptStepInfo;

import com.esotericsoftware.kryo.Kryo;

public class OrchestrationStepsContractKryoRegistrar implements KryoRegistrar {
  @Override
  public void register(Kryo kryo) {
    // moved from src layer to contracts
    kryo.register(BarrierOutcome.class, 3203);
    kryo.register(BarrierSpecParameters.class, 3204);
    kryo.register(ResourceRestraintSpecParameters.class, 3206);
    kryo.register(ResourceRestraintOutcome.class, 3207);
    kryo.register(AcquireMode.class, 3208);
    kryo.register(HoldingScope.class, 3210);

    kryo.register(HarnessApprovalOutcome.class, 3221);
    kryo.register(JiraApprovalOutcome.class, 3224);
    kryo.register(JiraIssueOutcome.class, 3225);
    kryo.register(FlagConfigurationStepParameters.class, 3226);
    kryo.register(CustomApprovalStepInfo.class, 3229);
    kryo.register(CustomApprovalOutcome.class, 3231);

    // made it same as which was in CD
    kryo.register(HttpStepInfo.class, 8048);
    kryo.register(HttpOutcome.class, 12501);
    kryo.register(ShellScriptStepInfo.class, 8055);
  }
}
