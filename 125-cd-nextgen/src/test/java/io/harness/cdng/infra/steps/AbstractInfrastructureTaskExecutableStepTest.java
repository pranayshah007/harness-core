/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.infra.steps;

import static io.harness.rule.OwnerRule.VINIT_KUMAR;

import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.infra.yaml.AwsSamInfrastructure;
import io.harness.cdng.infra.yaml.AzureWebAppInfrastructure;
import io.harness.cdng.infra.yaml.EcsInfrastructure;
import io.harness.cdng.infra.yaml.GoogleFunctionsInfrastructure;
import io.harness.cdng.infra.yaml.SshWinRmAzureInfrastructure;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class AbstractInfrastructureTaskExecutableStepTest extends CategoryTest {
  @Mock private InfrastructureStepHelper infrastructureStepHelper;
  @InjectMocks
  private AbstractInfrastructureTaskExecutableStep abstractInfrastructureTaskExecutableStep =
      Mockito.mock(AbstractInfrastructureTaskExecutableStep.class, Mockito.CALLS_REAL_METHODS);
  private AutoCloseable mocks;
  @Before
  public void setUp() throws Exception {
    this.mocks = MockitoAnnotations.openMocks(this);
  }
  @Test
  @Owner(developers = VINIT_KUMAR)
  @Category(UnitTests.class)
  public void testValidateSshWinRmAzureInfrastructure() {
    SshWinRmAzureInfrastructure infrastructure = SshWinRmAzureInfrastructure.builder()
                                                     .connectorRef(ParameterField.createValueField("ConnectorDisconnectHandler-ref"))
                                                     .subscriptionId(ParameterField.createValueField("subscription-id"))
                                                     .resourceGroup(ParameterField.createValueField("resource-group"))
                                                     .credentialsRef(ParameterField.createValueField("credentials-ref"))
                                                     .build();

    abstractInfrastructureTaskExecutableStep.validateInfrastructure(infrastructure, null, null);

    Map<String, ParameterField<String>> fieldNameValueMap = new HashMap<>();
    fieldNameValueMap.put("connectorRef", infrastructure.getConnectorRef());
    fieldNameValueMap.put("subscriptionId", infrastructure.getSubscriptionId());
    fieldNameValueMap.put("resourceGroup", infrastructure.getResourceGroup());
    fieldNameValueMap.put("credentialsRef", infrastructure.getCredentialsRef());

    // Verify that validateExpression is called with the expected fieldNameValueMap.
    verify(infrastructureStepHelper).validateExpression(fieldNameValueMap);
  }
  @Test
  @Owner(developers = VINIT_KUMAR)
  @Category(UnitTests.class)
  public void testValidateGoogleFunctionInfrastructure() {
    GoogleFunctionsInfrastructure infrastructure = GoogleFunctionsInfrastructure.builder()
                                                       .connectorRef(ParameterField.createValueField("ConnectorDisconnectHandler-ref"))
                                                       .region(ParameterField.createValueField("region"))
                                                       .project(ParameterField.createValueField("project"))
                                                       .build();

    abstractInfrastructureTaskExecutableStep.validateInfrastructure(infrastructure, null, null);

    Map<String, ParameterField<String>> fieldNameValueMap = new HashMap<>();
    fieldNameValueMap.put("connectorRef", infrastructure.getConnectorRef());
    fieldNameValueMap.put("region", infrastructure.getRegion());
    fieldNameValueMap.put("project", infrastructure.getProject());

    // Verify that validateExpression is called with the expected fieldNameValueMap.
    verify(infrastructureStepHelper).validateExpression(fieldNameValueMap);
  }
  @Test
  @Owner(developers = VINIT_KUMAR)
  @Category(UnitTests.class)
  public void testValidateECSInfrastructure() {
    EcsInfrastructure infrastructure = EcsInfrastructure.builder()
                                           .connectorRef(ParameterField.createValueField("ConnectorDisconnectHandler-ref"))
                                           .region(ParameterField.createValueField("region"))
                                           .cluster(ParameterField.createValueField("cluster"))
                                           .build();

    abstractInfrastructureTaskExecutableStep.validateInfrastructure(infrastructure, null, null);

    Map<String, ParameterField<String>> fieldNameValueMap = new HashMap<>();
    fieldNameValueMap.put("connectorRef", infrastructure.getConnectorRef());
    fieldNameValueMap.put("region", infrastructure.getRegion());
    fieldNameValueMap.put("cluster", infrastructure.getCluster());

    // Verify that validateExpression is called with the expected fieldNameValueMap.
    verify(infrastructureStepHelper).validateExpression(fieldNameValueMap);
  }
  @Test
  @Owner(developers = VINIT_KUMAR)
  @Category(UnitTests.class)
  public void testValidateAzureWebAppInfrastructure() {
    // Create a mock AzureWebAppInfrastructure
    AzureWebAppInfrastructure infrastructure = AzureWebAppInfrastructure.builder()
                                                   .connectorRef(ParameterField.createValueField("ConnectorDisconnectHandler-ref"))
                                                   .subscriptionId(ParameterField.createValueField("subscription-id"))
                                                   .resourceGroup(ParameterField.createValueField("resource-group"))
                                                   .build();
    abstractInfrastructureTaskExecutableStep.validateInfrastructure(infrastructure, null, null);

    Map<String, ParameterField<String>> fieldNameValueMap = new HashMap<>();
    fieldNameValueMap.put("connectorRef", infrastructure.getConnectorRef());
    fieldNameValueMap.put("subscriptionId", infrastructure.getSubscriptionId());
    fieldNameValueMap.put("resourceGroup", infrastructure.getResourceGroup());

    verify(infrastructureStepHelper).validateExpression(fieldNameValueMap);
  }
  @Test
  @Owner(developers = VINIT_KUMAR)
  @Category(UnitTests.class)
  public void testValidateAWS_SAMInfrastructure() {
    // Create a mock AzureWebAppInfrastructure
    AwsSamInfrastructure infrastructure = AwsSamInfrastructure.builder()
                                              .connectorRef(ParameterField.createValueField("ConnectorDisconnectHandler-ref"))
                                              .region(ParameterField.createValueField("region"))
                                              .build();
    abstractInfrastructureTaskExecutableStep.validateInfrastructure(infrastructure, null, null);

    Map<String, ParameterField<String>> fieldNameValueMap = new HashMap<>();
    fieldNameValueMap.put("connectorRef", infrastructure.getConnectorRef());
    fieldNameValueMap.put("region", infrastructure.getRegion());

    verify(infrastructureStepHelper).validateExpression(fieldNameValueMap);
  }
  @Test
  @Owner(developers = VINIT_KUMAR)
  @Category(UnitTests.class)
  public void testValidateAWS_SAMInfrastructureWithFielsAsNull() {
    AwsSamInfrastructure infrastructureWithNullFields =
        AwsSamInfrastructure.builder().connectorRef(null).region(ParameterField.createValueField("region")).build();
    abstractInfrastructureTaskExecutableStep.validateInfrastructure(infrastructureWithNullFields, null, null);
    Map<String, ParameterField<String>> fieldNameValueMapWithNull = new HashMap<>();
    fieldNameValueMapWithNull.put("connectorRef", null); // Null field
    fieldNameValueMapWithNull.put("region", infrastructureWithNullFields.getRegion());
    verify(infrastructureStepHelper).validateExpression(fieldNameValueMapWithNull);
  }
  @Test
  @Owner(developers = VINIT_KUMAR)
  @Category(UnitTests.class)
  public void testValidateAWS_SAMInfrastructureWithAllFielsAsNull() {
    AwsSamInfrastructure infrastructureWithNullFields =
        AwsSamInfrastructure.builder().connectorRef(null).region(null).build();
    abstractInfrastructureTaskExecutableStep.validateInfrastructure(infrastructureWithNullFields, null, null);
    Map<String, ParameterField<String>> fieldNameValueMapWithNull = new HashMap<>();
    fieldNameValueMapWithNull.put("connectorRef", null); // Null field
    fieldNameValueMapWithNull.put("region", null);
    verify(infrastructureStepHelper).validateExpression(fieldNameValueMapWithNull);
  }
}
