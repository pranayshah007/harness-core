/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.envGroup.resource;

import static io.harness.pms.rbac.NGResourceType.ENVIRONMENT;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;
import static io.harness.rule.OwnerRule.vivekveman;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.category.element.UnitTests;
import io.harness.cdng.envGroup.beans.EnvironmentGroupConfig;
import io.harness.cdng.envGroup.beans.EnvironmentGroupEntity;
import io.harness.cdng.envGroup.beans.EnvironmentGroupEntity.EnvironmentGroupKeys;
import io.harness.cdng.envGroup.beans.EnvironmentGroupWrapperConfig;
import io.harness.cdng.envGroup.mappers.EnvironmentGroupMapper;
import io.harness.cdng.envGroup.services.EnvironmentGroupService;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.envGroup.dto.EnvironmentGroupDeleteResponse;
import io.harness.ng.core.envGroup.dto.EnvironmentGroupRequestDTO;
import io.harness.ng.core.envGroup.dto.EnvironmentGroupResponse;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.dto.EnvironmentResponse;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rbac.CDNGRbacPermissions;
import io.harness.rule.Owner;
import io.harness.utils.NGFeatureFlagHelperService;
import io.harness.utils.PageUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import javax.ws.rs.NotFoundException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

public class EnvironmentGroupResourceTest extends CategoryTest {
  @Mock private EnvironmentGroupService environmentGroupService;
  @Mock private EnvironmentService environmentService;
  @Mock private AccessControlClient accessControlClient;
  @Mock private EnvironmentGroupRbacHelper environmentGroupRbacHelper;
  @Mock private NGFeatureFlagHelperService featureFlagHelperService;

  @InjectMocks private EnvironmentGroupResource environmentGroupResource;

  String ACC_ID = "accId";
  String ORG_ID = "orgId";
  String PRO_ID = "proId";
  String ENV_GROUP_ID = "newEnvGroup";

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  private EnvironmentGroupEntity getEntity() {
    return EnvironmentGroupEntity.builder()
        .accountId(ACC_ID)
        .orgIdentifier(ORG_ID)
        .projectIdentifier(PRO_ID)
        .identifier(ENV_GROUP_ID)
        .name("newEnvGroup")
        .envIdentifiers(Arrays.asList("env1", "env2"))
        .color("newCol")
        .createdAt(1L)
        .lastModifiedAt(2L)
        .yaml("yaml")
        .build();
  }

  private EnvironmentGroupRequestDTO getRequestDto(String yaml) throws IOException {
    EnvironmentGroupConfig environmentGroupConfig =
        YamlUtils.read(yaml, EnvironmentGroupWrapperConfig.class).getEnvironmentGroupConfig();

    return EnvironmentGroupRequestDTO.builder()
        .orgIdentifier(environmentGroupConfig.getOrgIdentifier())
        .projectIdentifier(environmentGroupConfig.getProjectIdentifier())
        .identifier(environmentGroupConfig.getIdentifier())
        .color("newCol")
        .yaml(yaml)
        .build();
  }

