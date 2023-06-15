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
import static io.harness.rule.OwnerRule.HINGER;
import static io.harness.rule.OwnerRule.TARUN_UBA;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;

import static java.lang.String.format;
import static junit.framework.TestCase.assertEquals;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.services.ServiceEntityManagementService;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.ng.core.service.services.impl.ServiceEntityYamlSchemaHelper;
import io.harness.ng.core.utils.OrgAndProjectValidationHelper;
import io.harness.pms.rbac.NGResourceType;
import io.harness.rule.Owner;
import io.harness.spec.server.ng.v1.model.Service;
import io.harness.spec.server.ng.v1.model.ServiceRequest;
import io.harness.spec.server.ng.v1.model.ServiceResponse;
import io.harness.utils.featureflaghelper.NGFeatureFlagHelperService;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.Optional;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import javax.ws.rs.core.Response;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import lombok.Builder;
import lombok.Value;
import org.joor.Reflect;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDC)
@RunWith(JUnitParamsRunner.class)
public class ProjectServicesApiImplTest extends CategoryTest {
  @Inject @InjectMocks ProjectServicesApiImpl projectServicesApiImpl;
  @Mock NGFeatureFlagHelperService featureFlagHelperService;
  @Mock ServiceEntityService serviceEntityService;
  @Mock OrgAndProjectValidationHelper orgAndProjectValidationHelper;
  @Mock AccessControlClient accessControlClient;
  @Mock ServiceEntityManagementService serviceEntityManagementService;
  @Mock ServiceEntityYamlSchemaHelper serviceEntityYamlSchemaHelper;
  @Inject ServiceResourceApiUtils serviceResourceApiUtils;

