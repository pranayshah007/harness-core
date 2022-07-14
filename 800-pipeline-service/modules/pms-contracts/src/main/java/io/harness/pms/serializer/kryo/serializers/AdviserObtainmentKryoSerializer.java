/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.serializer.kryo.serializers;

import io.harness.pms.contracts.advisers.AdviserObtainment;
import io.harness.serializer.kryo.ProtobufKryoSerializer;

public class AdviserObtainmentKryoSerializer extends ProtobufKryoSerializer<AdviserObtainment> {
  private static AdviserObtainmentKryoSerializer instance;

  private AdviserObtainmentKryoSerializer() {}

  public static synchronized AdviserObtainmentKryoSerializer getInstance() {
    if (instance == null) {
      instance = new AdviserObtainmentKryoSerializer();
    }
    return instance;
  }
}
