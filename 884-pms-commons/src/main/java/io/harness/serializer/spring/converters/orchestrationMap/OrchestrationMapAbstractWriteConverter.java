/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.serializer.spring.converters.orchestrationMap;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.kryo.KryoSerializer;
import io.harness.pms.data.OrchestrationMap;

import org.bson.types.Binary;
import org.springframework.core.convert.converter.Converter;

@OwnedBy(PIPELINE)
public abstract class OrchestrationMapAbstractWriteConverter<T extends OrchestrationMap>
    implements Converter<T, Binary> {
  private final KryoSerializer kryoSerializer;

  public OrchestrationMapAbstractWriteConverter(KryoSerializer kryoSerializer) {
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
