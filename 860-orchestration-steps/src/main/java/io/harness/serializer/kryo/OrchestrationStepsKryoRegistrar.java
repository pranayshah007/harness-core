/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer.kryo;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.steps.http.HttpStepInfo;
import io.harness.serializer.KryoRegistrar;
import io.harness.steps.approval.step.custom.CustomApprovalOutcome;
import io.harness.steps.approval.step.custom.CustomApprovalStepInfo;
import io.harness.steps.approval.step.custom.beans.CustomApprovalResponseData;
import io.harness.steps.approval.step.custom.beans.CustomApprovalTicketNG;
import io.harness.steps.approval.step.harness.HarnessApprovalOutcome;
import io.harness.steps.approval.step.harness.HarnessApprovalResponseData;
import io.harness.steps.approval.step.jira.JiraApprovalOutcome;
import io.harness.steps.approval.step.jira.beans.JiraApprovalResponseData;
import io.harness.steps.approval.step.servicenow.beans.ServiceNowApprovalResponseData;
import io.harness.steps.barriers.BarrierSpecParameters;
import io.harness.steps.barriers.beans.BarrierExecutionInstance;
import io.harness.steps.barriers.beans.BarrierOutcome;
import io.harness.steps.barriers.beans.BarrierResponseData;
import io.harness.steps.barriers.beans.BarrierResponseData.BarrierError;
import io.harness.steps.cf.FlagConfigurationStepParameters;
import io.harness.steps.http.HttpOutcome;
import io.harness.steps.jira.JiraIssueOutcome;
import io.harness.steps.resourcerestraint.ResourceRestraintSpecParameters;
import io.harness.steps.resourcerestraint.beans.AcquireMode;
import io.harness.steps.resourcerestraint.beans.HoldingScope;
import io.harness.steps.resourcerestraint.beans.ResourceRestraintInstance;
import io.harness.steps.resourcerestraint.beans.ResourceRestraintOutcome;
import io.harness.steps.resourcerestraint.beans.ResourceRestraintResponseData;
import io.harness.steps.shellscript.ShellScriptStepInfo;

import com.esotericsoftware.kryo.Kryo;

@OwnedBy(PIPELINE)
public class OrchestrationStepsKryoRegistrar implements KryoRegistrar {
  @Override
  public void register(Kryo kryo) {
    kryo.register(BarrierExecutionInstance.class, 3201);
    kryo.register(BarrierResponseData.class, 3202);
    kryo.register(BarrierOutcome.class, 3203);
    kryo.register(BarrierSpecParameters.class, 3204);
    kryo.register(ResourceRestraintInstance.class, 3205);
    kryo.register(ResourceRestraintSpecParameters.class, 3206);
    kryo.register(ResourceRestraintOutcome.class, 3207);
    kryo.register(AcquireMode.class, 3208);
    kryo.register(ResourceRestraintResponseData.class, 3209);
    kryo.register(HoldingScope.class, 3210);

    kryo.register(HarnessApprovalResponseData.class, 3220);
    kryo.register(HarnessApprovalOutcome.class, 3221);
    kryo.register(JiraApprovalResponseData.class, 3223);
    kryo.register(JiraApprovalOutcome.class, 3224);
    kryo.register(JiraIssueOutcome.class, 3225);
    kryo.register(FlagConfigurationStepParameters.class, 3226);
    kryo.register(BarrierError.class, 3227);
    kryo.register(ServiceNowApprovalResponseData.class, 3228);
    kryo.register(CustomApprovalStepInfo.class, 3229);
    kryo.register(CustomApprovalResponseData.class, 3230);
    kryo.register(CustomApprovalOutcome.class, 3231);
    kryo.register(CustomApprovalTicketNG.class, 3232);

    // made it same as which was in CD
    kryo.register(HttpStepInfo.class, 8048);
    kryo.register(HttpOutcome.class, 12501);
    kryo.register(ShellScriptStepInfo.class, 8055);
  }
}
