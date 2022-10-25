/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer.kryo;

import io.harness.engine.interrupts.steps.TestTransportEntity;
import io.harness.kryo.KryoRegistrar;

import software.wings.expression.MapTestSweepingOutput;
import software.wings.expression.SweepingOutputData;
import software.wings.service.impl.WorkflowExecutionUpdateFake;
import software.wings.sm.CustomExecutionEventAdvisor;
import software.wings.sm.StateMachineExecutionCallbackMock;
import software.wings.sm.TestStateExecutionData;

import com.esotericsoftware.kryo.Kryo;

public class TestManagerKryoRegistrar implements KryoRegistrar {
  @Override
  public void register(Kryo kryo) {
    int index = 71 * 10000;
    kryo.register(StateMachineExecutionCallbackMock.class, index++);
    kryo.register(TestStateExecutionData.class, index++);
    kryo.register(CustomExecutionEventAdvisor.class, index++);
    kryo.register(WorkflowExecutionUpdateFake.class, index++);

    kryo.register(SweepingOutputData.class, index++);

    kryo.register(TestTransportEntity.class, index++);
    kryo.register(MapTestSweepingOutput.class, index++);
  }
}
