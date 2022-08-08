/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer.spring;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.serializer.KryoSerializer;

import org.bson.types.Binary;
import org.springframework.core.convert.converter.Converter;

@OwnedBy(HarnessTeam.CDP)
public class AbstractWriteConverter<T extends WriteConverter> implements Converter<T, Binary> {
  private final KryoSerializer kryoSerializer;

  public AbstractWriteConverter(KryoSerializer kryoSerializer) {
    this.kryoSerializer = kryoSerializer;
  }

  @Override
  public Binary convert(T object) {
    if (object == null) {
      return null;
    }

    return new Binary(kryoSerializer.asDeflatedBytes(object));
  }
}
