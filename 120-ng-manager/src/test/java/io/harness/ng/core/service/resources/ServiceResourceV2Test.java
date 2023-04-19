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
import static io.harness.rule.OwnerRule.ACHYUTH;
import static io.harness.rule.OwnerRule.SATHISH;
import static io.harness.rule.OwnerRule.SHIVAM;
import static io.harness.rule.OwnerRule.TATHAGAT;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;
import static io.harness.rule.OwnerRule.VED;
import static io.harness.rule.OwnerRule.vivekveman;

import static java.lang.String.format;
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
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.cdng.artifact.resources.artifactory.service.ArtifactoryResourceServiceImpl;
import io.harness.cdng.manifest.yaml.kinds.KustomizeCommandFlagType;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.OrgAndProjectValidationHelper;
import io.harness.ng.core.beans.ServiceV2YamlMetadata;
import io.harness.ng.core.beans.ServicesV2YamlMetadataDTO;
import io.harness.ng.core.beans.ServicesYamlMetadataApiInput;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.service.dto.ServiceRequestDTO;
import io.harness.ng.core.service.dto.ServiceResponse;
import io.harness.ng.core.service.dto.ServiceResponseDTO;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.pms.rbac.NGResourceType;
import io.harness.repositories.UpsertOptions;
import io.harness.rule.Owner;
import io.harness.utils.NGFeatureFlagHelperService;

import software.wings.beans.Service.ServiceKeys;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@OwnedBy(CDC)
public class ServiceResourceV2Test extends CategoryTest {
  @Mock ServiceEntityService serviceEntityService;
  @InjectMocks ServiceResourceV2 serviceResourceV2;
  @Mock OrgAndProjectValidationHelper orgAndProjectValidationHelper;
  @Mock AccessControlClient accessControlClient;
  @Mock NGFeatureFlagHelperService featureFlagHelperService;
  @Mock ServiceEntityYamlSchemaHelper serviceSchemaHelper;

