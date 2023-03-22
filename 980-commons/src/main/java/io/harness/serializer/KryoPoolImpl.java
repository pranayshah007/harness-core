/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.util.Pool;

@OwnedBy(HarnessTeam.PIPELINE)
public abstract class KryoPoolImpl extends Pool<Kryo> {
  public KryoPoolImpl(boolean threadSafe, boolean softReferences) {
    super(threadSafe, softReferences);
  }

  public <T> T run(KryoCallback<T> callback) {
    Kryo kryo = obtain();
    try {
      return callback.execute(kryo);
    } finally {
      free(kryo);
    }
  }
}
