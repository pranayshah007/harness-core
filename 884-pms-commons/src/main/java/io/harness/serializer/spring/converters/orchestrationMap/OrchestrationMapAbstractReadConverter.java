/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.serializer.spring.converters.orchestrationMap;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.data.OrchestrationMap;
import io.harness.serializer.KryoSerializerWrapper;

import org.bson.types.Binary;
import org.springframework.core.convert.converter.Converter;

@OwnedBy(PIPELINE)
@SuppressWarnings("unchecked")
public abstract class OrchestrationMapAbstractReadConverter<T extends OrchestrationMap>
    implements Converter<Binary, T> {
  private final KryoSerializerWrapper kryoSerializerWrapper;

  public OrchestrationMapAbstractReadConverter(KryoSerializerWrapper kryoSerializerWrapper) {
    this.kryoSerializerWrapper = kryoSerializerWrapper;
  }

  @Override
  public T convert(Binary binary) {
    if (binary.getData() == null) {
      return null;
    }

    return (T) kryoSerializerWrapper.asInflatedObject(binary.getData());
  }
}
