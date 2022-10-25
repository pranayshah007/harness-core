/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.virtualstack;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.kryo.KryoSerializer;
import io.harness.manage.GlobalContextManager;

import com.google.protobuf.ByteString;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@OwnedBy(HarnessTeam.PL)
@Slf4j
public class VirtualStackUtils {
  public VirtualStackRequest populateRequest(KryoSerializer kryoSerializer) {
    VirtualStackRequest.Builder builder = VirtualStackRequest.newBuilder();
    try {
      builder.setGlobalContext(
          ByteString.copyFrom(kryoSerializer.asDeflatedBytes(GlobalContextManager.obtainGlobalContext())));

    } catch (Exception exception) {
      log.error("Make sure deflating the global object is supported", exception);
    }

    return builder.build();
  }
}
