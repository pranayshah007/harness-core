/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.rule;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.kryo.KryoRegistrar;
import io.harness.waiter.NotifyEventListenerTest;
import io.harness.waiter.TestNotifyCallback;
import io.harness.waiter.TestProgressCallback;
import io.harness.waiter.TestResponseData;
import io.harness.waiter.WaitNotifyEngineTest;

import com.esotericsoftware.kryo.Kryo;

@OwnedBy(HarnessTeam.PIPELINE)
public class WaitEngineTestRegistrar implements KryoRegistrar {
  @Override
  public void register(Kryo kryo) {
    int index = 26 * 10000;
    kryo.register(TestNotifyCallback.class, index++);
    kryo.register(TestProgressCallback.class, index++);
    kryo.register(TestResponseData.class, index++);
    kryo.register(NotifyEventListenerTest.TestNotifyCallback.class, index++);
    kryo.register(NotifyEventListenerTest.TestProgressCallback.class, index++);
    kryo.register(WaitNotifyEngineTest.TestNotifyCallback.class, index++);
    kryo.register(WaitNotifyEngineTest.TestProgressCallback.class, index++);
  }
}
