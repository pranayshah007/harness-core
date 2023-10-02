/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.serializer.spring;

import io.harness.exception.InternalServerErrorException;

import com.google.inject.Inject;
import com.google.protobuf.Message;
import com.google.protobuf.Message.Builder;
import com.google.protobuf.util.JsonFormat;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.core.convert.converter.Converter;

@SuppressWarnings("unchecked")
@Slf4j
public abstract class ProtoReadConverter<T extends Message> implements Converter<Document, T> {
  private final JsonFormat.Parser parser;
  private final Builder defaultBuilder;

  @Inject
  public ProtoReadConverter(Class<T> entityClass) {
    this.parser = JsonFormat.parser().ignoringUnknownFields();
    try {
      this.defaultBuilder = (Builder) entityClass.getMethod("newBuilder").invoke(null);
    } catch (Exception e) {
      throw new InternalServerErrorException(
          "Not able to initialise default builder for class - " + entityClass.getName(), e);
    }
  }

  @SneakyThrows
  @Override
  public T convert(Document dbObject) {
    if (dbObject.isEmpty()) {
      return (T) defaultBuilder.build();
    }
    Builder builder = defaultBuilder.clone();
    parser.merge(dbObject.toJson(), builder);
    return (T) builder.build();
  }
}
