/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.releasedetails.resources;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.ng.core.infrastructure.InfrastructureKind.KUBERNETES_DIRECT;
import static io.harness.rule.OwnerRule.TARUN_UBA;

import static junit.framework.TestCase.assertEquals;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.dtos.InstanceDTO;
import io.harness.entities.ReleaseDetailsMapping;
import io.harness.entities.releasedetailsinfo.ReleaseDetails;
import io.harness.entities.releasedetailsinfo.ReleaseEnvDetails;
import io.harness.entities.releasedetailsinfo.ReleaseServiceDetails;
import io.harness.rule.Owner;
import io.harness.spec.server.ng.v1.model.BatchReleaseDetailsResponse;
import io.harness.spec.server.ng.v1.model.ReleaseDetailsResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.serializer.HObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import junitparams.JUnitParamsRunner;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

@OwnedBy(CDC)
@RunWith(JUnitParamsRunner.class)
public class K8sReleaseServiceMappingApiUtilsTest extends CategoryTest {
  private ObjectMapper objectMapper;
  private Validator validator;
  String ACCOUNT_ID = randomAlphabetic(10);
  String ORG_ID = randomAlphabetic(10);
  String PROJECT_ID = randomAlphabetic(10);
  String RELEASE_KEY = "release_namespace";

  String INFRA_KEY = "infra_key";
  String CONNECTOR_REF = "connector_ref";
  String ENV_ID = "env_id";
  String ENV_NAME = "env_name";
  String ENV_NAME2 = "env_name2";

  String INFRA_NAME = "infra_name";
  String INFRA_ID = "infra_id";
  String SERVICE_ID = "service_id";
  String SERVICE_NAME = "service_name";

  private K8sReleaseServiceMappingApiUtils k8sReleaseServiceMappingApiUtils;

  List<ReleaseDetailsMapping> releaseDetailsMappingList;
  List<InstanceDTO> instanceDTOList;

