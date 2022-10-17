/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.environment.resources;

import static io.harness.NGConstants.HARNESS_BLUE;
import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.pms.rbac.NGResourceType.ENVIRONMENT;
import static io.harness.rbac.CDNGRbacPermissions.ENVIRONMENT_CREATE_PERMISSION;
import static io.harness.rbac.CDNGRbacPermissions.ENVIRONMENT_UPDATE_PERMISSION;
import static io.harness.rbac.CDNGRbacPermissions.ENVIRONMENT_VIEW_PERMISSION;
import static io.harness.rule.OwnerRule.TARUN_UBA;

import static java.util.Collections.singletonMap;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.acl.api.AccessCheckResponseDTO;
import io.harness.accesscontrol.acl.api.AccessControlDTO;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.configfile.ConfigFile;
import io.harness.cdng.configfile.ConfigFileWrapper;
import io.harness.ng.core.EnvironmentValidationHelper;
import io.harness.ng.core.OrgAndProjectValidationHelper;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.ng.core.environment.mappers.EnvironmentFilterHelper;
import io.harness.ng.core.environment.mappers.EnvironmentMapper;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.environment.yaml.NGEnvironmentConfig;
import io.harness.ng.core.environment.yaml.NGEnvironmentInfoConfig;
import io.harness.ng.core.service.ServiceEntityValidationHelper;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;
import io.harness.ng.core.serviceoverride.mapper.ServiceOverridesMapper;
import io.harness.ng.core.serviceoverride.services.ServiceOverrideService;
import io.harness.ng.core.serviceoverride.yaml.NGServiceOverrideConfig;
import io.harness.ng.core.serviceoverride.yaml.NGServiceOverrideInfoConfig;
import io.harness.rule.Owner;
import io.harness.spec.server.ng.model.EnvironmentRequest;
import io.harness.spec.server.ng.model.EnvironmentResponse;
import io.harness.spec.server.ng.model.ServiceOverrideRequest;
import io.harness.spec.server.ng.model.ServiceOverrideResponse;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(CDC)
public class ProjectEnvironmentsApiImplTest extends CategoryTest {
  @InjectMocks ProjectEnvironmentsApiImpl environmentResource;
  @Mock EnvironmentService environmentService;
  @Mock OrgAndProjectValidationHelper orgAndProjectValidationHelper;
  @Mock ServiceOverrideService serviceOverrideService;
  @Mock EnvironmentValidationHelper environmentValidationHelper;
  @Mock AccessControlClient accessControlClient;
  @Mock ServiceEntityValidationHelper serviceEntityValidationHelper;
  @Mock EnvironmentsResourceApiUtils environmentsResourceApiUtils;
  @Mock EnvironmentFilterHelper environmentFilterHelper;

