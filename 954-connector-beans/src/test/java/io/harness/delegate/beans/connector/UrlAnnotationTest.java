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
import io.harness.delegate.beans.connector.appdynamicsconnector.AppDynamicsConnectorDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.connector.customhealthconnector.CustomHealthConnectorDTO;
import io.harness.delegate.beans.connector.datadog.DatadogConnectorDTO;
import io.harness.delegate.beans.connector.docker.DockerConnectorDTO;
import io.harness.delegate.beans.connector.helm.HttpHelmConnectorDTO;
import io.harness.delegate.beans.connector.jenkins.JenkinsConnectorDTO;
import io.harness.delegate.beans.connector.jira.JiraConnectorDTO;
import io.harness.delegate.beans.connector.newrelic.NewRelicConnectorDTO;
import io.harness.delegate.beans.connector.nexusconnector.NexusConnectorDTO;
import io.harness.delegate.beans.connector.prometheusconnector.PrometheusConnectorDTO;
import io.harness.delegate.beans.connector.servicenow.ServiceNowConnectorDTO;
import io.harness.delegate.beans.connector.signalfxconnector.SignalFXConnectorDTO;
import io.harness.delegate.beans.connector.splunkconnector.SplunkConnectorDTO;
import io.harness.delegate.beans.connector.sumologic.SumoLogicConnectorDTO;
import io.harness.delegate.beans.connector.vaultconnector.VaultConnectorDTO;
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
  Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  @Owner(developers = SHREYAS)
  @Category(UnitTests.class)
  public void test_SumoLogicConnectorDTO_url()
      throws NoSuchFieldException, InvocationTargetException, NoSuchMethodException, IllegalAccessException {
    Class<? extends ConnectorConfigDTO> clazz = SumoLogicConnectorDTO.class;
    Field field = clazz.getDeclaredField("url");
    testClassField(clazz, field);
  }

  @Test
  @Owner(developers = SHREYAS)
  @Category(UnitTests.class)
  public void test_DynatraceConnectorDTO_url()
      throws NoSuchFieldException, InvocationTargetException, NoSuchMethodException, IllegalAccessException {
    Class<? extends ConnectorConfigDTO> clazz = HttpHelmConnectorDTO.class;
    Field field = clazz.getDeclaredField("helmRepoUrl");
    testClassField(clazz, field);
  }

  @Test
  @Owner(developers = SHREYAS)
  @Category(UnitTests.class)
  public void test_ArtifactoryConnectorDTO_url()
      throws NoSuchFieldException, InvocationTargetException, NoSuchMethodException, IllegalAccessException {
    Class<? extends ConnectorConfigDTO> clazz = ArtifactoryConnectorDTO.class;
    Field field = clazz.getDeclaredField("artifactoryServerUrl");
    testClassField(clazz, field);
  }

  @Test
  @Owner(developers = SHREYAS)
  @Category(UnitTests.class)
  public void test_SplunkConnectorDTO_url()
      throws NoSuchFieldException, InvocationTargetException, NoSuchMethodException, IllegalAccessException {
    Class<? extends ConnectorConfigDTO> clazz = SplunkConnectorDTO.class;
    Field field = clazz.getDeclaredField("splunkUrl");
    testClassField(clazz, field);
  }

  @Test
  @Owner(developers = SHREYAS)
  @Category(UnitTests.class)
  public void test_NewRelicConnectorDTO_url()
      throws NoSuchFieldException, InvocationTargetException, NoSuchMethodException, IllegalAccessException {
    Class<? extends ConnectorConfigDTO> clazz = NewRelicConnectorDTO.class;
    Field field = clazz.getDeclaredField("url");
    testClassField(clazz, field);
  }

  @Test
  @Owner(developers = SHREYAS)
  @Category(UnitTests.class)
  public void test_ServiceNowConnectorDTO_url()
      throws NoSuchFieldException, InvocationTargetException, NoSuchMethodException, IllegalAccessException {
    Class<? extends ConnectorConfigDTO> clazz = ServiceNowConnectorDTO.class;
    Field field = clazz.getDeclaredField("serviceNowUrl");
    testClassField(clazz, field);
  }

  @Test
  @Owner(developers = SHREYAS)
  @Category(UnitTests.class)
  public void test_VaultConnectorDTO_url()
      throws NoSuchFieldException, InvocationTargetException, NoSuchMethodException, IllegalAccessException {
    Class<? extends ConnectorConfigDTO> clazz = VaultConnectorDTO.class;
    Field field = clazz.getDeclaredField("vaultUrl");
    testClassField(clazz, field);
  }

  @Test
  @Owner(developers = SHREYAS)
  @Category(UnitTests.class)
  public void test_DockerConnectorDTO_url()
      throws NoSuchFieldException, InvocationTargetException, NoSuchMethodException, IllegalAccessException {
    Class<? extends ConnectorConfigDTO> clazz = DockerConnectorDTO.class;
    Field field = clazz.getDeclaredField("dockerRegistryUrl");
    testClassField(clazz, field);
  }

  @Test
  @Owner(developers = SHREYAS)
  @Category(UnitTests.class)
  public void test_DatadogConnectorDTO_url()
      throws NoSuchFieldException, InvocationTargetException, NoSuchMethodException, IllegalAccessException {
    Class<? extends ConnectorConfigDTO> clazz = DatadogConnectorDTO.class;
    Field field = clazz.getDeclaredField("url");
    testClassField(clazz, field);
  }

  @Test
  @Owner(developers = SHREYAS)
  @Category(UnitTests.class)
  public void test_CustomHealthConnectorDTO_url()
      throws NoSuchFieldException, InvocationTargetException, NoSuchMethodException, IllegalAccessException {
    Class<? extends ConnectorConfigDTO> clazz = CustomHealthConnectorDTO.class;
    Field field = clazz.getDeclaredField("baseURL");
    testClassField(clazz, field);
  }

  @Test
  @Owner(developers = SHREYAS)
  @Category(UnitTests.class)
  public void test_JiraConnectorDTO_url()
      throws NoSuchFieldException, InvocationTargetException, NoSuchMethodException, IllegalAccessException {
    Class<? extends ConnectorConfigDTO> clazz = JiraConnectorDTO.class;
    Field field = clazz.getDeclaredField("jiraUrl");
    testClassField(clazz, field);
  }

  @Test
  @Owner(developers = SHREYAS)
  @Category(UnitTests.class)
  public void test_AppDynamicsConnectorDTO_url()
      throws NoSuchFieldException, InvocationTargetException, NoSuchMethodException, IllegalAccessException {
    Class<? extends ConnectorConfigDTO> clazz = AppDynamicsConnectorDTO.class;
    Field field = clazz.getDeclaredField("controllerUrl");
    testClassField(clazz, field);
  }

  @Test
  @Owner(developers = SHREYAS)
  @Category(UnitTests.class)
  public void test_NexusConnectorDTO_url()
      throws NoSuchFieldException, InvocationTargetException, NoSuchMethodException, IllegalAccessException {
    Class<? extends ConnectorConfigDTO> clazz = NexusConnectorDTO.class;
    Field field = clazz.getDeclaredField("nexusServerUrl");
    testClassField(clazz, field);
  }

  @Test
  @Owner(developers = SHREYAS)
  @Category(UnitTests.class)
  public void test_JenkinsConnectorDTO_url()
      throws NoSuchFieldException, InvocationTargetException, NoSuchMethodException, IllegalAccessException {
    Class<? extends ConnectorConfigDTO> clazz = JenkinsConnectorDTO.class;
    Field field = clazz.getDeclaredField("jenkinsUrl");
    testClassField(clazz, field);
  }

  @Test
  @Owner(developers = SHREYAS)
  @Category(UnitTests.class)
  public void test_HttpHelmConnectorDTO_url()
      throws NoSuchFieldException, InvocationTargetException, NoSuchMethodException, IllegalAccessException {
    Class<? extends ConnectorConfigDTO> clazz = HttpHelmConnectorDTO.class;
    Field field = clazz.getDeclaredField("helmRepoUrl");
    testClassField(clazz, field);
  }

  @Test
  @Owner(developers = SHREYAS)
  @Category(UnitTests.class)
  public void test_PrometheusConnectorDTO_url()
      throws NoSuchFieldException, InvocationTargetException, NoSuchMethodException, IllegalAccessException {
    Class<? extends ConnectorConfigDTO> clazz = PrometheusConnectorDTO.class;
    Field field = clazz.getDeclaredField("url");
    testClassField(clazz, field);
  }

  @Test
  @Owner(developers = SHREYAS)
  @Category(UnitTests.class)
  public void test_SignalFXConnectorDTO_url()
      throws NoSuchFieldException, InvocationTargetException, NoSuchMethodException, IllegalAccessException {
    Class<? extends ConnectorConfigDTO> clazz = SignalFXConnectorDTO.class;
    Field field = clazz.getDeclaredField("url");
    testClassField(clazz, field);
  }

  public void testClassField(Class<? extends ConnectorConfigDTO> clazz, Field field)
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    assertThat(field.isAnnotationPresent(URL.class)).isTrue();
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
      assertThat(violations.stream().filter(violation -> URL_FAILURE_MSG.equals(violation.getMessage())).count())
          .isEqualTo(1);
    }
  }

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
    assertThat(classUrlFieldPairList.size()).isEqualTo(20);
  }
}
