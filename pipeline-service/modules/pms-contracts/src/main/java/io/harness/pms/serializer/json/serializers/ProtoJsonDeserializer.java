/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.serializer.json.serializers;

import io.harness.exception.InternalServerErrorException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import java.io.IOException;
import lombok.SneakyThrows;

public abstract class ProtoJsonDeserializer<T extends Message> extends JsonDeserializer<T> {
  private final JsonFormat.Parser parser;
  private final Message.Builder defaultBuilder;

  @Inject
  public ProtoJsonDeserializer(Class<T> entityClass) {
    this.parser = JsonFormat.parser().ignoringUnknownFields();
    try {
      this.defaultBuilder = (Message.Builder) entityClass.getMethod("newBuilder").invoke(null);
    } catch (Exception e) {
      throw new InternalServerErrorException(
          "Not able to initialise default builder for class - " + entityClass.getName(), e);
    }
  }

  @SneakyThrows
  @Override
  public T deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
    ObjectNode root = p.readValueAsTree();
    Message.Builder builder = defaultBuilder.clone();
    parser.merge(root.toString(), builder);
    return (T) builder.build();
  }
}