  Environment environmentEntity;
  List<NGTag> tags;
  String slug = randomAlphabetic(10);
  String name = randomAlphabetic(10);
  String account = randomAlphabetic(10);
  String org = randomAlphabetic(10);
  String project = randomAlphabetic(10);
  String service = randomAlphabetic(10);
  String yaml;
  String yamlServiceOverride;
  String description = "sample description";
  NGEnvironmentConfig ngEnvironmentConfig;
  EnvironmentResponse environmentResponse;
  EnvironmentRequest environmentRequest;
  ServiceOverrideResponse serviceOverrideResponse;
  NGServiceOverrideConfig ngServiceOverrideConfig;
  NGServiceOverridesEntity ngServiceOverridesEntity;
  ServiceOverrideRequest serviceOverrideRequest;
  private static final ConfigFileWrapper configFile3b =
      ConfigFileWrapper.builder().configFile(ConfigFile.builder().identifier("config_file3").uuid("b").build()).build();
  io.harness.spec.server.ng.model.Environment environment;
  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);

    tags = Arrays.asList(NGTag.builder().key("k1").value("v1").build());
    ngEnvironmentConfig = NGEnvironmentConfig.builder()
                              .ngEnvironmentInfoConfig(NGEnvironmentInfoConfig.builder()
                                                           .name(name)
                                                           .identifier(slug)
                                                           .orgIdentifier(org)
                                                           .projectIdentifier(project)
                                                           .tags(singletonMap("k1", "v1"))
                                                           .type(EnvironmentType.PreProduction)
                                                           .build())
                              .build();

    ngServiceOverrideConfig =
        NGServiceOverrideConfig.builder()
            .serviceOverrideInfoConfig(
                NGServiceOverrideInfoConfig.builder().serviceRef(service).environmentRef(slug).build())
            .build();

    yaml = EnvironmentMapper.toYaml(ngEnvironmentConfig);
    yamlServiceOverride = ServiceOverridesMapper.toYaml(ngServiceOverrideConfig);

    serviceOverrideRequest = new ServiceOverrideRequest();
    serviceOverrideRequest.setYaml(yamlServiceOverride);

    environmentRequest = new EnvironmentRequest();
    environmentRequest.setColor(HARNESS_BLUE);
    environmentRequest.setDescription(description);
    environmentRequest.setName(name);
    environmentRequest.setSlug(slug);
    environmentRequest.setYaml(EnvironmentMapper.toYaml(ngEnvironmentConfig));
    environmentRequest.setType(EnvironmentRequest.TypeEnum.PREPRODUCTION);

    serviceOverrideResponse = new ServiceOverrideResponse();
    serviceOverrideResponse.setYaml(yamlServiceOverride);
    serviceOverrideResponse.setProject(project);
    serviceOverrideResponse.setService(service);
    serviceOverrideResponse.setEnvironemnt(slug);
    serviceOverrideResponse.setAccount(account);
    serviceOverrideResponse.setOrg(org);

    environmentEntity = Environment.builder()
                            .accountId(account)
                            .identifier(slug)
                            .orgIdentifier(org)
                            .projectIdentifier(project)
                            .color(HARNESS_BLUE)
                            .name(name)
                            .type(EnvironmentType.PreProduction)
                            .tags(tags)
                            .version(1L)
                            .yaml(EnvironmentMapper.toYaml(ngEnvironmentConfig))
                            .build();

    ngServiceOverridesEntity = NGServiceOverridesEntity.builder()
                                   .accountId(account)
                                   .projectIdentifier(project)
                                   .orgIdentifier(org)
                                   .environmentRef(slug)
                                   .serviceRef(service)
                                   .yaml(yamlServiceOverride)
                                   .build();

    environment = new io.harness.spec.server.ng.model.Environment();
    environment.setYaml(EnvironmentMapper.toYaml(ngEnvironmentConfig));
    environment.setProject(project);
    environment.setType(io.harness.spec.server.ng.model.Environment.TypeEnum.PREPRODUCTION);
    environment.setName(name);
    environment.setTags(singletonMap("k1", "v1"));
    environment.setDescription(description);
    environment.setSlug(slug);
    environment.setAccount(account);
    environment.setColor(HARNESS_BLUE);
    environment.setOrg(org);

    environmentResponse = new EnvironmentResponse();
    environmentResponse.setEnvironment(environment);
    environmentResponse.setUpdated(123456789L);
    environmentResponse.setCreated(987654321L);
  }
  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testGet() {
    doReturn(Optional.of(environmentEntity)).when(environmentService).get(account, org, project, slug, false);
    when(environmentsResourceApiUtils.toYaml(any())).thenReturn(yaml);
    when(environmentsResourceApiUtils.toEnvironmentResponseWrapper(any())).thenReturn(environmentResponse);
    Response envResponse = environmentResource.getEnvironment(org, project, slug, account);

    assertThat(envResponse).isNotNull();
    assertThat(envResponse.getEntity()).isEqualTo(environmentResponse);
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testGetEnvironmentWithInvalidEnvironmentIdentifier() {
    String incorrectEnvironmentIdentifier = "notTheIdentifierWeNeed";
    doReturn(Optional.empty())
        .when(environmentService)
        .get(account, org, project, incorrectEnvironmentIdentifier, false);
    assertThatThrownBy(() -> environmentResource.getEnvironment(org, project, incorrectEnvironmentIdentifier, account))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testCreate() {
    doReturn(environmentEntity).when(environmentService).create(any());
    when(orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(org, project, account))
        .thenReturn(true);
    when(environmentsResourceApiUtils.toEnvironmentEntity(any(), any(), any(), any())).thenReturn(environmentEntity);
    when(environmentsResourceApiUtils.toEnvironmentResponseWrapper(any())).thenReturn(environmentResponse);
    Response envResponse = environmentResource.createEnvironment(environmentRequest, org, project, account);
    Map<String, String> environmentAttributes = new HashMap<>();
    environmentAttributes.put("type", environmentRequest.getType().toString());
    verify(accessControlClient, times(1))
        .checkForAccessOrThrow(ResourceScope.of(account, org, project),
            Resource.of(ENVIRONMENT, null, environmentAttributes), ENVIRONMENT_CREATE_PERMISSION);
    assertThat(envResponse.getEntity()).isEqualTo(environmentResponse);
    verify(orgAndProjectValidationHelper, times(1)).checkThatTheOrganizationAndProjectExists(org, project, account);
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testDelete() {
    doReturn(true).when(environmentService).delete(account, org, project, slug, null);
    Response data = environmentResource.deleteEnvironment(org, project, slug, account);
    assertThat(data.getStatus()).isEqualTo(204);
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testUpdate() {
    doReturn(environmentEntity).when(environmentService).update(environmentEntity);
    when(environmentsResourceApiUtils.toEnvironmentResponseWrapper(any())).thenReturn(environmentResponse);
    when(environmentsResourceApiUtils.toEnvironmentEntity(any(), any(), any(), any())).thenReturn(environmentEntity);
    Response response = environmentResource.updateEnvironment(environmentRequest, org, project, slug, account);
    assertThat(response).isNotNull();
    assertThat(response.getEntity()).isEqualTo(environmentResponse);
    verify(accessControlClient, times(1))
        .checkForAccessOrThrow(
            ResourceScope.of(account, org, project), Resource.of(ENVIRONMENT, slug), ENVIRONMENT_UPDATE_PERMISSION);
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testListEnvironmentsWithDESCSort() {
    when(environmentsResourceApiUtils.toYaml(any())).thenReturn(yaml);
    when(environmentFilterHelper.createCriteriaForGetList(account, org, project, false, null))
        .thenReturn(new Criteria());
    when(environmentsResourceApiUtils.toEnvironmentResponseWrapper(any())).thenReturn(environmentResponse);
    Pageable pageable =
        PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, software.wings.beans.Environment.EnvironmentKeys.createdAt));
    final Page<Environment> environments = new PageImpl<>(Collections.singletonList(environmentEntity), pageable, 1);
    doReturn(environments).when(environmentService).list(any(), any());

    Response response =
        environmentResource.getEnvironments(org, project, account, 0, 10, null, null, null, "DESC", false);
    assertThat(response.getEntity()).isNotNull();
    List<EnvironmentResponse> environmentsResponse = (List<EnvironmentResponse>) response.getEntity();
    EnvironmentResponse environmentResponseEntity = environmentsResponse.get(0);
    assertThat(environmentsResponse.size()).isEqualTo(1);
    assertThat(environmentResponseEntity).isEqualTo(environmentResponse);
    verify(accessControlClient)
        .checkForAccessOrThrow(ResourceScope.of(account, org, project), Resource.of(ENVIRONMENT, null),
            ENVIRONMENT_VIEW_PERMISSION, "Unauthorized to list environments");
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testGetServiceOverride() {
    doReturn(Optional.of(ngServiceOverridesEntity))
        .when(serviceOverrideService)
        .get(account, org, project, slug, service);
    when(orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(org, project, account))
        .thenReturn(true);
    when(environmentValidationHelper.checkThatEnvExists(account, org, project, slug)).thenReturn(true);
    doNothing().when(serviceEntityValidationHelper).checkThatServiceExists(account, org, project, service);
    when(environmentsResourceApiUtils.toServiceOverrideResponse(any())).thenReturn(serviceOverrideResponse);
    Response envResponse = environmentResource.getEnvServiceOverride(org, project, slug, service, account);
    assertThat(envResponse).isNotNull();
    assertThat(envResponse.getEntity()).isEqualTo(serviceOverrideResponse);
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testCreateServiceOverride() {
    doReturn(ngServiceOverridesEntity)
        .when(environmentsResourceApiUtils)
        .toServiceOverridesEntity(account, serviceOverrideRequest, org, project, slug, service);
    doReturn(serviceOverrideResponse)
        .when(environmentsResourceApiUtils)
        .toServiceOverrideResponse(ngServiceOverridesEntity);

    doReturn(ngServiceOverridesEntity).when(serviceOverrideService).upsert(ngServiceOverridesEntity);
    doReturn(Optional.of(ngServiceOverridesEntity)).when(serviceOverrideService).get(any(), any(), any(), any(), any());
    when(orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(org, project, account))
        .thenReturn(true);
    AccessCheckResponseDTO accessCheckResponseDTO =
        AccessCheckResponseDTO.builder()
            .accessControlList(Collections.singletonList(AccessControlDTO.builder().permitted(true).build()))
            .build();
    when(environmentValidationHelper.checkThatEnvExists(account, org, project, slug)).thenReturn(true);
    doNothing().when(environmentsResourceApiUtils).checkDuplicateManifestIdentifiersWithIn(any());
    doNothing().when(environmentsResourceApiUtils).checkDuplicateConfigFilesIdentifiersWithIn(any());
    when(accessControlClient.checkForAccess(any())).thenReturn(accessCheckResponseDTO);
    Response serviceOverride =
        environmentResource.updateEnvServiceOverride(org, project, slug, service, serviceOverrideRequest, account);
    assertThat(serviceOverride.getEntity()).isEqualTo(serviceOverrideResponse);
    verify(orgAndProjectValidationHelper, times(1)).checkThatTheOrganizationAndProjectExists(org, project, account);
    verify(environmentValidationHelper, times(1)).checkThatEnvExists(account, org, project, slug);
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testDeleteServiceOverride() {
    when(orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(org, project, account))
        .thenReturn(true);
    AccessCheckResponseDTO accessCheckResponseDTO =
        AccessCheckResponseDTO.builder()
            .accessControlList(Collections.singletonList(AccessControlDTO.builder().permitted(true).build()))
            .build();
    when(environmentValidationHelper.checkThatEnvExists(account, org, project, slug)).thenReturn(true);
    doNothing().when(environmentsResourceApiUtils).checkDuplicateManifestIdentifiersWithIn(any());
    doNothing().when(environmentsResourceApiUtils).checkDuplicateConfigFilesIdentifiersWithIn(any());
    when(accessControlClient.checkForAccess(any())).thenReturn(accessCheckResponseDTO);
    Response data = environmentResource.deleteEnvServiceOverride(org, project, slug, service, account);
    assertThat(data.getStatus()).isEqualTo(204);
    verify(orgAndProjectValidationHelper, times(1)).checkThatTheOrganizationAndProjectExists(org, project, account);
    verify(environmentValidationHelper, times(1)).checkThatEnvExists(account, org, project, slug);
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testUpdateServiceOverride() {
    doReturn(ngServiceOverridesEntity)
        .when(environmentsResourceApiUtils)
        .toServiceOverridesEntity(account, serviceOverrideRequest, org, project, slug, service);
    doReturn(serviceOverrideResponse)
        .when(environmentsResourceApiUtils)
        .toServiceOverrideResponse(ngServiceOverridesEntity);

    doReturn(ngServiceOverridesEntity).when(serviceOverrideService).upsert(ngServiceOverridesEntity);
    doReturn(Optional.of(ngServiceOverridesEntity)).when(serviceOverrideService).get(any(), any(), any(), any(), any());
    when(orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(org, project, account))
        .thenReturn(true);
    AccessCheckResponseDTO accessCheckResponseDTO =
        AccessCheckResponseDTO.builder()
            .accessControlList(Collections.singletonList(AccessControlDTO.builder().permitted(true).build()))
            .build();
    when(environmentValidationHelper.checkThatEnvExists(account, org, project, slug)).thenReturn(true);
    doNothing().when(environmentsResourceApiUtils).checkDuplicateManifestIdentifiersWithIn(any());
    doNothing().when(environmentsResourceApiUtils).checkDuplicateConfigFilesIdentifiersWithIn(any());
    when(accessControlClient.checkForAccess(any())).thenReturn(accessCheckResponseDTO);
    Response serviceOverride =
        environmentResource.updateEnvServiceOverride(org, project, slug, service, serviceOverrideRequest, account);
    assertThat(serviceOverride.getEntity()).isEqualTo(serviceOverrideResponse);
    verify(orgAndProjectValidationHelper, times(1)).checkThatTheOrganizationAndProjectExists(org, project, account);
    verify(environmentValidationHelper, times(1)).checkThatEnvExists(account, org, project, slug);
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testListServiceOverridesWithDESCSort() {
    when(environmentsResourceApiUtils.toYaml(any())).thenReturn(yaml);
    when(environmentsResourceApiUtils.toServiceOverrideResponse(any())).thenReturn(serviceOverrideResponse);
    when(environmentFilterHelper.createCriteriaForGetServiceOverrides(any(), any(), any(), any(), any()))
        .thenReturn(new Criteria());
    Pageable pageable =
        PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, software.wings.beans.Environment.EnvironmentKeys.createdAt));
    final Page<NGServiceOverridesEntity> serviceOverrides =
        new PageImpl<>(Collections.singletonList(ngServiceOverridesEntity), pageable, 1);
    doReturn(serviceOverrides).when(serviceOverrideService).list(any(), any());
    Response response = environmentResource.getEnvServiceOverrides(org, project, slug, null, account, "DESC", 0, 10);
    assertThat(response.getEntity()).isNotNull();
    List<ServiceOverrideResponse> serviceOverridesList = (List<ServiceOverrideResponse>) response.getEntity();
    ServiceOverrideResponse serviceOverrideResponseEntity = serviceOverridesList.get(0);
    assertThat(serviceOverridesList.size()).isEqualTo(1);
    assertThat(serviceOverrideResponseEntity).isEqualTo(serviceOverrideResponse);
    verify(accessControlClient)
        .checkForAccessOrThrow(ResourceScope.of(account, org, project), Resource.of(ENVIRONMENT, slug),
            ENVIRONMENT_VIEW_PERMISSION, "Unauthorized to view environment");
    verify(orgAndProjectValidationHelper, times(1)).checkThatTheOrganizationAndProjectExists(org, project, account);
    verify(environmentValidationHelper, times(1)).checkThatEnvExists(account, org, project, slug);
  }
}
