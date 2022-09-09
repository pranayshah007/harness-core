/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.yaml.schema;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.packages.HarnessPackages.IO_HARNESS;
import static io.harness.packages.HarnessPackages.SOFTWARE_WINGS;

import io.harness.EntityType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.validation.OneOfField;
import io.harness.validation.OneOfFields;
import io.harness.yaml.YamlSdkInitConstants;
import io.harness.yaml.schema.beans.YamlSchemaRootClass;
import io.harness.yaml.utils.YamlSchemaUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.inject.Singleton;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.reflections.Reflections;

@Slf4j
@Singleton
@OwnedBy(DX)
public class AbstractSchemaChecker {
  public void schemaTests(List<YamlSchemaRootClass> yamlSchemaRootClasses, ObjectMapper objectMapper)
      throws IOException, ClassNotFoundException {
    Reflections reflections = new Reflections(IO_HARNESS, SOFTWARE_WINGS);
    ensureSchemaUpdated(yamlSchemaRootClasses, objectMapper);
    ensureOneOfHasCorrectValues(reflections);
  }

  void ensureSchemaUpdated(List<YamlSchemaRootClass> yamlSchemaRootClazzes, ObjectMapper objectMapper)
      throws IOException, ClassNotFoundException {
    if (isEmpty(yamlSchemaRootClazzes)) {
      return;
    }
    List<YamlSchemaRootClass> yamlSchemaRootClasses = new ArrayList<>(yamlSchemaRootClazzes);
    Set<YamlSchemaRootClass> toBeRemoved = new HashSet<>();
    for (YamlSchemaRootClass yamlSchemaRootClass : yamlSchemaRootClasses) {
      final String schemaPathForRootCLass = getSchemaPathForRootCLass(yamlSchemaRootClass);
      try {
        IOUtils.resourceToString(schemaPathForRootCLass, StandardCharsets.UTF_8, this.getClass().getClassLoader());
      } catch (Exception e) {
        log.warn("No schema found for unit testing for {}.", yamlSchemaRootClass.getEntityType());
        toBeRemoved.add(yamlSchemaRootClass);
      }
    }
    yamlSchemaRootClasses.removeAll(toBeRemoved);
    YamlSchemaGenerator yamlSchemaGenerator = new YamlSchemaGenerator(
        new JacksonClassHelper(objectMapper), new SwaggerGenerator(objectMapper), yamlSchemaRootClasses);
    final Map<EntityType, JsonNode> entityTypeJsonNodeMap = yamlSchemaGenerator.generateYamlSchema();
    final ObjectWriter objectWriter = yamlSchemaGenerator.getObjectWriter();
    for (YamlSchemaRootClass schemaRoot : yamlSchemaRootClasses) {
      log.info("Running schema check for {}", schemaRoot.getEntityType());
      final String schemaPathForEntityType = getSchemaPathForRootCLass(schemaRoot);
      final JsonNode jsonNode = entityTypeJsonNodeMap.get(schemaRoot.getEntityType());
      final String currentVersionSchema = objectWriter.writeValueAsString(jsonNode);
      final String previousVersionSchema =
          IOUtils.resourceToString(schemaPathForEntityType, StandardCharsets.UTF_8, this.getClass().getClassLoader());
      if (!previousVersionSchema.replaceAll("\\s+", "").equals(currentVersionSchema.replaceAll("\\s+", ""))) {
        log.info("Difference in schema :\n" + StringUtils.difference(currentVersionSchema, previousVersionSchema));
        throw new YamlSchemaException(String.format("Yaml schema not updated for %s", schemaRoot.getEntityType()));
      }
      log.info("schema check success for {}", schemaRoot.getEntityType());
    }
  }

  private String getSchemaPathForRootCLass(YamlSchemaRootClass schemaRoot) {
    final String schemaBasePath = YamlSdkInitConstants.schemaBasePath;
    return YamlSchemaUtils.getSchemaPathForEntityType(schemaRoot.getEntityType(), schemaBasePath);
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
