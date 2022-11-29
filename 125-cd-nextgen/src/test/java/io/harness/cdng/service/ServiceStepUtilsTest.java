/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.service;

import static io.harness.pms.yaml.ParameterField.createValueField;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.service.beans.ServiceConfig;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.cdng.service.beans.ServiceYaml;
import io.harness.cdng.service.steps.ServiceStepParameters;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ServiceStepUtilsTest extends CategoryTest {
  @Mock ServiceEntityService serviceEntityService;
  private static final Ambiance ambiance = Ambiance.newBuilder().putAllSetupAbstractions(setupAbstractions()).build();
  ;

  AutoCloseable mocks;
  @Before
  public void before() {
    mocks = MockitoAnnotations.openMocks(this);
  }

  @After
  public void closeMocks() throws Exception {
    mocks.close();
  }
  private static final String IDENTIFIER = "SVC_NAME";
  private static final String SERVICE_DESCRIPTION = "SERVICE_DESCRIPTION";
  private static final String ACCOUNT_ID = "ACCOUNT_ID";
  private static final String ORG_ID = "ORG_ID";
  private static final String PROJECT_ID = "PROJECT_ID";
  private static final ParameterField<String> description = createValueField(SERVICE_DESCRIPTION);
  private static final ServiceConfig serviceConfig = ServiceConfig.builder()
                                                         .service(ServiceYaml.builder()
                                                                      .name(IDENTIFIER)
                                                                      .identifier(IDENTIFIER)
                                                                      .description(description)
                                                                      .tags(Collections.singletonMap("a", "b"))
                                                                      .build())
                                                         .build();

  private static ServiceEntity serviceEntity = ServiceEntity.builder()
                                                   .name(IDENTIFIER)
                                                   .accountId(ACCOUNT_ID)
                                                   .identifier(IDENTIFIER)
                                                   .projectIdentifier(PROJECT_ID)
                                                   .orgIdentifier(ORG_ID)
                                                   .type(ServiceDefinitionType.KUBERNETES)
                                                   .build();

  @Test
  @Owner(developers = OwnerRule.TATHAGAT)
  @Category(UnitTests.class)
  public void testGetServiceEntityExistingServiceWithYaml() {
    String serviceV2Yaml = readFile("service/serviceV2WithVarAndManifests.yaml");
    String expectedServiceYaml = readFile("service/svcV2YamlPostPipelineExecution.yaml");
    ParameterField<ServiceConfig> serviceConfigParameterField = createValueField(serviceConfig);

    ServiceStepParameters stepParameters = ServiceStepParameters.builder()
                                               .name(IDENTIFIER)
                                               .description(description)
                                               .identifier(IDENTIFIER)
                                               .tags(Collections.singletonMap("a", "b"))
                                               .serviceConfigInternal(serviceConfigParameterField)
                                               .build();

    serviceEntity.setDescription("random");
    serviceEntity.setTags(Collections.singletonList(NGTag.builder().key("random_key").value("random_val").build()));
    serviceEntity.setYaml(serviceV2Yaml);

    doReturn(Optional.of(serviceEntity))
        .when(serviceEntityService)
        .get(anyString(), anyString(), anyString(), anyString(), anyBoolean());

    ServiceEntity outputServiceEntity =
        ServiceStepUtils.getServiceEntity(serviceEntityService, ambiance, stepParameters);
    assertThat(outputServiceEntity).isNotNull();
    assertThat(outputServiceEntity.getYaml()).isNotBlank();
    assertThat(outputServiceEntity.getYaml()).isEqualTo(expectedServiceYaml);
    assertThat(outputServiceEntity.getTags()).isNotEmpty();
    assertThat(outputServiceEntity.getTags()).containsExactlyInAnyOrder(NGTag.builder().key("a").value("b").build());
    assertThat(outputServiceEntity.getDescription()).isEqualTo(description.getValue());
    assertThat(outputServiceEntity.getName()).isEqualTo(IDENTIFIER);

    unsetFieldsInService();
  }

  @Test
  @Owner(developers = OwnerRule.TATHAGAT)
  @Category(UnitTests.class)
  public void testGetServiceEntityExistingServiceWithoutYaml() {
    String expectedServiceYaml = readFile("service/basicSvcV2YamlPostPipelineExecution.yaml");
    ParameterField<ServiceConfig> serviceConfigParameterField = createValueField(serviceConfig);

    ServiceStepParameters stepParameters = ServiceStepParameters.builder()
                                               .name(IDENTIFIER)
                                               .description(description)
                                               .identifier(IDENTIFIER)
                                               .tags(Collections.singletonMap("a", "b"))
                                               .serviceConfigInternal(serviceConfigParameterField)
                                               .build();

    serviceEntity.setDescription("random");

    doReturn(Optional.of(serviceEntity))
        .when(serviceEntityService)
        .get(anyString(), anyString(), anyString(), anyString(), anyBoolean());

    ServiceEntity outputServiceEntity =
        ServiceStepUtils.getServiceEntity(serviceEntityService, ambiance, stepParameters);
    assertThat(outputServiceEntity).isNotNull();
    assertThat(outputServiceEntity.getYaml()).isNotBlank();
    assertThat(outputServiceEntity.getYaml()).isEqualTo(expectedServiceYaml);
    assertThat(outputServiceEntity.getTags()).isNotEmpty();
    assertThat(outputServiceEntity.getTags()).containsExactlyInAnyOrder(NGTag.builder().key("a").value("b").build());
    assertThat(outputServiceEntity.getDescription()).isEqualTo(description.getValue());
    assertThat(outputServiceEntity.getName()).isEqualTo(IDENTIFIER);

    unsetFieldsInService();
  }

  @Test
  @Owner(developers = OwnerRule.TATHAGAT)
  @Category(UnitTests.class)
  public void testGetServiceEntityNonExistingService() {
    String expectedServiceYaml = readFile("service/basicSvcV2YamlPostPipelineExecution.yaml");
    ParameterField<ServiceConfig> serviceConfigParameterField = createValueField(serviceConfig);

    ServiceStepParameters stepParameters = ServiceStepParameters.builder()
                                               .name(IDENTIFIER)
                                               .description(description)
                                               .identifier(IDENTIFIER)
                                               .tags(Collections.singletonMap("a", "b"))
                                               .serviceConfigInternal(serviceConfigParameterField)
                                               .build();

    doReturn(Optional.empty())
        .when(serviceEntityService)
        .get(anyString(), anyString(), anyString(), anyString(), anyBoolean());

    ServiceEntity outputServiceEntity =
        ServiceStepUtils.getServiceEntity(serviceEntityService, ambiance, stepParameters);
    assertThat(outputServiceEntity).isNotNull();
    assertThat(outputServiceEntity.getYaml()).isNotBlank();
    assertThat(outputServiceEntity.getYaml()).isEqualTo(expectedServiceYaml);
    assertThat(outputServiceEntity.getTags()).isNotEmpty();
    assertThat(outputServiceEntity.getTags()).containsExactlyInAnyOrder(NGTag.builder().key("a").value("b").build());
    assertThat(outputServiceEntity.getDescription()).isEqualTo(description.getValue());
    assertThat(outputServiceEntity.getName()).isEqualTo(IDENTIFIER);
  }

  @Test
  @Owner(developers = OwnerRule.TATHAGAT)
  @Category(UnitTests.class)
  public void testGetServiceEntityExistingWithServiceRefAndYaml() {
    String serviceV2Yaml = readFile("service/serviceV2WithVarAndManifests.yaml");
    ParameterField<String> serviceRefInternal = createValueField(IDENTIFIER);

    ServiceStepParameters stepParameters =
        ServiceStepParameters.builder().serviceRefInternal(serviceRefInternal).build();

    serviceEntity.setDescription("random");
    serviceEntity.setTags(Collections.singletonList(NGTag.builder().key("random_key").value("random_val").build()));
    serviceEntity.setYaml(serviceV2Yaml);

    doReturn(Optional.of(serviceEntity))
        .when(serviceEntityService)
        .get(anyString(), anyString(), anyString(), anyString(), anyBoolean());

    ServiceEntity outputServiceEntity =
        ServiceStepUtils.getServiceEntity(serviceEntityService, ambiance, stepParameters);

    assertThat(outputServiceEntity).isNotNull();
    assertThat(outputServiceEntity).isSameAs(serviceEntity);

    unsetFieldsInService();
  }

  @Test
  @Owner(developers = OwnerRule.TATHAGAT)
  @Category(UnitTests.class)
  public void testGetServiceEntityWithoutYamlExistingWithServiceRef() {
    ParameterField<String> serviceRefInternal = createValueField(IDENTIFIER);
    ServiceStepParameters stepParameters =
        ServiceStepParameters.builder().serviceRefInternal(serviceRefInternal).build();

    serviceEntity.setDescription("random");
    serviceEntity.setTags(Collections.singletonList(NGTag.builder().key("random_key").value("random_val").build()));

    doReturn(Optional.of(serviceEntity))
        .when(serviceEntityService)
        .get(anyString(), anyString(), anyString(), anyString(), anyBoolean());

    ServiceEntity outputServiceEntity =
        ServiceStepUtils.getServiceEntity(serviceEntityService, ambiance, stepParameters);

    assertThat(outputServiceEntity).isNotNull();
    assertThat(outputServiceEntity.getYaml()).isBlank();
    assertThat(outputServiceEntity.getTags()).isNotEmpty();
    assertThat(outputServiceEntity.getTags())
        .containsExactlyInAnyOrder(NGTag.builder().key("random_key").value("random_val").build());
    assertThat(outputServiceEntity.getDescription()).isEqualTo("random");
    assertThat(outputServiceEntity.getName()).isEqualTo(IDENTIFIER);
    unsetFieldsInService();
  }

  @Test
  @Owner(developers = OwnerRule.TATHAGAT)
  @Category(UnitTests.class)
  public void testGetServiceEntityExistingWithServiceRefAndNoSvcInDb() {
    ParameterField<String> serviceRefInternal = createValueField(IDENTIFIER);

    ServiceStepParameters stepParameters =
        ServiceStepParameters.builder().serviceRefInternal(serviceRefInternal).build();

    doReturn(Optional.empty())
        .when(serviceEntityService)
        .get(anyString(), anyString(), anyString(), anyString(), anyBoolean());

    assertThatThrownBy(() -> ServiceStepUtils.getServiceEntity(serviceEntityService, ambiance, stepParameters))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Service with identifier " + IDENTIFIER + " does not exist");
  }

  private String readFile(String filename) {
    ClassLoader classLoader = getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: " + filename);
    }
  }

  private static Map<String, String> setupAbstractions() {
    return ImmutableMap.<String, String>builder()
        .put(SetupAbstractionKeys.accountId, ACCOUNT_ID)
        .put(SetupAbstractionKeys.orgIdentifier, ORG_ID)
        .put(SetupAbstractionKeys.projectIdentifier, PROJECT_ID)
        .build();
  }

  private void unsetFieldsInService() {
    serviceEntity.setDescription(null);
    serviceEntity.setTags(null);
    serviceEntity.setYaml(null);
  }
}
