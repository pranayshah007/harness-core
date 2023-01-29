/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.persistence.converters;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;

import com.google.inject.Singleton;
import com.google.protobuf.Message;
import com.google.protobuf.Message.Builder;
import com.google.protobuf.util.JsonFormat;
import com.mongodb.BasicDBObject;
import dev.morphia.converters.SimpleValueConverter;
import dev.morphia.converters.TypeConverter;
import dev.morphia.mapping.MappedField;
import lombok.SneakyThrows;

@SuppressWarnings("unchecked")
@Singleton
@OwnedBy(PIPELINE)
public abstract class ProtoMessageConverter<T extends Message> extends TypeConverter implements SimpleValueConverter {
  public ProtoMessageConverter(Class<T> entityClass) {
    super(entityClass);
  }

  @SneakyThrows
  @Override
  public Object encode(Object value, MappedField optionalExtraInfo) {
    if (value == null) {
      return null;
    }
    Message message = (Message) value;
    String entityJson = JsonFormat.printer().includingDefaultValueFields().print(message);
    return BasicDBObject.parse(entityJson);
  }

  @SneakyThrows
  @Override
  public Object decode(Class<?> targetClass, Object fromDBObject, MappedField optionalExtraInfo) {
    if (fromDBObject == null) {
      return null;
    }
    Builder builder = null;
    builder = (Builder) targetClass.getMethod("newBuilder").invoke(null);
    JsonFormat.parser().ignoringUnknownFields().merge(fromDBObject.toString(), builder);
    return (T) builder.build();
  }
}
