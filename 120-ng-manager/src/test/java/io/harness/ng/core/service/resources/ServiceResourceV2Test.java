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
import static io.harness.rule.OwnerRule.SHIVAM;
import static io.harness.rule.OwnerRule.TATHAGAT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
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
import io.harness.ng.core.beans.ServiceV2YamlMetadata;
import io.harness.ng.core.beans.ServicesV2YamlMetadataDTO;
import io.harness.ng.core.beans.ServicesYamlMetadataApiInput;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.service.dto.ServiceRequestDTO;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.pms.rbac.NGResourceType;
import io.harness.repositories.UpsertOptions;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageImpl;

@OwnedBy(CDC)
public class ServiceResourceV2Test extends CategoryTest {
  @Mock ServiceEntityService serviceEntityService;
  @InjectMocks ServiceResourceV2 serviceResourceV2;
  @Mock OrgAndProjectValidationHelper orgAndProjectValidationHelper;
  @Mock AccessControlClient accessControlClient;

  private final String ACCOUNT_ID = "account_id";
  private final String ORG_IDENTIFIER = "orgId";
  private final String PROJ_IDENTIFIER = "projId";
  private final String IDENTIFIER = "identifier";
  private final String NAME = "name";
  ServiceEntity entity;
  ServiceEntity entityWithMongoVersion;
  ServiceRequestDTO serviceRequestDTO;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    entity = ServiceEntity.builder()
                 .accountId(ACCOUNT_ID)
                 .orgIdentifier(ORG_IDENTIFIER)
                 .projectIdentifier(PROJ_IDENTIFIER)
                 .identifier(IDENTIFIER)
                 .version(1L)
                 .description("")
                 .build();
    entityWithMongoVersion = ServiceEntity.builder()
                                 .accountId(ACCOUNT_ID)
                                 .orgIdentifier(ORG_IDENTIFIER)
                                 .projectIdentifier(PROJ_IDENTIFIER)
                                 .identifier(IDENTIFIER)
                                 .description("")
                                 .version(1L)
                                 .build();
    serviceRequestDTO = ServiceRequestDTO.builder()
                            .identifier(IDENTIFIER)
                            .orgIdentifier(ORG_IDENTIFIER)
                            .projectIdentifier(PROJ_IDENTIFIER)
                            .name(NAME)
                            .build();
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testCreateService() throws IOException {
    when(orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
             ORG_IDENTIFIER, PROJ_IDENTIFIER, ACCOUNT_ID))
        .thenReturn(true);
    when(serviceEntityService.create(any())).thenReturn(entity);
    serviceResourceV2.create(ACCOUNT_ID, serviceRequestDTO);
    verify(accessControlClient, times(1))
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, serviceRequestDTO.getOrgIdentifier(),
                                   serviceRequestDTO.getProjectIdentifier()),
            Resource.of(NGResourceType.SERVICE, null), SERVICE_CREATE_PERMISSION);
    verify(orgAndProjectValidationHelper, times(1))
        .checkThatTheOrganizationAndProjectExists(ORG_IDENTIFIER, PROJ_IDENTIFIER, ACCOUNT_ID);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testCreateServices() throws IOException {
    List<ServiceRequestDTO> serviceRequestDTOList = new ArrayList<>();
    List<ServiceEntity> serviceEntityList = new ArrayList<>();
    List<ServiceEntity> outputServiceEntitiesList = new ArrayList<>();
    outputServiceEntitiesList.add(entity);

    serviceEntityList.add(entity);
    serviceRequestDTOList.add(serviceRequestDTO);
    when(orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
             ORG_IDENTIFIER, PROJ_IDENTIFIER, ACCOUNT_ID))
        .thenReturn(true);
    when(serviceEntityService.bulkCreate(eq(ACCOUNT_ID), any())).thenReturn(new PageImpl<>(outputServiceEntitiesList));
    serviceResourceV2.createServices(ACCOUNT_ID, serviceRequestDTOList);
    verify(accessControlClient, times(1))
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, serviceRequestDTO.getOrgIdentifier(),
                                   serviceRequestDTO.getProjectIdentifier()),
            Resource.of(NGResourceType.SERVICE, null), SERVICE_CREATE_PERMISSION);
    verify(orgAndProjectValidationHelper, times(1))
        .checkThatTheOrganizationAndProjectExists(ORG_IDENTIFIER, PROJ_IDENTIFIER, ACCOUNT_ID);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testListTemplate() {
    when(serviceEntityService.get(any(), any(), any(), any(), eq(false))).thenReturn(Optional.of(entity));
    serviceResourceV2.get(IDENTIFIER, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, false);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testListTemplateForNotFoundException() {
    when(serviceEntityService.get(any(), any(), any(), any(), eq(false))).thenReturn(Optional.empty());
    assertThatThrownBy(() -> serviceResourceV2.get(IDENTIFIER, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, false))
        .hasMessage("Service with identifier [identifier] in project [projId], org [orgId] not found");
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testUpdateService() throws IOException {
    when(orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
             ORG_IDENTIFIER, PROJ_IDENTIFIER, ACCOUNT_ID))
        .thenReturn(true);
    when(serviceEntityService.update(any())).thenReturn(entity);
    serviceResourceV2.update("IF_MATCH", ACCOUNT_ID, serviceRequestDTO);
    verify(accessControlClient, times(1))
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, serviceRequestDTO.getOrgIdentifier(),
                                   serviceRequestDTO.getProjectIdentifier()),
            Resource.of(NGResourceType.SERVICE, serviceRequestDTO.getIdentifier()), SERVICE_UPDATE_PERMISSION);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testUpsertService() throws IOException {
    when(orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
             ORG_IDENTIFIER, PROJ_IDENTIFIER, ACCOUNT_ID))
        .thenReturn(true);
    when(serviceEntityService.upsert(any(), eq(UpsertOptions.DEFAULT))).thenReturn(entity);
    serviceResourceV2.upsert("IF_MATCH", ACCOUNT_ID, serviceRequestDTO);
    verify(accessControlClient, times(1))
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, serviceRequestDTO.getOrgIdentifier(),
                                   serviceRequestDTO.getProjectIdentifier()),
            Resource.of(NGResourceType.SERVICE, serviceRequestDTO.getIdentifier()), SERVICE_UPDATE_PERMISSION);
    verify(orgAndProjectValidationHelper, times(1))
        .checkThatTheOrganizationAndProjectExists(ORG_IDENTIFIER, PROJ_IDENTIFIER, ACCOUNT_ID);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testGetServicesYamlAndRuntimeInputs() {
    String svcId1 = "svcId1";
    String svcId2 = "svcId2";

    final ServicesYamlMetadataApiInput servicesYamlMetadataApiInput =
        ServicesYamlMetadataApiInput.builder().serviceIdentifiers(Arrays.asList(svcId1, svcId2)).build();

    ServiceEntity service1 = ServiceEntity.builder().identifier(svcId1).yaml("dummy-yaml1").build();
    ServiceEntity service2 = ServiceEntity.builder().identifier(svcId2).yaml("dummy-yaml2").build();

    doReturn(Arrays.asList(service1, service2))
        .when(serviceEntityService)
        .getServices(anyString(), anyString(), anyString(), anyList());
    doReturn("input-set1", "input-set2").when(serviceEntityService).createServiceInputsYaml(anyString(), anyString());

    final ResponseDTO<ServicesV2YamlMetadataDTO> servicesYamlAndRuntimeInputsResponse =
        serviceResourceV2.getServicesYamlAndRuntimeInputs(
            servicesYamlMetadataApiInput, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER);

    final ServicesV2YamlMetadataDTO data = servicesYamlAndRuntimeInputsResponse.getData();
    assertThat(data).isNotNull();
    assertThat(data.getServiceV2YamlMetadataList()).hasSize(2);
    assertThat(data.getServiceV2YamlMetadataList()
                   .stream()
                   .map(io.harness.ng.core.beans.ServiceV2YamlMetadata::getServiceYaml)
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder("dummy-yaml1", "dummy-yaml2");
    assertThat(data.getServiceV2YamlMetadataList()
                   .stream()
                   .map(io.harness.ng.core.beans.ServiceV2YamlMetadata::getServiceIdentifier)
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder(svcId1, svcId2);

    assertThat(data.getServiceV2YamlMetadataList()
                   .stream()
                   .map(ServiceV2YamlMetadata::getInputSetTemplateYaml)
                   .collect(Collectors.toList()))
        .containsExactlyInAnyOrder("input-set1", "input-set2");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testGetServicesYamlAndRuntimeInputsFail() {
    final ServicesYamlMetadataApiInput servicesYamlMetadataApiInput =
        ServicesYamlMetadataApiInput.builder().serviceIdentifiers(Collections.singletonList("svcId1")).build();

    ServiceEntity service1 = ServiceEntity.builder().identifier("svcId1").build();

    doReturn(Collections.singletonList(service1))
        .when(serviceEntityService)
        .getServices(anyString(), anyString(), anyString(), anyList());

    assertThatThrownBy(()
                           -> serviceResourceV2.getServicesYamlAndRuntimeInputs(
                               servicesYamlMetadataApiInput, ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(
            "Service with identifier svcId1 is not configured with a Service definition. Service Yaml is empty");
  }
}