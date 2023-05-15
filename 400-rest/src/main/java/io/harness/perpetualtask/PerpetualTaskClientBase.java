/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.perpetualtask;

import io.harness.serializer.KryoSerializer;

import com.google.inject.Inject;
import com.google.inject.name.Named;

public abstract class PerpetualTaskClientBase {
  @Inject private KryoSerializer kryoSerializer;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;

  public KryoSerializer getKryoSerializer(boolean referenceFalse) {
    return referenceFalse ? referenceFalseKryoSerializer : kryoSerializer;
  }
}
