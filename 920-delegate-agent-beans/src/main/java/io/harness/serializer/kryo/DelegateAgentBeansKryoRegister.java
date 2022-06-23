/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.serializer.kryo;

import io.harness.beans.DelegateHeartbeatResponseStreaming;
import io.harness.delegate.beans.DelegateConnectionHeartbeat;
import io.harness.delegate.beans.DelegateParams;
import io.harness.delegate.beans.DelegateProfileParams;
import io.harness.delegate.beans.DelegateRegisterResponse;
import io.harness.delegate.beans.DelegateTaskAbortEvent;
import io.harness.delegate.beans.DelegateTaskEvent;
import io.harness.serializer.KryoRegistrar;

import com.esotericsoftware.kryo.Kryo;

public class DelegateAgentBeansKryoRegister implements KryoRegistrar {
  @Override
  public void register(Kryo kryo) {
    kryo.register(DelegateRegisterResponse.class, 6501);
    kryo.register(DelegateParams.class, 6502);
    kryo.register(DelegateConnectionHeartbeat.class, 6503);
    kryo.register(DelegateProfileParams.class, 6504);
    kryo.register(DelegateTaskEvent.class, 6505);
    kryo.register(DelegateTaskAbortEvent.class, 6506);
    kryo.register(DelegateHeartbeatResponseStreaming.class, 6507);
  }
}
