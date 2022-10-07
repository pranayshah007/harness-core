/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.serializer.kryo;

import io.harness.delegate.NoEligibleDelegatesInAccountException;
import io.harness.delegate.beans.NoAvailableDelegatesException;
import io.harness.delegate.beans.NoDelegatesException;
import io.harness.exception.DelegateTaskExpiredException;
import io.harness.serializer.KryoRegistrar;

import com.esotericsoftware.kryo.Kryo;

public class DelegateServiceContractsKryoRegistrar implements KryoRegistrar {
  @Override
  public void register(Kryo kryo) {
    kryo.register(NoEligibleDelegatesInAccountException.class, 73989);
    kryo.register(NoAvailableDelegatesException.class, 73990);
    kryo.register(NoDelegatesException.class, 73991);
    kryo.register(DelegateTaskExpiredException.class, 980036);
  }
}