  private final String ACCOUNT_ID = "account_id";
  private final String ORG_IDENTIFIER = "orgId";
  private final String PROJ_IDENTIFIER = "projId";
  private final String IDENTIFIER = "identifier";
  private final String NAME = "name";
  ServiceEntity entity;
  ServiceEntity entityWithMongoVersion;
  ServiceRequestDTO serviceRequestDTO;
  ServiceResponseDTO serviceResponseDTO;

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
    serviceResponseDTO = ServiceResponseDTO.builder()
                             .accountId(ACCOUNT_ID)
                             .identifier(IDENTIFIER)
                             .orgIdentifier(ORG_IDENTIFIER)
                             .projectIdentifier(PROJ_IDENTIFIER)
                             .version(1L)
                             .description("")
                             .tags(new HashMap<>())
                             .build();
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testCreateService() {
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
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testCreateServiceWithSchemaValidationFlagOn() throws IOException {
    when(featureFlagHelperService.isEnabled(ACCOUNT_ID, FeatureName.DISABLE_CDS_SERVICE_ENV_SCHEMA_VALIDATION))
        .thenReturn(false);
    when(featureFlagHelperService.isEnabled(ACCOUNT_ID, FeatureName.NG_SVC_ENV_REDESIGN)).thenReturn(true);
    String yaml = "service:\n"
        + "  name: das\n"
        + "  identifier: das\n"
        + "  tags: {}\n"
        + "  serviceDefinition:\n"
        + "    type: Kubernetes";
    serviceRequestDTO = ServiceRequestDTO.builder()
                            .identifier(IDENTIFIER)
                            .orgIdentifier(ORG_IDENTIFIER)
                            .projectIdentifier(PROJ_IDENTIFIER)
                            .name(NAME)
                            .yaml(yaml)
                            .build();
    assertThatThrownBy(() -> serviceResourceV2.create(ACCOUNT_ID, serviceRequestDTO))
        .isInstanceOf(InvalidRequestException.class);
    verify(serviceSchemaHelper, times(1)).validateSchema(ACCOUNT_ID, serviceRequestDTO.getYaml());
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testCreateServices() {
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
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testCreateServicesWithSchemaValidationFlagOn() throws IOException {
    when(featureFlagHelperService.isEnabled(ACCOUNT_ID, FeatureName.DISABLE_CDS_SERVICE_ENV_SCHEMA_VALIDATION))
        .thenReturn(false);
    when(featureFlagHelperService.isEnabled(ACCOUNT_ID, FeatureName.NG_SVC_ENV_REDESIGN)).thenReturn(true);
    String yaml = "service:\n"
        + "  name: das\n"
        + "  identifier: das\n"
        + "  tags: {}\n"
        + "  serviceDefinition:\n"
        + "    type: Kubernetes";
    List<ServiceRequestDTO> serviceRequestDTOList = new ArrayList<>();
    serviceRequestDTO = ServiceRequestDTO.builder()
                            .identifier(IDENTIFIER)
                            .orgIdentifier(ORG_IDENTIFIER)
                            .projectIdentifier(PROJ_IDENTIFIER)
                            .name(NAME)
                            .yaml("")
                            .build();
    ServiceRequestDTO serviceRequestDTO1 = ServiceRequestDTO.builder()
                                               .identifier(IDENTIFIER)
                                               .orgIdentifier(ORG_IDENTIFIER)
                                               .projectIdentifier(PROJ_IDENTIFIER)
                                               .name(NAME)
                                               .yaml(yaml)
                                               .build();
    serviceRequestDTOList.add(serviceRequestDTO);
    serviceRequestDTOList.add(serviceRequestDTO1);

    assertThatThrownBy(() -> serviceResourceV2.createServices(ACCOUNT_ID, serviceRequestDTOList))
        .isInstanceOf(InvalidRequestException.class);
    verify(serviceSchemaHelper, times(1)).validateSchema(ACCOUNT_ID, serviceRequestDTO.getYaml());
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
  public void testUpdateService() {
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
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testUpdateServiceWithSchemeFfOn() {
    when(featureFlagHelperService.isEnabled(ACCOUNT_ID, FeatureName.DISABLE_CDS_SERVICE_ENV_SCHEMA_VALIDATION))
        .thenReturn(false);
    when(featureFlagHelperService.isEnabled(ACCOUNT_ID, FeatureName.NG_SVC_ENV_REDESIGN)).thenReturn(true);
    when(orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
             ORG_IDENTIFIER, PROJ_IDENTIFIER, ACCOUNT_ID))
        .thenReturn(true);
    when(serviceEntityService.update(any())).thenReturn(entity);
    serviceResourceV2.update("IF_MATCH", ACCOUNT_ID, serviceRequestDTO);
    verify(accessControlClient, times(1))
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, serviceRequestDTO.getOrgIdentifier(),
                                   serviceRequestDTO.getProjectIdentifier()),
            Resource.of(NGResourceType.SERVICE, serviceRequestDTO.getIdentifier()), SERVICE_UPDATE_PERMISSION);
    verify(serviceSchemaHelper, times(1)).validateSchema(ACCOUNT_ID, serviceRequestDTO.getYaml());
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testUpsertService() {
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
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testUpsertServiceWithSchemaValidationFlagOn() {
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
    verify(serviceSchemaHelper, times(1)).validateSchema(ACCOUNT_ID, serviceRequestDTO.getYaml());
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

    when(featureFlagHelperService.isEnabled(ACCOUNT_ID, FeatureName.CDS_ARTIFACTORY_REPOSITORY_URL_MANDATORY))
        .thenReturn(true);

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
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testCreateServiceWithEmptyYaml() throws IOException {
    when(featureFlagHelperService.isEnabled(ACCOUNT_ID, FeatureName.DISABLE_CDS_SERVICE_ENV_SCHEMA_VALIDATION))
        .thenReturn(false);

    when(featureFlagHelperService.isEnabled(ACCOUNT_ID, FeatureName.NG_SVC_ENV_REDESIGN)).thenReturn(true);

    when(serviceEntityService.create(any())).thenReturn(entity);

    serviceRequestDTO = ServiceRequestDTO.builder()
                            .identifier(IDENTIFIER)
                            .orgIdentifier(ORG_IDENTIFIER)
                            .projectIdentifier(PROJ_IDENTIFIER)
                            .name(NAME)
                            .yaml("")
                            .build();

    serviceResourceV2.create(ACCOUNT_ID, serviceRequestDTO);
    verify(serviceSchemaHelper, times(2)).validateSchema(any(), any());
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testUpsertServiceWithEmptyYaml() {
    when(orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
             ORG_IDENTIFIER, PROJ_IDENTIFIER, ACCOUNT_ID))
        .thenReturn(true);

    when(serviceEntityService.upsert(any(), eq(UpsertOptions.DEFAULT))).thenReturn(entity);

    serviceRequestDTO = ServiceRequestDTO.builder()
                            .identifier(IDENTIFIER)
                            .orgIdentifier(ORG_IDENTIFIER)
                            .projectIdentifier(PROJ_IDENTIFIER)
                            .name(NAME)
                            .yaml("")
                            .build();

    serviceResourceV2.upsert("IF_MATCH", ACCOUNT_ID, serviceRequestDTO);

    verify(serviceSchemaHelper, times(2)).validateSchema(any(), any());
  }
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testCreateServicesWithEmptyYaml() throws IOException {
    when(featureFlagHelperService.isEnabled(ACCOUNT_ID, FeatureName.DISABLE_CDS_SERVICE_ENV_SCHEMA_VALIDATION))
        .thenReturn(false);
    when(featureFlagHelperService.isEnabled(ACCOUNT_ID, FeatureName.NG_SVC_ENV_REDESIGN)).thenReturn(true);
    List<ServiceRequestDTO> serviceRequestDTOList = new ArrayList<>();
    List<ServiceEntity> outputServiceEntitiesList = new ArrayList<>();
    outputServiceEntitiesList.add(entity);
    serviceRequestDTO = ServiceRequestDTO.builder()
                            .identifier(IDENTIFIER)
                            .orgIdentifier(ORG_IDENTIFIER)
                            .projectIdentifier(PROJ_IDENTIFIER)
                            .name(NAME)
                            .build();
    ServiceRequestDTO serviceRequestDTO1 = ServiceRequestDTO.builder()
                                               .identifier(IDENTIFIER)
                                               .orgIdentifier(ORG_IDENTIFIER)
                                               .projectIdentifier(PROJ_IDENTIFIER)
                                               .name(NAME)
                                               .build();
    serviceRequestDTOList.add(serviceRequestDTO);
    serviceRequestDTOList.add(serviceRequestDTO1);
    when(serviceEntityService.bulkCreate(eq(ACCOUNT_ID), any())).thenReturn(new PageImpl<>(outputServiceEntitiesList));
    serviceResourceV2.createServices(ACCOUNT_ID, serviceRequestDTOList);
    verify(serviceSchemaHelper, times(4)).validateSchema(any(), any());
  }

  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testUpdateServiceWithEmptyYaml() {
    when(featureFlagHelperService.isEnabled(ACCOUNT_ID, FeatureName.DISABLE_CDS_SERVICE_ENV_SCHEMA_VALIDATION))
        .thenReturn(false);
    when(featureFlagHelperService.isEnabled(ACCOUNT_ID, FeatureName.NG_SVC_ENV_REDESIGN)).thenReturn(true);
    when(orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
             ORG_IDENTIFIER, PROJ_IDENTIFIER, ACCOUNT_ID))
        .thenReturn(true);
    when(serviceEntityService.update(any())).thenReturn(entity);
    ServiceRequestDTO serviceRequestDTO1 = ServiceRequestDTO.builder()
                                               .identifier(IDENTIFIER)
                                               .orgIdentifier(ORG_IDENTIFIER)
                                               .projectIdentifier(PROJ_IDENTIFIER)
                                               .name(NAME)
                                               .build();
    serviceResourceV2.update("IF_MATCH", ACCOUNT_ID, serviceRequestDTO1);
    verify(serviceSchemaHelper, times(2)).validateSchema(any(), any());
  }

  @Test
  @Owner(developers = VED)
  @Category(UnitTests.class)
  public void testUpdateArtifactoryRegistryUrlIfEmpty() {
    String serviceYaml = "service:\n"
        + "  name: arf4\n"
        + "  identifier: arf4\n"
        + "  tags: {}\n"
        + "  serviceDefinition:\n"
        + "    spec:\n"
        + "      artifacts:\n"
        + "        primary:\n"
        + "          primaryArtifactRef: <+input>\n"
        + "          sources:\n"
        + "            - spec:\n"
        + "                connectorRef: artifconn1\n"
        + "                artifactPath: adoptopenjdk/openjdk8\n"
        + "                tag: <+input>\n"
        + "                repository: docker\n"
        + "                repositoryFormat: docker\n"
        + "              identifier: s\n"
        + "              type: ArtifactoryRegistry\n"
        + "            - spec:\n"
        + "                connectorRef: account.harnessImage\n"
        + "                imagePath: library/nginx\n"
        + "                tag: <+input>\n"
        + "              identifier: sfdff\n"
        + "              type: DockerRegistry\n"
        + "            - spec:\n"
        + "                connectorRef: artifconn1\n"
        + "                artifactPath: adoptopenjdk/openjdk8\n"
        + "                tag: <+input>\n"
        + "                repository: docker\n"
        + "                repositoryFormat: docker\n"
        + "              identifier: dhjjadnck\n"
        + "              type: ArtifactoryRegistry\n"
        + "    type: Kubernetes\n";

    ServiceEntity service = ServiceEntity.builder()
                                .accountId("accountId")
                                .name("service")
                                .description("description")
                                .orgIdentifier("orgIdentifier")
                                .projectIdentifier("projectIdentifier")
                                .yaml(serviceYaml)
                                .build();

    ArtifactoryConnectorDTO artifactoryConnectorDTO =
        ArtifactoryConnectorDTO.builder().artifactoryServerUrl("https://harness.jfrog.io/harness").build();

    try (MockedStatic<ArtifactoryResourceServiceImpl> utilities =
             Mockito.mockStatic(ArtifactoryResourceServiceImpl.class)) {
      utilities.when(() -> ArtifactoryResourceServiceImpl.getConnector(any())).thenReturn(artifactoryConnectorDTO);

      service = serviceResourceV2.updateArtifactoryRegistryUrlIfEmpty(
          service, "accountId", "orgIdentifier", "projectIdentifier");
    }

    String updatedYaml = "service:\n"
        + "  name: \"arf4\"\n"
        + "  identifier: \"arf4\"\n"
        + "  tags: {}\n"
        + "  serviceDefinition:\n"
        + "    spec:\n"
        + "      artifacts:\n"
        + "        primary:\n"
        + "          primaryArtifactRef: \"<+input>\"\n"
        + "          sources:\n"
        + "          - spec:\n"
        + "              connectorRef: \"artifconn1\"\n"
        + "              artifactPath: \"adoptopenjdk/openjdk8\"\n"
        + "              tag: \"<+input>\"\n"
        + "              repository: \"docker\"\n"
        + "              repositoryFormat: \"docker\"\n"
        + "              repositoryUrl: \"https://harness-docker.jfrog.io\"\n"
        + "            identifier: \"s\"\n"
        + "            type: \"ArtifactoryRegistry\"\n"
        + "          - spec:\n"
        + "              connectorRef: \"account.harnessImage\"\n"
        + "              imagePath: \"library/nginx\"\n"
        + "              tag: \"<+input>\"\n"
        + "            identifier: \"sfdff\"\n"
        + "            type: \"DockerRegistry\"\n"
        + "          - spec:\n"
        + "              connectorRef: \"artifconn1\"\n"
        + "              artifactPath: \"adoptopenjdk/openjdk8\"\n"
        + "              tag: \"<+input>\"\n"
        + "              repository: \"docker\"\n"
        + "              repositoryFormat: \"docker\"\n"
        + "              repositoryUrl: \"https://harness-docker.jfrog.io\"\n"
        + "            identifier: \"dhjjadnck\"\n"
        + "            type: \"ArtifactoryRegistry\"\n"
        + "    type: \"Kubernetes\"\n";

    assertThat(updatedYaml).isEqualTo(service.getYaml());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testListServices() {
    Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, ServiceKeys.createdAt));
    Page<ServiceEntity> serviceList = new PageImpl<>(Collections.singletonList(entity), pageable, 1);
    when(serviceEntityService.list(any(), any())).thenReturn(serviceList);
    List<ServiceResponse> content =
        serviceResourceV2.getAllServicesList(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "services", 0, 10, null)
            .getData()
            .getContent();

    assertThat(content).isNotNull();
    assertThat(content.size()).isEqualTo(1);
    assertThat(content.get(0).getService()).isEqualTo(serviceResponseDTO);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testListServicesWithInvalidAccountIdentifier() {
    when(serviceEntityService.list(any(), any()))
        .thenThrow(new InvalidRequestException(format("Invalid account identifier, %s", ACCOUNT_ID)));

    assertThatThrownBy(()
                           -> serviceResourceV2.getAllServicesList(
                               ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "services", 0, 10, null))
        .hasMessage(format("Invalid account identifier, %s", ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testKustomizeCommandFlags() {
    assertThat(serviceResourceV2.getKustomizeCommandFlags().getData())
        .containsExactlyInAnyOrder(KustomizeCommandFlagType.BUILD);
  }
}