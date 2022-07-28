/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer.kryo;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.serializer.KryoRegistrar;
import io.harness.steps.approval.step.custom.beans.CustomApprovalResponseData;
import io.harness.steps.approval.step.custom.beans.CustomApprovalTicketNG;
import io.harness.steps.approval.step.harness.HarnessApprovalResponseData;
import io.harness.steps.approval.step.jira.beans.JiraApprovalResponseData;
import io.harness.steps.approval.step.servicenow.beans.ServiceNowApprovalResponseData;
import io.harness.steps.barriers.beans.BarrierExecutionInstance;
import io.harness.steps.barriers.beans.BarrierResponseData;
import io.harness.steps.barriers.beans.BarrierResponseData.BarrierError;
import io.harness.steps.resourcerestraint.beans.ResourceRestraintInstance;
import io.harness.steps.resourcerestraint.beans.ResourceRestraintResponseData;

import com.esotericsoftware.kryo.Kryo;

@OwnedBy(PIPELINE)
public class OrchestrationStepsKryoRegistrar implements KryoRegistrar {
  @Override
  public void register(Kryo kryo) {
    kryo.register(BarrierExecutionInstance.class, 3201);
    kryo.register(BarrierResponseData.class, 3202);
    kryo.register(ResourceRestraintInstance.class, 3205);
    kryo.register(ResourceRestraintResponseData.class, 3209);

    kryo.register(HarnessApprovalResponseData.class, 3220);
    kryo.register(JiraApprovalResponseData.class, 3223);
    kryo.register(BarrierError.class, 3227);
    kryo.register(ServiceNowApprovalResponseData.class, 3228);
    kryo.register(CustomApprovalResponseData.class, 3230);
    kryo.register(CustomApprovalTicketNG.class, 3232);
  }
}
