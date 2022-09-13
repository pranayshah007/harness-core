/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.connector;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.SHREYAS;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import org.apache.commons.math3.util.Pair;
import org.hibernate.validator.constraints.URL;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.scanners.TypeAnnotationsScanner;

@OwnedBy(PL)
public class UrlAnnotationTest {
  public static final String PACKAGE_NAME = "io.harness.delegate.beans.connector";
  public static final String URL_FAILURE_MSG = "must be a valid URL";

  public static final List<String> validURLs = new LinkedList<>(Arrays.asList("http://localhost", "https://localhost",
      "ftp://localhost", "file://localhost", "http://localhost.com", "https://localhost.com", "http://127.0.0.1",
      "https://127.0.0.1", "http://google.com", "https://google.com", "http://shortenedUrl", "https://shortenedUrl/",
      "http://toli:123", "https://app.harness.io", "ftp://abc", "ftp://", "file://abc", "file://"));

  public static final List<String> invalidURLs =
      new LinkedList<>(Arrays.asList("invalidUrl", "app.harness.io", "abc://invalid.com", "invalid.com"));

  @Test
  @Owner(developers = SHREYAS)
  @Category(UnitTests.class)
  public void testUrlAnnotation() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    // Get the validator
    Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    // Create reflections object
    Reflections reflections = new Reflections(PACKAGE_NAME, new SubTypesScanner(false), new TypeAnnotationsScanner());

    // This will store pair of class and url annotated field as a list.
    List<Pair<Class<? extends ConnectorConfigDTO>, Field>> classUrlFieldPairList = new LinkedList<>();
    // Gets subclasses of connector config dto and pair it with url annotated field in them to fill the above list
    reflections.getSubTypesOf(ConnectorConfigDTO.class)
        .forEach(aClass
            -> Arrays.stream(aClass.getDeclaredFields())
                   .filter(field -> field.isAnnotationPresent(URL.class))
                   .forEach(field -> classUrlFieldPairList.add(new Pair<>(aClass, field))));
    // Iterate over each pair, to validate the url annotation.
    for (Pair<Class<? extends ConnectorConfigDTO>, Field> classFieldPair : classUrlFieldPairList) {
      Class<? extends ConnectorConfigDTO> clazz = classFieldPair.getKey();
      Field field = classFieldPair.getValue();
      // Get builder from the class
      Method builderMethod = clazz.getMethod("builder");
      Object builderObj = builderMethod.invoke(null);
      // Get build method from builder object
      Method build = builderObj.getClass().getMethod("build");
      build.setAccessible(true);
      // Create connector config dto object
      ConnectorConfigDTO connectorConfigDTO = (ConnectorConfigDTO) build.invoke(builderObj);
      field.setAccessible(true);
      // Check for valid URL
      for (String validUrl : validURLs) {
        field.set(connectorConfigDTO, validUrl);
        Set<ConstraintViolation<ConnectorConfigDTO>> violations =
            validator.validateProperty(connectorConfigDTO, field.getName());
        assertThat(violations).isEmpty();
      }
      // check for invalid invalid
      for (String invalidUrl : invalidURLs) {
        field.set(connectorConfigDTO, invalidUrl);
        Set<ConstraintViolation<ConnectorConfigDTO>> violations =
            validator.validateProperty(connectorConfigDTO, field.getName());
        assertThat(violations).isNotNull();
        assertThat(violations.size()).isEqualTo(1);
        assertThat(violations.stream().filter((violation) -> URL_FAILURE_MSG.equals(violation.getMessage())).count())
            .isEqualTo(1);
      }
    }
  }
}
