/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.iacm.serializer.kryo;

import io.harness.beans.stages.IACMStageConfigImplV1;
import io.harness.beans.stages.IACMStageNodeV1;
import io.harness.serializer.KryoRegistrar;

import com.esotericsoftware.kryo.Kryo;

public class IACMBeansKryoRegistrar implements KryoRegistrar {
  @Override
  public void register(Kryo kryo) {
    // IACM
    kryo.register(IACMStageNodeV1.class, 200000);
    kryo.register(IACMStageConfigImplV1.class, 200001);
    kryo.register(IACMStageNodeV1.StepType.class, 200002);
  }
}