  @Before
  public void setUp() {
    objectMapper = new ObjectMapper();
    HObjectMapper.configureObjectMapperForNG(objectMapper);

    ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
    k8sReleaseServiceMappingApiUtils = new K8sReleaseServiceMappingApiUtils(validator);

    ReleaseEnvDetails releaseEnvDetails = ReleaseEnvDetails.builder()
                                              .infrastructureKind(KUBERNETES_DIRECT)
                                              .connectorRef(CONNECTOR_REF)
                                              .envId(ENV_ID)
                                              .envName(ENV_NAME)
                                              .infraIdentifier(INFRA_ID)
                                              .infraName(INFRA_NAME)
                                              .orgIdentifier(ORG_ID)
                                              .projectIdentifier(PROJECT_ID)
                                              .build();

    ReleaseEnvDetails releaseEnvDetails2 = ReleaseEnvDetails.builder()
                                               .infrastructureKind(KUBERNETES_DIRECT)
                                               .connectorRef(CONNECTOR_REF)
                                               .envId(ENV_ID)
                                               .envName(ENV_NAME2)
                                               .infraIdentifier(INFRA_ID)
                                               .infraName(INFRA_NAME)
                                               .orgIdentifier(ORG_ID)
                                               .projectIdentifier(PROJECT_ID)
                                               .build();

    ReleaseServiceDetails releaseServiceDetails = ReleaseServiceDetails.builder()
                                                      .serviceId(SERVICE_ID)
                                                      .serviceName(SERVICE_NAME)
                                                      .orgIdentifier(ORG_ID)
                                                      .projectIdentifier(PROJECT_ID)
                                                      .build();
    ReleaseDetails releaseDetails =
        ReleaseDetails.builder().envDetails(releaseEnvDetails).serviceDetails(releaseServiceDetails).build();
    ReleaseDetails releaseDetails2 =
        ReleaseDetails.builder().envDetails(releaseEnvDetails2).serviceDetails(releaseServiceDetails).build();

    ReleaseDetailsMapping releaseDetailsMapping = ReleaseDetailsMapping.builder()
                                                      .accountIdentifier(ACCOUNT_ID)
                                                      .orgIdentifier(ORG_ID)
                                                      .projectIdentifier(PROJECT_ID)
                                                      .releaseKey(RELEASE_KEY)
                                                      .releaseDetails(releaseDetails)
                                                      .infraKey(INFRA_KEY)
                                                      .build();

    ReleaseDetailsMapping releaseDetailsMapping2 = ReleaseDetailsMapping.builder()
                                                       .accountIdentifier(ACCOUNT_ID)
                                                       .orgIdentifier(ORG_ID)
                                                       .projectIdentifier(PROJECT_ID)
                                                       .releaseKey(RELEASE_KEY)
                                                       .releaseDetails(releaseDetails2)
                                                       .infraKey(INFRA_KEY)
                                                       .build();

    releaseDetailsMappingList = new ArrayList<>();
    releaseDetailsMappingList.add(releaseDetailsMapping);
    releaseDetailsMappingList.add(releaseDetailsMapping2);

    instanceDTOList = new ArrayList<>();
    InstanceDTO instanceDTO = InstanceDTO.builder()
                                  .serviceIdentifier(SERVICE_ID)
                                  .serviceName(SERVICE_NAME)
                                  .envIdentifier(ENV_ID)
                                  .envName(ENV_NAME)
                                  .connectorRef(CONNECTOR_REF)
                                  .infraIdentifier(INFRA_ID)
                                  .infrastructureKind(KUBERNETES_DIRECT)
                                  .infraName(INFRA_NAME)
                                  .projectIdentifier(PROJECT_ID)
                                  .orgIdentifier(ORG_ID)
                                  .accountIdentifier(ACCOUNT_ID)
                                  .build();
    instanceDTOList.add(instanceDTO);
    instanceDTOList.add(instanceDTO);
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testReleaseDetailsMapping() {
    BatchReleaseDetailsResponse batchReleaseDetailsResponse =
        k8sReleaseServiceMappingApiUtils.mapToBatchReleaseDetailsResponse(releaseDetailsMappingList);
    Set<ConstraintViolation<Object>> violations = validator.validate(batchReleaseDetailsResponse);
    assertThat(violations).as(violations.toString()).isEmpty();

    assertThat(batchReleaseDetailsResponse).hasSize(2);
    ReleaseDetailsResponse releaseDetailsResponse = batchReleaseDetailsResponse.get(0);

    assertEquals(ACCOUNT_ID, releaseDetailsResponse.getAccount());
    assertEquals(ORG_ID, releaseDetailsResponse.getOrg());
    assertEquals(PROJECT_ID, releaseDetailsResponse.getProject());
    assertEquals(RELEASE_KEY, releaseDetailsResponse.getReleaseKey());

    assertEquals(SERVICE_ID, releaseDetailsResponse.getServiceDetails().getServiceId());
    assertEquals(ORG_ID, releaseDetailsResponse.getServiceDetails().getOrg());
    assertEquals(PROJECT_ID, releaseDetailsResponse.getServiceDetails().getProject());
    assertEquals(SERVICE_NAME, releaseDetailsResponse.getServiceDetails().getServiceName());

    assertEquals(PROJECT_ID, releaseDetailsResponse.getEnvironmentDetails().getProject());
    assertEquals(ORG_ID, releaseDetailsResponse.getEnvironmentDetails().getOrg());
    assertEquals(CONNECTOR_REF, releaseDetailsResponse.getEnvironmentDetails().getConnectorRef());
    assertEquals(ENV_ID, releaseDetailsResponse.getEnvironmentDetails().getEnvId());
    assertEquals(KUBERNETES_DIRECT, releaseDetailsResponse.getEnvironmentDetails().getInfrastructureKind());
    assertEquals(INFRA_ID, releaseDetailsResponse.getEnvironmentDetails().getInfraId());
    assertEquals(INFRA_NAME, releaseDetailsResponse.getEnvironmentDetails().getInfraName());
    assertEquals(ENV_NAME, releaseDetailsResponse.getEnvironmentDetails().getEnvName());
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testReleaseDetailsMappingInCaseWhenDataFromInstanceCollection() {
    BatchReleaseDetailsResponse batchReleaseDetailsResponse =
        k8sReleaseServiceMappingApiUtils.mapInstancesToBatchReleaseDetailsResponse(instanceDTOList, RELEASE_KEY);
    Set<ConstraintViolation<Object>> violations = validator.validate(batchReleaseDetailsResponse);
    assertThat(violations).as(violations.toString()).isEmpty();

    assertThat(batchReleaseDetailsResponse).hasSize(1);
    ReleaseDetailsResponse releaseDetailsResponse = batchReleaseDetailsResponse.get(0);

    assertEquals(ACCOUNT_ID, releaseDetailsResponse.getAccount());
    assertEquals(ORG_ID, releaseDetailsResponse.getOrg());
    assertEquals(PROJECT_ID, releaseDetailsResponse.getProject());
    assertEquals(RELEASE_KEY, releaseDetailsResponse.getReleaseKey());

    assertEquals(SERVICE_ID, releaseDetailsResponse.getServiceDetails().getServiceId());
    assertEquals(SERVICE_NAME, releaseDetailsResponse.getServiceDetails().getServiceName());

    assertEquals(CONNECTOR_REF, releaseDetailsResponse.getEnvironmentDetails().getConnectorRef());
    assertEquals(ENV_ID, releaseDetailsResponse.getEnvironmentDetails().getEnvId());
    assertEquals(KUBERNETES_DIRECT, releaseDetailsResponse.getEnvironmentDetails().getInfrastructureKind());
    assertEquals(INFRA_ID, releaseDetailsResponse.getEnvironmentDetails().getInfraId());
    assertEquals(INFRA_NAME, releaseDetailsResponse.getEnvironmentDetails().getInfraName());
    assertEquals(ENV_NAME, releaseDetailsResponse.getEnvironmentDetails().getEnvName());
  }
}
