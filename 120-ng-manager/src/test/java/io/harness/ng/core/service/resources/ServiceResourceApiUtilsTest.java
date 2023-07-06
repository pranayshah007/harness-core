/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service.resources;

import static io.harness.rule.OwnerRule.PRATYUSH;
import static io.harness.rule.OwnerRule.TARUN_UBA;

import static junit.framework.TestCase.assertEquals;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.acl.api.PermissionCheckDTO;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.feature.NGServiceV2FFCalculator;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.ng.core.service.services.impl.ServiceEntityServiceImpl;
import io.harness.pms.rbac.NGResourceType;
import io.harness.rbac.CDNGRbacPermissions;
import io.harness.rule.Owner;
import io.harness.spec.server.ng.v1.model.Service;
import io.harness.spec.server.ng.v1.model.ServiceRequest;
import io.harness.spec.server.ng.v1.model.ServiceResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import io.serializer.HObjectMapper;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import org.joor.Reflect;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ServiceResourceApiUtilsTest extends CategoryTest {
  @Mock NGServiceV2FFCalculator ngServiceV2FFCalculator;
  @Inject ServiceEntityService serviceEntityService;
  private ObjectMapper objectMapper;
  private Validator validator;
  String identifier = randomAlphabetic(10);
  String name = randomAlphabetic(10);
  String account = randomAlphabetic(10);
  String org = randomAlphabetic(10);
  String project = randomAlphabetic(10);
  String description = randomAlphabetic(10);
  private ServiceResourceApiUtils serviceResourceApiUtils;
  Map<FeatureName, Boolean> featureFlags = new HashMap<>();

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    objectMapper = new ObjectMapper();
    HObjectMapper.configureObjectMapperForNG(objectMapper);

    ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
    serviceResourceApiUtils = new ServiceResourceApiUtils(validator);
    serviceEntityService = new ServiceEntityServiceImpl(null, null, null, null, null, null, null, null);
    Reflect.on(serviceResourceApiUtils).set("serviceEntityService", serviceEntityService);
    Reflect.on(serviceEntityService).set("ngServiceV2FFCalculator", ngServiceV2FFCalculator);
  }
  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testServiceEntity() {
    featureFlags.put(FeatureName.CDS_HELM_MULTIPLE_MANIFEST_SUPPORT_NG, false);
    when(ngServiceV2FFCalculator.computeFlags(account)).thenReturn(featureFlags);
    ServiceRequest serviceRequest = new ServiceRequest();
    serviceRequest.setIdentifier(identifier);
    serviceRequest.setName(name);
    serviceRequest.description(description);
    serviceRequest.setTags(null);
    ServiceEntity serviceEntity = serviceResourceApiUtils.mapToServiceEntity(serviceRequest, org, project, account);
    Set<ConstraintViolation<Object>> violations = validator.validate(serviceEntity);
    assertThat(violations.isEmpty()).as(violations.toString()).isTrue();

    assertEquals(account, serviceEntity.getAccountId());
    assertEquals(identifier, serviceEntity.getIdentifier());
    assertEquals(name, serviceEntity.getName());
    assertEquals(org, serviceEntity.getOrgIdentifier());
    assertEquals(project, serviceEntity.getProjectIdentifier());
  }

  @Test
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testServiceEntityWithMultipleHelmManifest() {
    featureFlags.put(FeatureName.CDS_HELM_MULTIPLE_MANIFEST_SUPPORT_NG, true);
    when(ngServiceV2FFCalculator.computeFlags(account)).thenReturn(featureFlags);
    ServiceRequest serviceRequest = new ServiceRequest();
    serviceRequest.setIdentifier(identifier);
    serviceRequest.setName(name);
    serviceRequest.description(description);
    serviceRequest.setTags(null);
    ServiceEntity serviceEntity = serviceResourceApiUtils.mapToServiceEntity(serviceRequest, org, project, account);
    Set<ConstraintViolation<Object>> violations = validator.validate(serviceEntity);
    assertThat(violations.isEmpty()).as(violations.toString()).isTrue();

    assertEquals(account, serviceEntity.getAccountId());
    assertEquals(identifier, serviceEntity.getIdentifier());
    assertEquals(name, serviceEntity.getName());
    assertEquals(org, serviceEntity.getOrgIdentifier());
    assertEquals(project, serviceEntity.getProjectIdentifier());
  }
  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testServiceResponse() {
    ServiceEntity serviceEntity = ServiceEntity.builder()
                                      .identifier(identifier)
                                      .accountId(account)
                                      .orgIdentifier(org)
                                      .projectIdentifier(project)
                                      .name(name)
                                      .description(description)
                                      .createdAt(123456789L)
                                      .lastModifiedAt(987654321L)
                                      .build();
    ServiceResponse serviceResponse = serviceResourceApiUtils.mapToServiceResponse(serviceEntity);
    Set<ConstraintViolation<Object>> violations = validator.validate(serviceResponse);
    assertThat(violations.isEmpty()).as(violations.toString()).isTrue();

    assertEquals(account, serviceResponse.getService().getAccount());
    assertEquals(identifier, serviceResponse.getService().getIdentifier());
    assertEquals(name, serviceResponse.getService().getName());
    assertEquals(org, serviceResponse.getService().getOrg());
    assertEquals(project, serviceResponse.getService().getProject());
    assertEquals(description, serviceResponse.getService().getDescription());
    assertEquals(123456789L, serviceResponse.getCreated().longValue());
    assertEquals(987654321L, serviceResponse.getUpdated().longValue());
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testAccessListServiceResponse() {
    ServiceEntity serviceEntity = ServiceEntity.builder()
                                      .identifier(identifier)
                                      .accountId(account)
                                      .orgIdentifier(org)
                                      .projectIdentifier(project)
                                      .name(name)
                                      .description(description)
                                      .createdAt(123456789L)
                                      .lastModifiedAt(987654321L)
                                      .build();
    ServiceResponse serviceResponse = serviceResourceApiUtils.mapToAccessListResponse(serviceEntity);
    Set<ConstraintViolation<Object>> violations = validator.validate(serviceResponse);
    assertThat(violations.isEmpty()).as(violations.toString()).isTrue();

    assertEquals(account, serviceResponse.getService().getAccount());
    assertEquals(identifier, serviceResponse.getService().getIdentifier());
    assertEquals(name, serviceResponse.getService().getName());
    assertEquals(org, serviceResponse.getService().getOrg());
    assertEquals(project, serviceResponse.getService().getProject());
    assertEquals(description, serviceResponse.getService().getDescription());
    assertEquals(123456789L, serviceResponse.getCreated().longValue());
    assertEquals(987654321L, serviceResponse.getUpdated().longValue());
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testPermissionCheckDTO() {
    ServiceResponse serviceResponse = new ServiceResponse();
    Service service = new Service();
    service.setAccount(account);
    service.setOrg(org);
    service.setProject(project);
    service.setIdentifier(identifier);
    service.setName(name);
    service.setDescription(description);
    serviceResponse.setService(service);
    serviceResponse.setUpdated(123456789L);
    serviceResponse.setCreated(987654321L);
    PermissionCheckDTO permissionCheckDTO =
        serviceResourceApiUtils.serviceResponseToPermissionCheckDTO(serviceResponse);
    Set<ConstraintViolation<Object>> violations = validator.validate(permissionCheckDTO);
    assertThat(violations.isEmpty()).as(violations.toString()).isTrue();

    assertEquals(CDNGRbacPermissions.SERVICE_RUNTIME_PERMISSION, permissionCheckDTO.getPermission());
    assertEquals(identifier, permissionCheckDTO.getResourceIdentifier());
    assertEquals(org, permissionCheckDTO.getResourceScope().getOrgIdentifier());
    assertEquals(account, permissionCheckDTO.getResourceScope().getAccountIdentifier());
    assertEquals(project, permissionCheckDTO.getResourceScope().getProjectIdentifier());
    assertEquals(NGResourceType.SERVICE, permissionCheckDTO.getResourceType());
  }
}