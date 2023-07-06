/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.logging.serializer.kryo;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.logging.UnitProgress;
import io.harness.serializer.kryo.ProtobufKryoSerializer;

@OwnedBy(HarnessTeam.DEL)
@RecasterAlias("io.harness.logging.UnitProgress")
public class UnitProgressKryoSerializer extends ProtobufKryoSerializer<UnitProgress> {
  private static UnitProgressKryoSerializer instance;

  private UnitProgressKryoSerializer() {}

  public static synchronized UnitProgressKryoSerializer getInstance() {
    if (instance == null) {
      instance = new UnitProgressKryoSerializer();
    }
    return instance;
  }
}
