/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.logging.serializer.kryo;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.kryo.KryoRegistrar;
import io.harness.logging.UnitProgress;
import io.harness.logging.UnitStatus;

import com.esotericsoftware.kryo.Kryo;

@OwnedBy(HarnessTeam.DEL)
public class ApiServiceBeansProtoKryoRegistrar implements KryoRegistrar {
  @Override
  public void register(Kryo kryo) {
    kryo.register(UnitProgress.class, UnitProgressKryoSerializer.getInstance(), 9701);
    kryo.register(UnitStatus.class, 9702);
  }
}
