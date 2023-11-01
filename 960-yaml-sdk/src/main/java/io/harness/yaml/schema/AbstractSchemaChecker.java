/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.yaml.schema;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.packages.HarnessPackages.IO_HARNESS;
import static io.harness.packages.HarnessPackages.SOFTWARE_WINGS;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.validation.OneOfField;
import io.harness.validation.OneOfFields;

import com.google.inject.Singleton;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.reflections.Reflections;

@Slf4j
@Singleton
@OwnedBy(DX)
public class AbstractSchemaChecker {
  public void schemaTests() {
    Reflections reflections = new Reflections(IO_HARNESS, SOFTWARE_WINGS);
    ensureOneOfHasCorrectValues(reflections);
  }

  void ensureOneOfHasCorrectValues(Reflections reflections) {
    final Set<Class<?>> typesAnnotatedWith = reflections.getTypesAnnotatedWith(OneOfField.class, true);
    for (Class<?> clazz : typesAnnotatedWith) {
      final OneOfField annotation = clazz.getAnnotation(OneOfField.class);
      validateForOneOfFields(clazz, annotation);
    }
    final Set<Class<?>> typesAnnotated = reflections.getTypesAnnotatedWith(OneOfFields.class, true);
    for (Class<?> clazz : typesAnnotated) {
      final OneOfFields annotation = clazz.getAnnotation(OneOfFields.class);
      final OneOfField[] value = annotation.value();
      for (OneOfField oneOfField : value) {
        validateForOneOfFields(clazz, oneOfField);
      }
    }
  }

  void validateForOneOfFields(Class<?> clazz, OneOfField annotation) {
    final String[] fields = annotation.fields();
    final Field[] declaredFieldsInClass = clazz.getDeclaredFields();
    final Set<String> decFieldSwaggerName =
        Arrays.stream(declaredFieldsInClass).map(Field::getName).collect(Collectors.toSet());
    for (String field : fields) {
      if (!decFieldSwaggerName.contains(field)) {
        throw new InvalidRequestException(String.format("Field %s has incorrect Name", field));
      }
      log.info("One of field passed for field {}", field);
    }
  }
}