/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service.resources;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rbac.CDNGRbacPermissions.SERVICE_CREATE_PERMISSION;
import static io.harness.rbac.CDNGRbacPermissions.SERVICE_UPDATE_PERMISSION;
import static io.harness.rule.OwnerRule.TARUN_UBA;

import static java.lang.String.format;
import static junit.framework.TestCase.assertEquals;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.OrgAndProjectValidationHelper;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.services.ServiceEntityManagementService;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.pms.rbac.NGResourceType;
import io.harness.rule.Owner;
import io.harness.spec.server.ng.model.Service;
import io.harness.spec.server.ng.model.ServiceRequest;
import io.harness.spec.server.ng.model.ServiceResponse;

import java.io.IOException;
import java.util.Optional;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDC)
public class ServicesApiImplTest extends CategoryTest {
  @InjectMocks ServicesApiImpl servicesApiImpl;
  @Mock ServiceEntityService serviceEntityService;
  @Mock OrgAndProjectValidationHelper orgAndProjectValidationHelper;
  @Mock AccessControlClient accessControlClient;
  @Mock ServiceEntityManagementService serviceEntityManagementService;
  @Mock ServiceResourceApiUtils serviceResourceApiUtils;

  String slug = randomAlphabetic(10);
  String name = randomAlphabetic(10);
  String account = randomAlphabetic(10);
  String org = randomAlphabetic(10);
  String project = randomAlphabetic(10);
  String description = "sample description";
  ServiceEntity entity;
  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    entity = ServiceEntity.builder()
                 .accountId(account)
                 .orgIdentifier(org)
                 .projectIdentifier(project)
                 .identifier(slug)
                 .version(1L)
                 .yaml("test")
                 .description("")
                 .build();
  }
  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testCreateService() throws IOException {
    when(orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(org, project, account))
        .thenReturn(true);
    when(serviceEntityService.create(any())).thenReturn(entity);
    when(serviceResourceApiUtils.getServiceEntity(any(), any(), any(), any())).thenReturn(entity);
    ServiceRequest serviceRequest = new ServiceRequest();
    serviceRequest.setSlug(slug);
    serviceRequest.setName(name);
    serviceRequest.setDescription(description);
    servicesApiImpl.createService(serviceRequest, org, project, account);
    verify(accessControlClient, times(1))
        .checkForAccessOrThrow(ResourceScope.of(account, org, project), Resource.of(NGResourceType.SERVICE, null),
            SERVICE_CREATE_PERMISSION);
    verify(orgAndProjectValidationHelper, times(1)).checkThatTheOrganizationAndProjectExists(org, project, account);
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testGetService() {
    when(serviceEntityService.get(any(), any(), any(), any(), eq(false))).thenReturn(Optional.of(entity));
    Service service = new Service();
    service.setAccount(account);
    service.setSlug(slug);
    service.setOrg(org);
    service.setProject(project);
    service.setName(name);
    service.setDescription(description);
    ServiceResponse serviceResponse = new ServiceResponse();
    serviceResponse.setCreated(987654321L);
    serviceResponse.setUpdated(123456789L);
    serviceResponse.setService(service);
    when(serviceResourceApiUtils.mapToServiceResponse(entity)).thenReturn(serviceResponse);
    Response response = servicesApiImpl.getService(org, project, slug, account);
    ServiceResponse entityCurr = (ServiceResponse) response.getEntity();

    assertEquals(slug, entityCurr.getService().getSlug());
    assertEquals(entity.getVersion().toString(), response.getEntityTag().getValue());
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testListTemplate() {
    when(serviceEntityService.get(any(), any(), any(), any(), eq(false))).thenReturn(Optional.of(entity));
    servicesApiImpl.getService(org, project, slug, account);
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testListTemplateForNotFoundException() {
    when(serviceEntityService.get(any(), any(), any(), any(), eq(false))).thenReturn(Optional.empty());
    assertThatThrownBy(() -> servicesApiImpl.getService(org, project, slug, account))
        .hasMessage(format("Service with identifier [%s] in project [%s], org [%s] not found", slug, project, org));
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testUpdateService() throws IOException {
    when(orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(org, project, account))
        .thenReturn(true);
    when(serviceEntityService.update(any())).thenReturn(entity);
    when(serviceResourceApiUtils.getServiceEntity(any(), any(), any(), any())).thenReturn(entity);
    io.harness.spec.server.ng.model.ServiceRequest serviceRequest =
        new io.harness.spec.server.ng.model.ServiceRequest();
    serviceRequest.setSlug(slug);
    serviceRequest.setName(name);
    serviceRequest.setDescription(description);
    servicesApiImpl.updateService(serviceRequest, org, project, slug, account);
    verify(accessControlClient, times(1))
        .checkForAccessOrThrow(ResourceScope.of(account, org, project),
            Resource.of(NGResourceType.SERVICE, serviceRequest.getSlug()), SERVICE_UPDATE_PERMISSION);
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testDeleteService() throws IOException {
    when(orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(org, project, account))
        .thenReturn(true);
    when(serviceEntityService.create(any())).thenReturn(entity);
    when(serviceResourceApiUtils.getServiceEntity(any(), any(), any(), any())).thenReturn(entity);
    io.harness.spec.server.ng.model.ServiceRequest serviceRequest =
        new io.harness.spec.server.ng.model.ServiceRequest();
    serviceRequest.setSlug(slug);
    serviceRequest.setName(name);
    serviceRequest.setDescription(description);
    servicesApiImpl.createService(serviceRequest, org, project, account);
    Service service = new Service();
    service.setAccount(account);
    service.setSlug(slug);
    service.setOrg(org);
    service.setProject(project);
    service.setName(name);
    service.setDescription(description);
    ServiceResponse serviceResponse = new ServiceResponse();
    serviceResponse.setCreated(987654321L);
    serviceResponse.setUpdated(123456789L);
    serviceResponse.setService(service);
    when(orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(org, project, account))
        .thenReturn(true);
    when(serviceEntityService.get(any(), any(), any(), any(), eq(false))).thenReturn(Optional.of(entity));
    when(serviceEntityManagementService.deleteService(any(), any(), any(), any(), any())).thenReturn(true);
    when(serviceResourceApiUtils.mapToServiceResponse(any())).thenReturn(serviceResponse);

    Response response = servicesApiImpl.deleteService(org, project, slug, account);

    ServiceResponse serviceResponseFinal = (ServiceResponse) response.getEntity();

    assertEquals(slug, entity.getIdentifier());
    assertEquals(account, serviceResponseFinal.getService().getAccount());
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testDeleteServiceFail() {
    when(serviceEntityService.get(any(), any(), any(), any(), eq(false))).thenReturn(Optional.of(entity));
    doReturn(false).when(serviceEntityManagementService).deleteService(account, org, project, slug, "ifMatch");
    try {
      servicesApiImpl.deleteService(org, project, slug, account);
    } catch (InvalidRequestException e) {
      assertEquals(e.getMessage(), String.format("Service with identifier [%s] could not be deleted", slug));
    }
  }
}