/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.releasedetails.resources;

import static io.harness.ng.core.infrastructure.InfrastructureKind.KUBERNETES_DIRECT;
import static io.harness.rule.OwnerRule.TARUN_UBA;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.dtos.InstanceDTO;
import io.harness.entities.ReleaseDetailsMapping;
import io.harness.entities.releasedetailsinfo.ReleaseDetails;
import io.harness.entities.releasedetailsinfo.ReleaseEnvDetails;
import io.harness.entities.releasedetailsinfo.ReleaseServiceDetails;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.repositories.releasedetailsmapping.ReleaseDetailsMappingRepository;
import io.harness.rule.Owner;
import io.harness.service.instance.InstanceService;
import io.harness.spec.server.ng.v1.model.BatchReleaseDetailsResponse;
import io.harness.spec.server.ng.v1.model.ReleaseDetailsRequest;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import javax.ws.rs.core.Response;
import org.joor.Reflect;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class K8sReleaseServiceMappingApiImplTest {
  @Inject @InjectMocks K8sReleaseServiceMappingApiImpl k8sReleaseServiceMappingApi;
  @Mock ReleaseDetailsMappingRepository releaseDetailsMappingRepository;

  @Mock InstanceService instanceService;

  @Inject K8sReleaseServiceMappingApiUtils k8sReleaseServiceMappingApiUtils;
  String ACCOUNT_ID = randomAlphabetic(10);
  String ORG_ID = randomAlphabetic(10);
  String PROJECT_ID = randomAlphabetic(10);
  String RELEASE_NAME = "release";
  String NAMESPACE = "namespace";
  String RELEASE_KEY = "release_namespace";

  String INFRA_KEY = "infra_key";
  String CONNECTOR_REF = "connector_ref";
  String ENV_ID = "env_id";
  String ENV_NAME = "env_name";
  String INFRA_NAME = "infra_name";
  String INFRA_ID = "infra_id";
  String SERVICE_ID = "service_id";
  String SERVICE_NAME = "service_name";
  String description = "sample description";
  ServiceEntity entity;
  List<ReleaseDetailsMapping> releaseDetailsMappingList;
  List<InstanceDTO> instanceDTOList;
  List<ReleaseDetailsRequest> releaseDetailsRequests;
  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    Validator validator = factory.getValidator();
    k8sReleaseServiceMappingApiUtils = new K8sReleaseServiceMappingApiUtils(validator);
    Reflect.on(k8sReleaseServiceMappingApi).set("k8sReleaseServiceMappingApiUtils", k8sReleaseServiceMappingApiUtils);
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

    ReleaseServiceDetails releaseServiceDetails = ReleaseServiceDetails.builder()
                                                      .serviceId(SERVICE_ID)
                                                      .serviceName(SERVICE_NAME)
                                                      .orgIdentifier(ORG_ID)
                                                      .projectIdentifier(PROJECT_ID)
                                                      .build();
    ReleaseDetails releaseDetails =
        ReleaseDetails.builder().envDetails(releaseEnvDetails).serviceDetails(releaseServiceDetails).build();

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
                                                       .releaseDetails(releaseDetails)
                                                       .infraKey(INFRA_KEY)
                                                       .build();
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

    instanceDTOList = new ArrayList<>();
    instanceDTOList.add(instanceDTO);

    releaseDetailsMappingList = new ArrayList<>();
    releaseDetailsMappingList.add(releaseDetailsMapping);
    releaseDetailsMappingList.add(releaseDetailsMapping2);

    releaseDetailsRequests = new ArrayList<>();
    ReleaseDetailsRequest releaseDetailsRequest = new ReleaseDetailsRequest();
    releaseDetailsRequest.setReleaseName(RELEASE_NAME);
    releaseDetailsRequest.setNamespace(NAMESPACE);
    releaseDetailsRequests.add(releaseDetailsRequest);
  }
  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testGetV1ReleaseDetails() {
    when(releaseDetailsMappingRepository.findByAccountIdentifierAndReleaseKey(ACCOUNT_ID, RELEASE_KEY))
        .thenReturn(releaseDetailsMappingList);
    Response response = k8sReleaseServiceMappingApi.getV1ReleaseDetails(releaseDetailsRequests, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(
        ((BatchReleaseDetailsResponse) ((ArrayList) response.getEntity()).get(0)).get(0).releaseKey(RELEASE_KEY));
    verify(releaseDetailsMappingRepository, times(1)).findByAccountIdentifierAndReleaseKey(ACCOUNT_ID, RELEASE_KEY);
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testGetV1ReleaseDetailsForInstanceDetails() {
    when(releaseDetailsMappingRepository.findByAccountIdentifierAndReleaseKey(ACCOUNT_ID, RELEASE_KEY))
        .thenReturn(Collections.emptyList());
    when(instanceService.getActiveInstancesByInstanceInfoAndReleaseName(ACCOUNT_ID, NAMESPACE, RELEASE_NAME))
        .thenReturn(instanceDTOList);
    Response response = k8sReleaseServiceMappingApi.getV1ReleaseDetails(releaseDetailsRequests, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(
        ((BatchReleaseDetailsResponse) ((ArrayList) response.getEntity()).get(0)).get(0).releaseKey(RELEASE_KEY));
    verify(releaseDetailsMappingRepository, times(1)).findByAccountIdentifierAndReleaseKey(ACCOUNT_ID, RELEASE_KEY);
    verify(instanceService, times(1))
        .getActiveInstancesByInstanceInfoAndReleaseName(ACCOUNT_ID, NAMESPACE, RELEASE_NAME);
  }
}
