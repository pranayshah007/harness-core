/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.stencils;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.serializer.JsonUtils;

import software.wings.beans.artifact.JenkinsArtifactStream;

import com.google.common.collect.ImmutableMap;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Created by peeyushaggarwal on 4/6/17.
 */
@OwnedBy(CDC)
@Slf4j
public class UISchemaProcessor {
  public static <T> Map<String, Object> generate(Class<T> stencilClass) throws IntrospectionException {
    List<String> order = processFieldOrder(stencilClass);
    return ImmutableMap.of("ui:order", order);
  }

  private static <T> List<String> processFieldOrder(Class<T> stencilClass) throws IntrospectionException {
    return Stream
        .concat(fieldStream(stencilClass)
                    .filter(field -> field.getAnnotation(UIOrder.class) != null)
                    .map(field -> Pair.of(field.getName(), field.getAnnotation(UIOrder.class).value())),
            stream(Introspector.getBeanInfo(stencilClass).getPropertyDescriptors())
                .filter(propertyDescriptor -> propertyDescriptor.getReadMethod().getAnnotation(UIOrder.class) != null)
                .map(propertyDescriptor
                    -> Pair.of(propertyDescriptor.getName(),
                        propertyDescriptor.getReadMethod().getAnnotation(UIOrder.class).value())))
        .sorted(Comparator.comparing(Pair::getRight))
        .map(Pair::getLeft)
        .distinct()
        .collect(toList());
  }

  private static Stream<Field> fieldStream(Class<?> klass) {
    if (klass != null && klass != Object.class) {
      return Stream.concat(stream(klass.getDeclaredFields()), fieldStream(klass.getSuperclass()));
    } else {
      return Stream.empty();
    }
  }

  public static void main(String... args) throws IntrospectionException {
    log.info(JsonUtils.asJson(UISchemaProcessor.generate(JenkinsArtifactStream.class)));
  }
}
