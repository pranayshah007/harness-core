/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.serializer.json.serializers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import java.io.IOException;

@OwnedBy(PIPELINE)
public class ProtoJsonSerializer<T extends Message> extends StdSerializer<T> {
  public ProtoJsonSerializer(Class<T> t) {
    super(t);
  }
  public ProtoJsonSerializer() {
    this(null);
  }
  @Override
  public void serialize(T entity, JsonGenerator jgen, SerializerProvider serializerProvider) throws IOException {
    jgen.writeRawValue(JsonFormat.printer().includingDefaultValueFields().print(entity));
  }
}
