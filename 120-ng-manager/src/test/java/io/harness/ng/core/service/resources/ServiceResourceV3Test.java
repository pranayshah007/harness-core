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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.OrgAndProjectValidationHelper;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.services.ServiceEntityManagementService;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.pms.rbac.NGResourceType;
import io.harness.rule.Owner;
import io.harness.spec.server.ng.model.ServiceRequest;
import io.harness.spec.server.ng.model.ServiceResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageImpl;

@OwnedBy(CDC)
public class ServiceResourceV3Test extends CategoryTest {
  @InjectMocks ServicesResourceV3 serviceResourceV3;
  @Mock ServiceEntityService serviceEntityService;
  @Mock OrgAndProjectValidationHelper orgAndProjectValidationHelper;
  @Mock AccessControlClient accessControlClient;
  @Mock ServiceEntityManagementService serviceEntityManagementService;

  String slug = randomAlphabetic(10);
  String name = randomAlphabetic(10);
  String account = randomAlphabetic(10);
  String org = randomAlphabetic(10);
  String project = randomAlphabetic(10);
  String description = "sample description";
  ServiceEntity entity;
  ServiceEntity entityWithMongoVersion;
  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    entity = ServiceEntity.builder()
                 .accountId(account)
                 .orgIdentifier(org)
                 .projectIdentifier(project)
                 .identifier(slug)
                 .version(1L)
                 .description("")
                 .build();
    entityWithMongoVersion = ServiceEntity.builder()
                                 .accountId(account)
                                 .orgIdentifier(org)
                                 .projectIdentifier(project)
                                 .identifier(slug)
                                 .description("")
                                 .version(1L)
                                 .build();
  }
  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testCreateService() throws IOException {
    when(orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(org, project, account))
        .thenReturn(true);
    when(serviceEntityService.create(any())).thenReturn(entity);
    io.harness.spec.server.ng.model.ServiceRequest serviceRequest =
        new io.harness.spec.server.ng.model.ServiceRequest();
    serviceRequest.setSlug(slug);
    serviceRequest.setName(name);
    serviceRequest.setDescription(description);
    serviceResourceV3.createService(serviceRequest, org, project, account);
    verify(accessControlClient, times(1))
        .checkForAccessOrThrow(ResourceScope.of(account, org, project), Resource.of(NGResourceType.SERVICE, null),
            SERVICE_CREATE_PERMISSION);
    verify(orgAndProjectValidationHelper, times(1)).checkThatTheOrganizationAndProjectExists(org, project, account);
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testCreateServices() throws IOException {
    List<ServiceRequest> serviceRequestList = new ArrayList<>();
    List<ServiceEntity> serviceEntityList = new ArrayList<>();
    List<ServiceEntity> outputServiceEntitiesList = new ArrayList<>();
    outputServiceEntitiesList.add(entity);

    serviceEntityList.add(entity);
    io.harness.spec.server.ng.model.ServiceRequest serviceRequest =
        new io.harness.spec.server.ng.model.ServiceRequest();
    serviceRequest.setSlug(slug);
    serviceRequest.setName(name);
    serviceRequest.setDescription(description);
    serviceRequestList.add(serviceRequest);
    when(orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(org, project, account))
        .thenReturn(true);
    when(serviceEntityService.bulkCreate(eq(account), any())).thenReturn(new PageImpl<>(outputServiceEntitiesList));
    serviceResourceV3.createServicesBatch(serviceRequestList, org, project, account, 0, 30);
    verify(accessControlClient, times(1))
        .checkForAccessOrThrow(ResourceScope.of(account, org, project), Resource.of(NGResourceType.SERVICE, null),
            SERVICE_CREATE_PERMISSION);
    verify(orgAndProjectValidationHelper, times(1)).checkThatTheOrganizationAndProjectExists(org, project, account);
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testListTemplate() {
    when(serviceEntityService.get(any(), any(), any(), any(), eq(false))).thenReturn(Optional.of(entity));
    serviceResourceV3.getService(org, project, slug, account);
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testListTemplateForNotFoundException() {
    when(serviceEntityService.get(any(), any(), any(), any(), eq(false))).thenReturn(Optional.empty());
    assertThatThrownBy(() -> serviceResourceV3.getService(org, project, slug, account))
        .hasMessage(format("Service with identifier [%s] in project [%s], org [%s] not found", slug, project, org));
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testUpdateService() throws IOException {
    when(orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(org, project, account))
        .thenReturn(true);
    when(serviceEntityService.update(any())).thenReturn(entity);
    io.harness.spec.server.ng.model.ServiceRequest serviceRequest =
        new io.harness.spec.server.ng.model.ServiceRequest();
    serviceRequest.setSlug(slug);
    serviceRequest.setName(name);
    serviceRequest.setDescription(description);
    serviceResourceV3.updateService(serviceRequest, org, project, slug, account);
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
    io.harness.spec.server.ng.model.ServiceRequest serviceRequest =
        new io.harness.spec.server.ng.model.ServiceRequest();
    serviceRequest.setSlug(slug);
    serviceRequest.setName(name);
    serviceRequest.setDescription(description);
    serviceResourceV3.createService(serviceRequest, org, project, account);
    when(orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(org, project, account))
        .thenReturn(true);
    when(serviceEntityService.get(any(), any(), any(), any(), eq(false))).thenReturn(Optional.of(entity));
    when(serviceEntityManagementService.deleteService(any(), any(), any(), any(), any())).thenReturn(true);

    Response response = serviceResourceV3.deleteService(org, project, slug, account);

    ServiceResponse serviceResponse = (ServiceResponse) response.getEntity();

    assertEquals(slug, entity.getIdentifier());
    assertEquals(account, serviceResponse.getService().getAccount());
  }
}