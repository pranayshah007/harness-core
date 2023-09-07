/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.serializer.kryo;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.stepDetail.NodeExecutionsInfo;
import io.harness.execution.NodeExecution;
import io.harness.interrupts.InterruptEffect;
import io.harness.plan.NodeType;
import io.harness.pms.data.stepdetails.PmsStepDetails;
import io.harness.pms.data.stepparameters.PmsStepParameters;
import io.harness.serializer.KryoRegistrar;

import com.esotericsoftware.kryo.Kryo;

/**
 * We are trying to remain as independent from Kryo as possible.
 * All the classes which get saved inside DelegateResponseData need to be registered as our
 * WaitNotify engine used that.
 */
@OwnedBy(CDC)
public class OrchestrationBeansContractKryoRegistrar implements KryoRegistrar {
  @Override
  public void register(Kryo kryo) {
    kryo.register(NodeExecution.class, 2506);
    kryo.register(InterruptEffect.class, 2534);
    kryo.register(NodeExecutionsInfo.class, 87604);
    kryo.register(PmsStepParameters.class, 88405);
    kryo.register(PmsStepDetails.class, 88406);
    kryo.register(NodeType.class, 88409);
  }
}
