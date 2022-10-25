/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.serializer.kryo;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.kryo.KryoRegistrar;
import io.harness.waiter.StringNotifyProgressData;
import io.harness.waiter.StringNotifyResponseData;
import io.harness.waiter.WaitInstanceTimeoutCallback;

import com.esotericsoftware.kryo.Kryo;

@OwnedBy(HarnessTeam.DEL)
public class WaitEngineKryoRegister implements KryoRegistrar {
  @Override
  public void register(Kryo kryo) {
    kryo.register(StringNotifyResponseData.class, 5271);
    kryo.register(StringNotifyProgressData.class, 5700);

    kryo.register(WaitInstanceTimeoutCallback.class, 95002);
  }
}
