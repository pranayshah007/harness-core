/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.serializer.kryo.serializers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.serializer.kryo.ProtobufKryoSerializer;

@OwnedBy(HarnessTeam.PIPELINE)
public class FailureDataKryoSerializer extends ProtobufKryoSerializer<FailureData> {
  private static FailureDataKryoSerializer instance;

  private FailureDataKryoSerializer() {}

  public static synchronized FailureDataKryoSerializer getInstance() {
    if (instance == null) {
      instance = new FailureDataKryoSerializer();
    }
    return instance;
  }
}
