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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.bson.types.Binary;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;

@OwnedBy(PIPELINE)
@Singleton
@WritingConverter
public class OrchestrationMapWriteConverter implements Converter<OrchestrationMap, Binary> {
  @Inject private KryoSerializerWrapper kryoSerializerWrapper;

  @Override
  public Binary convert(OrchestrationMap orchestrationMap) {
    if (orchestrationMap == null) {
      return null;
    }
    return new Binary(kryoSerializerWrapper.asDeflatedBytes(orchestrationMap));
  }
}