  private String getYamlFieldFromGivenFileName(String file) {
    ClassLoader classLoader = this.getClass().getClassLoader();
    InputStream yamlFile = classLoader.getResourceAsStream(file);
    assertThat(yamlFile).isNotNull();

    String yaml = new Scanner(yamlFile, "UTF-8").useDelimiter("\\A").next();
    return yaml;
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetApi() {
    EnvironmentGroupEntity environmentGroupEntity = getEntity();

    // case1: get optional entity call returns non empty value
    doReturn(Optional.ofNullable(environmentGroupEntity))
        .when(environmentGroupService)
        .get(ACC_ID, ORG_ID, PRO_ID, ENV_GROUP_ID, false);
    doReturn(new ArrayList<>())
        .when(environmentService)
        .fetchesNonDeletedEnvironmentFromListOfIdentifiers(
            ACC_ID, ORG_ID, PRO_ID, environmentGroupEntity.getEnvIdentifiers());
    ResponseDTO<EnvironmentGroupResponse> responseDTO =
        environmentGroupResource.get(ENV_GROUP_ID, ACC_ID, ORG_ID, PRO_ID, false, null);
    assertThat(responseDTO).isNotNull();
    assertThat(responseDTO.getData().getEnvGroup().getIdentifier()).isEqualTo(ENV_GROUP_ID);

    // case2: get function returns empty object
    Optional<EnvironmentGroupEntity> optional = Optional.empty();
    doReturn(optional).when(environmentGroupService).get(ACC_ID, ORG_ID, PRO_ID, ENV_GROUP_ID, false);
    assertThatThrownBy(() -> environmentGroupResource.get(ENV_GROUP_ID, ACC_ID, ORG_ID, PRO_ID, false, null))
        .isInstanceOf(NotFoundException.class)
        .hasMessage("Environment Group with identifier [newEnvGroup] in project [proId], org [orgId] not found");
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testCreate() throws IOException {
    String yaml = getYamlFieldFromGivenFileName("cdng/envGroup/mappers/validEnvGroup.yml");
    EnvironmentGroupRequestDTO environmentGroupRequestDTO = getRequestDto(yaml);

    EnvironmentGroupConfig environmentGroupConfig =
        YamlUtils.read(yaml, EnvironmentGroupWrapperConfig.class).getEnvironmentGroupConfig();
    String projectIdentifier = environmentGroupRequestDTO.getProjectIdentifier();
    String orgIdentifier = environmentGroupRequestDTO.getOrgIdentifier();
    String name = environmentGroupConfig.getName();
    String identifier = environmentGroupRequestDTO.getIdentifier();
    List<String> envIdentifiers = environmentGroupConfig.getEnvIdentifiers();

    EnvironmentGroupEntity environmentGroupEntity =
        EnvironmentGroupMapper.toEnvironmentGroupEntity(ACC_ID, environmentGroupRequestDTO);
    doReturn(environmentGroupEntity).when(environmentGroupService).create(environmentGroupEntity);
    doReturn(new ArrayList<>())
        .when(environmentService)
        .fetchesNonDeletedEnvironmentFromListOfIdentifiers(
            ACC_ID, orgIdentifier, projectIdentifier, environmentGroupEntity.getEnvIdentifiers());
    ResponseDTO<EnvironmentGroupResponse> responseDTO =
        environmentGroupResource.create(ACC_ID, environmentGroupRequestDTO, null);
    assertThat(responseDTO).isNotNull();
    assertThat(responseDTO.getData().getEnvGroup().getIdentifier()).isEqualTo(identifier);
    assertThat(responseDTO.getData().getEnvGroup().getName()).isEqualTo(name);
    assertThat(responseDTO.getData().getEnvGroup().getEnvIdentifiers())
        .containsExactly(envIdentifiers.get(0), envIdentifiers.get(1));
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testDelete() {
    EnvironmentGroupEntity entity = getEntity();
    doReturn(entity).when(environmentGroupService).delete(ACC_ID, ORG_ID, PRO_ID, ENV_GROUP_ID, null, false);
    ResponseDTO<EnvironmentGroupDeleteResponse> deleteDTO =
        environmentGroupResource.delete(null, ENV_GROUP_ID, ACC_ID, ORG_ID, PRO_ID, null, false);
    assertThat(deleteDTO).isNotNull();
    assertThat(deleteDTO.getData().getDeleted()).isEqualTo(entity.getDeleted());
    assertThat(deleteDTO.getData().getIdentifier()).isEqualTo(ENV_GROUP_ID);
    assertThat(deleteDTO.getData().getAccountId()).isEqualTo(ACC_ID);
    assertThat(deleteDTO.getData().getOrgIdentifier()).isEqualTo(ORG_ID);
    assertThat(deleteDTO.getData().getProjectIdentifier()).isEqualTo(PRO_ID);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testListApi() {
    String searchTerm = "searchTerm";
    String filterIdentifier = "filterIdentifier";
    List<EnvironmentGroupEntity> envGroupEntityList = Arrays.asList(getEntity());
    Pageable pageRequest = PageUtils.getPageRequest(
        0, 1, null, Sort.by(Sort.Direction.DESC, EnvironmentGroupEntity.EnvironmentGroupKeys.lastModifiedAt));
    Criteria criteria = new Criteria();

    // case1: without envGroupIds
    doReturn(criteria)
        .when(environmentGroupService)
        .formCriteria(ACC_ID, ORG_ID, PRO_ID, false, searchTerm, filterIdentifier, null, false);
    doReturn(new PageImpl<>(envGroupEntityList))
        .when(environmentGroupService)
        .list(criteria, pageRequest, PRO_ID, ORG_ID, ACC_ID);
    doReturn(new PageImpl<>(envGroupEntityList))
        .when(environmentGroupService)
        .list(criteria, Pageable.unpaged(), PRO_ID, ORG_ID, ACC_ID);
    doReturn(envGroupEntityList).when(environmentGroupRbacHelper).getPermittedEnvironmentGroupList(any());
    ResponseDTO<PageResponse<EnvironmentGroupResponse>> pageResponseResponseDTO =
        environmentGroupResource.listEnvironmentGroup(
            ACC_ID, ORG_ID, PRO_ID, null, searchTerm, 0, 1, null, filterIdentifier, null, null, false);
    assertThat(pageResponseResponseDTO).isNotNull();
    assertThat(pageResponseResponseDTO.getData().getPageItemCount()).isEqualTo(1L);

    // case1: with envGroupIds
    List<String> envGroupIds = Arrays.asList("envGroup1");
    criteria.and(EnvironmentGroupKeys.envIdentifiers).in(envGroupIds);
    doReturn(criteria)
        .when(environmentGroupService)
        .formCriteria(ACC_ID, ORG_ID, PRO_ID, false, searchTerm, filterIdentifier, null, false);
    pageResponseResponseDTO = environmentGroupResource.listEnvironmentGroup(
        ACC_ID, ORG_ID, PRO_ID, null, searchTerm, 0, 1, null, filterIdentifier, null, null, false);
    assertThat(pageResponseResponseDTO).isNotNull();
    assertThat(pageResponseResponseDTO.getData().getPageItemCount()).isEqualTo(1L);
  }
  @Test
  @Owner(developers = vivekveman)
  @Category(UnitTests.class)
  public void testListApiForUserWithoutAccess() {
    String searchTerm = "searchTerm";
    String filterIdentifier = "filterIdentifier";
    List<EnvironmentGroupEntity> envGroupEntityList = Arrays.asList(getEntity());
    Pageable pageRequest = PageUtils.getPageRequest(
        0, 1, null, Sort.by(Sort.Direction.DESC, EnvironmentGroupEntity.EnvironmentGroupKeys.lastModifiedAt));
    Criteria criteria = new Criteria();

    // case1: without envGroupIds
    doReturn(criteria)
        .when(environmentGroupService)
        .formCriteria(ACC_ID, ORG_ID, PRO_ID, false, searchTerm, filterIdentifier, null, false);
    doReturn(new PageImpl<>(envGroupEntityList))
        .when(environmentGroupService)
        .list(criteria, pageRequest, PRO_ID, ORG_ID, ACC_ID);
    doReturn(new PageImpl<>(envGroupEntityList))
        .when(environmentGroupService)
        .list(criteria, Pageable.unpaged(), PRO_ID, ORG_ID, ACC_ID);
    doReturn(Collections.emptyList()).when(environmentGroupRbacHelper).getPermittedEnvironmentGroupList(any());
    ResponseDTO<PageResponse<EnvironmentGroupResponse>> pageResponseResponseDTO =
        environmentGroupResource.listEnvironmentGroup(
            ACC_ID, ORG_ID, PRO_ID, null, searchTerm, 0, 1, null, filterIdentifier, null, null, false);
    assertThat(pageResponseResponseDTO).isNotNull();
    assertThat(pageResponseResponseDTO.getData().getPageItemCount()).isEqualTo(0L);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testUpdate() throws IOException {
    String yaml = getYamlFieldFromGivenFileName("cdng/envGroup/mappers/validEnvGroup.yml");

    EnvironmentGroupRequestDTO environmentGroupRequestDTO = getRequestDto(yaml);
    EnvironmentGroupEntity environmentGroupEntity =
        EnvironmentGroupMapper.toEnvironmentGroupEntity(ACC_ID, environmentGroupRequestDTO);

    String projectIdentifier = environmentGroupRequestDTO.getProjectIdentifier();
    String orgIdentifier = environmentGroupRequestDTO.getOrgIdentifier();
    String identifier = environmentGroupRequestDTO.getIdentifier();

    // case1: updating identifier of envGroup
    doReturn(environmentGroupEntity).when(environmentGroupService).create(environmentGroupEntity);
    // passing identifier in api different from that of in yaml
    assertThatThrownBy(
        () -> environmentGroupResource.update(null, "ENV_GROUP_ID", ACC_ID, environmentGroupRequestDTO, null))
        .isInstanceOf(InvalidRequestException.class);

    // case2: testing other updates
    doReturn(new ArrayList<>())
        .when(environmentService)
        .fetchesNonDeletedEnvironmentFromListOfIdentifiers(
            ACC_ID, orgIdentifier, projectIdentifier, environmentGroupEntity.getEnvIdentifiers());
    doReturn(environmentGroupEntity.withVersion(10L)).when(environmentGroupService).update(environmentGroupEntity);
    assertThatCode(() -> environmentGroupResource.update(null, identifier, ACC_ID, environmentGroupRequestDTO, null))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetEnvironmentResponse() {
    EnvironmentGroupEntity entity = getEntity();
    Environment environment1 = Environment.builder().identifier("id1").build();
    Environment environment2 = Environment.builder().identifier("id2").build();

    doReturn(Arrays.asList(environment1, environment2))
        .when(environmentService)
        .fetchesNonDeletedEnvironmentFromListOfIdentifiers(
            ACC_ID, entity.getOrgIdentifier(), entity.getProjectIdentifier(), entity.getEnvIdentifiers());
    List<EnvironmentResponse> environmentResponses = environmentGroupResource.getEnvironmentResponses(entity);

    assertThat(environmentResponses.size()).isEqualTo(2);
    assertThat(environmentResponses.get(0).getEnvironment().getIdentifier()).isEqualTo("id1");
    assertThat(environmentResponses.get(1).getEnvironment().getIdentifier()).isEqualTo("id2");
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void validatePermissionForEnvironment() {
    EnvironmentGroupEntity entity = getEntity();
    String env1 = entity.getEnvIdentifiers().get(0);
    String env2 = entity.getEnvIdentifiers().get(1);
    environmentGroupResource.validatePermissionForEnvironment(entity);
    verify(accessControlClient, times(1))
        .checkForAccessOrThrow(ResourceScope.of(ACC_ID, ORG_ID, PRO_ID), Resource.of(ENVIRONMENT, env1),
            CDNGRbacPermissions.ENVIRONMENT_VIEW_PERMISSION);

    verify(accessControlClient, times(1))
        .checkForAccessOrThrow(ResourceScope.of(ACC_ID, ORG_ID, PRO_ID), Resource.of(ENVIRONMENT, env2),
            CDNGRbacPermissions.ENVIRONMENT_VIEW_PERMISSION);
  }
}
