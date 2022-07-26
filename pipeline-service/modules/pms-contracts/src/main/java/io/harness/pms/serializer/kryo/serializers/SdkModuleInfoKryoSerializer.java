/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.serializer.kryo.serializers;

import io.harness.pms.contracts.plan.SdkModuleInfo;
import io.harness.serializer.kryo.ProtobufKryoSerializer;

public class SdkModuleInfoKryoSerializer extends ProtobufKryoSerializer<SdkModuleInfo> {
  private static SdkModuleInfoKryoSerializer instance;

  private SdkModuleInfoKryoSerializer() {}

  public static synchronized SdkModuleInfoKryoSerializer getInstance() {
    if (instance == null) {
      instance = new SdkModuleInfoKryoSerializer();
    }
    return instance;
  }
}