  String identifier = randomAlphabetic(10);
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
                 .identifier(identifier)
                 .name(name)
                 .version(1L)
                 .yaml("test")
                 .description("")
                 .build();
    ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    Validator validator = factory.getValidator();
    serviceResourceApiUtils = new ServiceResourceApiUtils(validator);
    Reflect.on(projectServicesApiImpl).set("serviceResourceApiUtils", serviceResourceApiUtils);
  }
  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testCreateService() {
    when(orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(org, project, account))
        .thenReturn(true);
    when(serviceEntityService.create(any())).thenReturn(entity);
    ServiceRequest serviceRequest = new ServiceRequest();
    serviceRequest.setIdentifier(identifier);
    serviceRequest.setName(name);
    serviceRequest.setDescription(description);
    projectServicesApiImpl.createServiceEntity(serviceRequest, org, project, account);
    verify(accessControlClient, times(1))
        .checkForAccessOrThrow(ResourceScope.of(account, org, project), Resource.of(NGResourceType.SERVICE, null),
            SERVICE_CREATE_PERMISSION);
    verify(orgAndProjectValidationHelper, times(1)).checkThatTheOrganizationAndProjectExists(org, project, account);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testCreateServiceWithSchemaValidationFlagOn() throws IOException {
    when(featureFlagHelperService.isEnabled(account, FeatureName.DISABLE_CDS_SERVICE_ENV_SCHEMA_VALIDATION))
        .thenReturn(false);
    when(featureFlagHelperService.isEnabled(account, FeatureName.NG_SVC_ENV_REDESIGN)).thenReturn(true);

    when(orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(org, project, account))
        .thenReturn(true);
    when(serviceEntityService.create(any())).thenReturn(entity);
    ServiceRequest serviceRequest = new ServiceRequest();
    serviceRequest.setIdentifier(identifier);
    serviceRequest.setName(name);
    serviceRequest.setDescription(description);
    projectServicesApiImpl.createServiceEntity(serviceRequest, org, project, account);
    verify(accessControlClient, times(1))
        .checkForAccessOrThrow(ResourceScope.of(account, org, project), Resource.of(NGResourceType.SERVICE, null),
            SERVICE_CREATE_PERMISSION);
    verify(orgAndProjectValidationHelper, times(1)).checkThatTheOrganizationAndProjectExists(org, project, account);
    verify(serviceEntityYamlSchemaHelper, times(1)).validateSchema(account, serviceRequest.getYaml());
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testGetService() {
    when(serviceEntityService.get(any(), any(), any(), any(), eq(false))).thenReturn(Optional.of(entity));
    Service service = new Service();
    service.setAccount(account);
    service.setIdentifier(identifier);
    service.setOrg(org);
    service.setProject(project);
    service.setName(name);
    service.setDescription(description);
    ServiceResponse serviceResponse = new ServiceResponse();
    serviceResponse.setCreated(987654321L);
    serviceResponse.setUpdated(123456789L);
    serviceResponse.setService(service);
    Response response = projectServicesApiImpl.getServiceEntity(org, project, identifier, account);
    ServiceResponse entityCurr = (ServiceResponse) response.getEntity();

    assertEquals(identifier, entityCurr.getService().getIdentifier());
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testListTemplate() {
    when(serviceEntityService.get(any(), any(), any(), any(), eq(false))).thenReturn(Optional.of(entity));
    projectServicesApiImpl.getServiceEntity(org, project, identifier, account);
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testListTemplateForNotFoundException() {
    when(serviceEntityService.get(any(), any(), any(), any(), eq(false))).thenReturn(Optional.empty());
    assertThatThrownBy(() -> projectServicesApiImpl.getServiceEntity(org, project, identifier, account))
        .hasMessage(
            format("Service with identifier [%s] in project [%s], org [%s] not found", identifier, project, org));
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testUpdateService() throws IOException {
    when(featureFlagHelperService.isEnabled(account, FeatureName.DISABLE_CDS_SERVICE_ENV_SCHEMA_VALIDATION))
        .thenReturn(false);
    when(featureFlagHelperService.isEnabled(account, FeatureName.NG_SVC_ENV_REDESIGN)).thenReturn(true);
    when(orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(org, project, account))
        .thenReturn(true);
    when(serviceEntityService.update(any())).thenReturn(entity);
    io.harness.spec.server.ng.v1.model.ServiceRequest serviceRequest =
        new io.harness.spec.server.ng.v1.model.ServiceRequest();
    serviceRequest.setIdentifier(identifier);
    serviceRequest.setName(name);
    serviceRequest.setDescription(description);
    projectServicesApiImpl.updateServiceEntity(serviceRequest, org, project, identifier, account);
    verify(accessControlClient, times(1))
        .checkForAccessOrThrow(ResourceScope.of(account, org, project),
            Resource.of(NGResourceType.SERVICE, serviceRequest.getIdentifier()), SERVICE_UPDATE_PERMISSION);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testUpdateServiceWithSchemaValidationFlagOn() throws IOException {
    when(orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(org, project, account))
        .thenReturn(true);
    when(serviceEntityService.update(any())).thenReturn(entity);
    io.harness.spec.server.ng.v1.model.ServiceRequest serviceRequest =
        new io.harness.spec.server.ng.v1.model.ServiceRequest();
    serviceRequest.setIdentifier(identifier);
    serviceRequest.setName(name);
    serviceRequest.setDescription(description);
    projectServicesApiImpl.updateServiceEntity(serviceRequest, org, project, identifier, account);
    verify(accessControlClient, times(1))
        .checkForAccessOrThrow(ResourceScope.of(account, org, project),
            Resource.of(NGResourceType.SERVICE, serviceRequest.getIdentifier()), SERVICE_UPDATE_PERMISSION);
    verify(serviceEntityYamlSchemaHelper, times(1)).validateSchema(account, serviceRequest.getYaml());
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testDeleteService() throws IOException {
    when(orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(org, project, account))
        .thenReturn(true);
    when(serviceEntityService.create(any())).thenReturn(entity);
    io.harness.spec.server.ng.v1.model.ServiceRequest serviceRequest =
        new io.harness.spec.server.ng.v1.model.ServiceRequest();
    serviceRequest.setIdentifier(identifier);
    serviceRequest.setName(name);
    serviceRequest.setDescription(description);
    projectServicesApiImpl.createServiceEntity(serviceRequest, org, project, account);
    Service service = new Service();
    service.setAccount(account);
    service.setIdentifier(identifier);
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
    when(serviceEntityManagementService.deleteService(account, org, project, identifier, "ifMatch", false))
        .thenReturn(true);

    Response response = projectServicesApiImpl.deleteServiceEntity(org, project, identifier, account, false);

    ServiceResponse serviceResponseFinal = (ServiceResponse) response.getEntity();

    assertEquals(identifier, entity.getIdentifier());
    assertEquals(account, serviceResponseFinal.getService().getAccount());
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testDeleteServiceFail() {
    when(serviceEntityService.get(any(), any(), any(), any(), eq(false))).thenReturn(Optional.of(entity));
    doReturn(false)
        .when(serviceEntityManagementService)
        .deleteService(account, org, project, identifier, "ifMatch", false);
    try {
      projectServicesApiImpl.deleteServiceEntity(org, project, identifier, account, false);
    } catch (InvalidRequestException e) {
      assertEquals(e.getMessage(), String.format("Service with identifier [%s] could not be deleted", identifier));
    }
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  @Parameters(method = "getTestData")
  public void testCreateServicesSuccessfullyForDifferentScopes(TestData testData) throws IOException {
    when(orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(any(), any(), any())).thenReturn(true);
    when(serviceEntityService.create(any()))
        .thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0, ServiceEntity.class));

    Response response = projectServicesApiImpl.createServiceEntity(testData.getServiceRequest(),
        testData.getOrgIdentifier(), testData.getProjectIdentifier(), testData.getAccountIdentifier());

    verify(accessControlClient, times(1))
        .checkForAccessOrThrow(ResourceScope.of(testData.getAccountIdentifier(), testData.getOrgIdentifier(),
                                   testData.getProjectIdentifier()),
            Resource.of(NGResourceType.SERVICE, null), SERVICE_CREATE_PERMISSION);
    verify(orgAndProjectValidationHelper, times(1))
        .checkThatTheOrganizationAndProjectExists(
            testData.getOrgIdentifier(), testData.getProjectIdentifier(), testData.getAccountIdentifier());
    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.CREATED);
  }

  private Object[][] getTestData() {
    ServiceRequest serviceRequest = new ServiceRequest();
    serviceRequest.setIdentifier(identifier);
    serviceRequest.setName(name);
    serviceRequest.setDescription(description);

    // project scoped service
    TestData testData1 = TestData.builder()
                             .accountIdentifier(account)
                             .orgIdentifier(org)
                             .projectIdentifier(project)
                             .serviceRequest(serviceRequest)
                             .build();

    // org scoped service
    TestData testData2 =
        TestData.builder().accountIdentifier(account).orgIdentifier(org).serviceRequest(serviceRequest).build();

    // account scoped service
    TestData testData3 = TestData.builder().accountIdentifier(account).serviceRequest(serviceRequest).build();
    return new Object[][] {{testData1}, {testData2}, {testData3}};
  }

  @Value
  @Builder
  private static class TestData {
    ServiceRequest serviceRequest;
    String accountIdentifier;
    String orgIdentifier;
    String projectIdentifier;

    ServiceEntity serviceEntity;
  }
}