/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.serializer;

import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.exception.UnexpectedException;
import io.harness.serializer.AnnotationAwareJsonSubtypeResolver;
import io.harness.serializer.jackson.HarnessJacksonModule;

import software.wings.jersey.JsonViews;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hubspot.jackson.datatype.protobuf.ProtobufModule;
import io.dropwizard.jackson.Jackson;
import io.serializer.jackson.NGHarnessJacksonModule;
import java.util.List;
import lombok.experimental.UtilityClass;

@UtilityClass
public class HObjectMapper {
  public static final ObjectMapper NG_DEFAULT_OBJECT_MAPPER = configureObjectMapperForNG(Jackson.newObjectMapper());

  public static ObjectMapper get() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    mapper.configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE, true);
    return mapper;
  }

  public static ObjectMapper configureObjectMapperForNG(ObjectMapper mapper) {
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    mapper.configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE, true);
    AnnotationAwareJsonSubtypeResolver subtypeResolver =
        AnnotationAwareJsonSubtypeResolver.newInstance(mapper.getSubtypeResolver());
    mapper.setSubtypeResolver(subtypeResolver);
    mapper.setConfig(mapper.getSerializationConfig().withView(JsonViews.Public.class));
    mapper.setAnnotationIntrospector(new JacksonAnnotationIntrospector() {
      @Override
      public List<NamedType> findSubtypes(Annotated a) {
        final List<NamedType> subtypesFromSuper = super.findSubtypes(a);
        if (isNotEmpty(subtypesFromSuper)) {
          return subtypesFromSuper;
        }
        return emptyIfNull(subtypeResolver.findSubtypes(a));
      }
    });

    mapper.registerModule(new ProtobufModule());
    mapper.registerModule(new Jdk8Module());
    mapper.registerModule(new GuavaModule());
    mapper.registerModule(new JavaTimeModule());
    mapper.registerModule(new HarnessJacksonModule());
    mapper.registerModule(new NGHarnessJacksonModule());

    return mapper;
  }

  public static Object clone(Object object) {
    try {
      return NG_DEFAULT_OBJECT_MAPPER.readValue(NG_DEFAULT_OBJECT_MAPPER.writeValueAsString(object), object.getClass());
    } catch (Exception exception) {
      throw new UnexpectedException("Exception occurred while copying object.", exception);
    }
  }
}
