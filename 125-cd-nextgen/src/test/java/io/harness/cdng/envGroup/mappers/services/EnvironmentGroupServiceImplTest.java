/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.envGroup.mappers.services;

import static io.harness.rule.OwnerRule.HINGER;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.EntityType;
import io.harness.beans.IdentifierRef;
import io.harness.category.element.UnitTests;
import io.harness.cdng.envGroup.beans.EnvironmentGroupEntity;
import io.harness.cdng.envGroup.beans.EnvironmentGroupEntity.EnvironmentGroupKeys;
import io.harness.cdng.envGroup.beans.EnvironmentGroupFilterPropertiesDTO;
import io.harness.cdng.envGroup.services.EnvironmentGroupServiceHelper;
import io.harness.cdng.envGroup.services.EnvironmentGroupServiceImpl;
import io.harness.eventsframework.EventsFrameworkMetadataConstants;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.producer.Message;
import io.harness.eventsframework.protohelper.IdentifierRefProtoDTOHelper;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.eventsframework.schemas.entity.IdentifierRefProtoDTO;
import io.harness.eventsframework.schemas.entitysetupusage.EntitySetupUsageCreateV2DTO;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.entitysetupusage.dto.EntitySetupUsageDTO;
import io.harness.ng.core.entitysetupusage.service.EntitySetupUsageService;
import io.harness.repositories.envGroup.EnvironmentGroupRepository;
import io.harness.rule.Owner;

import com.google.protobuf.ByteString;
import com.google.protobuf.StringValue;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bson.Document;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

public class EnvironmentGroupServiceImplTest extends CategoryTest {
  private String ACC_ID = "accId";
  private String ORG_ID = "orgId";
  private String PRO_ID = "proId";
  private String ENV_GROUP_ID = "envGroupId";

  @Mock private EnvironmentGroupRepository environmentGroupRepository;
  @Mock private Producer eventProducer;
  @Mock private EntitySetupUsageService entitySetupUsageService;
  @Mock private EnvironmentGroupServiceHelper environmentGroupServiceHelper;

  @InjectMocks private EnvironmentGroupServiceImpl environmentGroupService;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGet() {
    environmentGroupService.get(ACC_ID, ORG_ID, PRO_ID, "envGroup", false);
    verify(environmentGroupRepository, times(1))
        .findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndDeletedNot(
            ACC_ID, ORG_ID, PRO_ID, "envGroup", true);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testCreate() {
    EnvironmentGroupEntity savedEntity = getEnvironmentGroupEntity(ACC_ID, ORG_ID, PRO_ID, ENV_GROUP_ID);
    doReturn(savedEntity).when(environmentGroupRepository).create(savedEntity);

    environmentGroupService.create(savedEntity);
    verify(environmentGroupRepository, times(1)).create(savedEntity);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testList() {
    Criteria criteria = new Criteria();
    Pageable pageRequest =
        PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, EnvironmentGroupEntity.EnvironmentGroupKeys.createdAt));
    environmentGroupService.list(criteria, (Pageable) pageRequest, PRO_ID, ORG_ID, ACC_ID);
    verify(environmentGroupRepository, times(1)).list(criteria, pageRequest, PRO_ID, ORG_ID, ACC_ID);
  }

  private EnvironmentGroupEntity getEnvironmentGroupEntity(String acc, String org, String pro, String envGroupId) {
    return EnvironmentGroupEntity.builder()
        .accountId(acc)
        .orgIdentifier(org)
        .projectIdentifier(pro)
        .identifier(envGroupId)
        .name("envName")
        .envIdentifiers(Arrays.asList("env1", "env2"))
        .build();
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testDelete() {
    // version is null
    EnvironmentGroupEntity entity = getEnvironmentGroupEntity(ACC_ID, ORG_ID, PRO_ID, ENV_GROUP_ID);
    doReturn(Optional.of(entity))
        .when(environmentGroupRepository)
        .findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndDeletedNot(
            ACC_ID, ORG_ID, PRO_ID, ENV_GROUP_ID, true);

    IdentifierRef identifierRef = IdentifierRef.builder()
                                      .accountIdentifier(entity.getAccountId())
                                      .orgIdentifier(entity.getOrgIdentifier())
                                      .projectIdentifier(entity.getProjectIdentifier())
                                      .identifier(entity.getIdentifier())
                                      .build();

    PageImpl<EntitySetupUsageDTO> entitySetupUsageDTOPage = new PageImpl<>(Arrays.asList());
    doReturn(entitySetupUsageDTOPage)
        .when(entitySetupUsageService)
        .listAllEntityUsage(
            0, 10, entity.getAccountId(), identifierRef.getFullyQualifiedName(), EntityType.ENVIRONMENT_GROUP, "");
    EnvironmentGroupEntity deletedEntity = entity.withDeleted(true);
    doReturn(true).when(environmentGroupRepository).deleteEnvGroup(deletedEntity, false);
    EnvironmentGroupEntity isDeletedEntity =
        environmentGroupService.delete(ACC_ID, ORG_ID, PRO_ID, ENV_GROUP_ID, null, false);

    assertThat(isDeletedEntity.getDeleted()).isTrue();

    // case2: version is not null and is not equal with version of entity
    EnvironmentGroupEntity entityWithVersion = entity.withVersion(10L);
    doReturn(Optional.of(entityWithVersion))
        .when(environmentGroupRepository)
        .findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndDeletedNot(
            ACC_ID, ORG_ID, PRO_ID, ENV_GROUP_ID, true);
    // making version equal to 20L which is not equal to 10L  in entityWithVersion
    assertThatThrownBy(() -> environmentGroupService.delete(ACC_ID, ORG_ID, PRO_ID, ENV_GROUP_ID, 20L, false))
        .isInstanceOf(InvalidRequestException.class);

    // case3: version is same as that in entity. Here entity fetched having deleted as false and should throw error
    EnvironmentGroupEntity nonDeletedEntity = entity.withDeleted(false);
    doReturn(false).when(environmentGroupRepository).deleteEnvGroup(deletedEntity, false);
    assertThatThrownBy(() -> environmentGroupService.delete(ACC_ID, ORG_ID, PRO_ID, ENV_GROUP_ID, 10L, false))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testUpdate() {
    EnvironmentGroupEntity originalEntity = getEnvironmentGroupEntity(ACC_ID, ORG_ID, PRO_ID, ENV_GROUP_ID);
    originalEntity = originalEntity.withName("oldName")
                         .withColor("oldColor")
                         .withLastModifiedAt(10L)
                         .withDescription("oldDes")
                         .withEnvIdentifiers(Collections.singletonList("env1"))
                         .withYaml("oldYaml");

    EnvironmentGroupEntity updatedEntity = originalEntity.withName("newName")
                                               .withColor("newColor")
                                               .withLastModifiedAt(20L)
                                               .withDescription("newDes")
                                               .withEnvIdentifiers(Arrays.asList("env1", "env2"))
                                               .withYaml("newYaml");

    doReturn(Optional.of(originalEntity))
        .when(environmentGroupRepository)
        .findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndDeletedNot(
            ACC_ID, ORG_ID, PRO_ID, ENV_GROUP_ID, true);

    ArgumentCaptor<EnvironmentGroupEntity> captorForUpdatedEntity =
        ArgumentCaptor.forClass(EnvironmentGroupEntity.class);
    ArgumentCaptor<EnvironmentGroupEntity> captorForOriginalEntity =
        ArgumentCaptor.forClass(EnvironmentGroupEntity.class);
    doReturn(updatedEntity)
        .when(environmentGroupRepository)
        .update(captorForUpdatedEntity.capture(), captorForOriginalEntity.capture(), any(Criteria.class));
    environmentGroupService.update(updatedEntity);

    EnvironmentGroupEntity capturedUpdatedEntity = captorForUpdatedEntity.getValue();
    assertThat(capturedUpdatedEntity.getName()).isEqualTo("newName");
    assertThat(capturedUpdatedEntity.getDescription()).isEqualTo("newDes");
    assertThat(capturedUpdatedEntity.getColor()).isEqualTo("newColor");
    assertThat(capturedUpdatedEntity.getLastModifiedAt()).isNotEqualTo(10L);
    assertThat(capturedUpdatedEntity.getEnvIdentifiers()).contains("env2");
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testCheckThatEnvironmentGroupIsNotReferredByOthers() {
    EnvironmentGroupEntity entity = getEnvironmentGroupEntity(ACC_ID, ORG_ID, PRO_ID, ENV_GROUP_ID);

    IdentifierRef identifierRef = IdentifierRef.builder()
                                      .accountIdentifier(entity.getAccountId())
                                      .orgIdentifier(entity.getOrgIdentifier())
                                      .projectIdentifier(entity.getProjectIdentifier())
                                      .identifier(entity.getIdentifier())
                                      .build();

    // Case1: Referred Entity is Empty
    PageImpl<EntitySetupUsageDTO> entitySetupUsageDTOPage = new PageImpl<>(Arrays.asList());
    doReturn(entitySetupUsageDTOPage)
        .when(entitySetupUsageService)
        .listAllEntityUsage(
            0, 10, entity.getAccountId(), identifierRef.getFullyQualifiedName(), EntityType.ENVIRONMENT_GROUP, "");

    assertThatCode(() -> environmentGroupService.checkThatEnvironmentGroupIsNotReferredByOthers(entity))
        .doesNotThrowAnyException();

    // Case2: Referred Entity list is Non-Empty
    entitySetupUsageDTOPage = new PageImpl<>(Arrays.asList(EntitySetupUsageDTO.builder().build()));
    doReturn(entitySetupUsageDTOPage)
        .when(entitySetupUsageService)
        .listAllEntityUsage(
            0, 10, entity.getAccountId(), identifierRef.getFullyQualifiedName(), EntityType.ENVIRONMENT_GROUP, "");

    assertThatThrownBy(() -> environmentGroupService.checkThatEnvironmentGroupIsNotReferredByOthers(entity));
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetEnvReferredEntities() {
    EnvironmentGroupEntity entity = getEnvironmentGroupEntity(ACC_ID, ORG_ID, PRO_ID, ENV_GROUP_ID);

    IdentifierRefProtoDTO refForEnv1 = IdentifierRefProtoDTO.newBuilder()
                                           .setAccountIdentifier(StringValue.of(entity.getAccountId()))
                                           .setOrgIdentifier(StringValue.of(entity.getOrgIdentifier()))
                                           .setProjectIdentifier(StringValue.of(entity.getProjectIdentifier()))
                                           .setIdentifier(StringValue.of(entity.getEnvIdentifiers().get(0)))
                                           .build();

    IdentifierRefProtoDTO refForEnv2 = IdentifierRefProtoDTO.newBuilder()
                                           .setAccountIdentifier(StringValue.of(entity.getAccountId()))
                                           .setOrgIdentifier(StringValue.of(entity.getOrgIdentifier()))
                                           .setProjectIdentifier(StringValue.of(entity.getProjectIdentifier()))
                                           .setIdentifier(StringValue.of(entity.getEnvIdentifiers().get(1)))
                                           .build();

    List<EntityDetailProtoDTO> envReferredEntities = environmentGroupService.getEnvReferredEntities(entity);
    assertThat(envReferredEntities.size()).isEqualTo(2);
    assertThat(envReferredEntities.get(0).getType()).isEqualTo(EntityTypeProtoEnum.ENVIRONMENT);
    assertThat(envReferredEntities.get(0).getIdentifierRef()).isEqualTo(refForEnv1);
    assertThat(envReferredEntities.get(1).getType()).isEqualTo(EntityTypeProtoEnum.ENVIRONMENT);
    assertThat(envReferredEntities.get(1).getIdentifierRef()).isEqualTo(refForEnv2);

    // Case2: When envIdentifiersList is Empty
    entity = entity.withEnvIdentifiers(Arrays.asList());
    assertThat(environmentGroupService.getEnvReferredEntities(entity).size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testSetUpUsagesWithNonDeletedEntity() {
    EnvironmentGroupEntity entity = getEnvironmentGroupEntity(ACC_ID, ORG_ID, PRO_ID, ENV_GROUP_ID);

    ArgumentCaptor<Message> captorForEvent = ArgumentCaptor.forClass(Message.class);
    doReturn(null).when(eventProducer).send(captorForEvent.capture());
    environmentGroupService.setupUsagesForEnvironmentList(entity);

    Message value = captorForEvent.getValue();
    Map<String, String> metadataMap = value.getMetadataMap();
    assertThat(metadataMap.size()).isGreaterThan(0);
    assertThat(metadataMap.get("accountId")).isEqualTo(ACC_ID);
    assertThat(metadataMap.get(EventsFrameworkMetadataConstants.REFERRED_ENTITY_TYPE))
        .isEqualTo(EntityTypeProtoEnum.ENVIRONMENT.name());
    assertThat(metadataMap.get(EventsFrameworkMetadataConstants.ACTION))
        .isEqualTo(EventsFrameworkMetadataConstants.FLUSH_CREATE_ACTION);

    // Data
    List<EntityDetailProtoDTO> referredEntities = environmentGroupService.getEnvReferredEntities(entity);

    EntityDetailProtoDTO envGroupDetails =
        EntityDetailProtoDTO.newBuilder()
            .setIdentifierRef(IdentifierRefProtoDTOHelper.createIdentifierRefProtoDTO(entity.getAccountId(),
                entity.getOrgIdentifier(), entity.getProjectIdentifier(), entity.getIdentifier()))
            .setType(EntityTypeProtoEnum.ENVIRONMENT_GROUP)
            .setName(entity.getName())
            .build();

    EntitySetupUsageCreateV2DTO entityReferenceDTO = EntitySetupUsageCreateV2DTO.newBuilder()
                                                         .setAccountIdentifier(entity.getAccountId())
                                                         .setReferredByEntity(envGroupDetails)
                                                         .setDeleteOldReferredByRecords(true)
                                                         .addAllReferredEntities(referredEntities)
                                                         .build();
    ByteString data = value.getData();

    assertThat(data).isEqualTo(entityReferenceDTO.toByteString());
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testSetUpUsagesWithDeletedEntity() {
    EnvironmentGroupEntity entity = getEnvironmentGroupEntity(ACC_ID, ORG_ID, PRO_ID, ENV_GROUP_ID);

    entity = entity.withDeleted(true);

    ArgumentCaptor<Message> captorForEvent = ArgumentCaptor.forClass(Message.class);
    doReturn(null).when(eventProducer).send(captorForEvent.capture());
    environmentGroupService.setupUsagesForEnvironmentList(entity);

    Message value = captorForEvent.getValue();
    Map<String, String> metadataMap = value.getMetadataMap();
    assertThat(metadataMap.size()).isGreaterThan(0);
    assertThat(metadataMap.get("accountId")).isEqualTo(ACC_ID);
    assertThat(metadataMap.get(EventsFrameworkMetadataConstants.REFERRED_ENTITY_TYPE))
        .isEqualTo(EntityTypeProtoEnum.ENVIRONMENT.name());
    assertThat(metadataMap.get(EventsFrameworkMetadataConstants.ACTION))
        .isEqualTo(EventsFrameworkMetadataConstants.FLUSH_CREATE_ACTION);

    // Data
    EntityDetailProtoDTO envGroupDetails =
        EntityDetailProtoDTO.newBuilder()
            .setIdentifierRef(IdentifierRefProtoDTOHelper.createIdentifierRefProtoDTO(entity.getAccountId(),
                entity.getOrgIdentifier(), entity.getProjectIdentifier(), entity.getIdentifier()))
            .setType(EntityTypeProtoEnum.ENVIRONMENT_GROUP)
            .setName(entity.getName())
            .build();

    EntitySetupUsageCreateV2DTO entityReferenceDTO = EntitySetupUsageCreateV2DTO.newBuilder()
                                                         .setAccountIdentifier(entity.getAccountId())
                                                         .setReferredByEntity(envGroupDetails)
                                                         .setDeleteOldReferredByRecords(true)
                                                         .build();
    ByteString data = value.getData();

    assertThat(data).isEqualTo(entityReferenceDTO.toByteString());
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testFormCriteria() {
    // CASE1: search term as null and deleted is false
    Criteria actualCriteria =
        environmentGroupService.formCriteria(ACC_ID, ORG_ID, PRO_ID, false, null, null, null, false);
    Document criteriaObject = actualCriteria.getCriteriaObject();
    assertThat(criteriaObject.get(EnvironmentGroupKeys.accountId)).isEqualTo(ACC_ID);
    assertThat(criteriaObject.get(EnvironmentGroupKeys.orgIdentifier)).isEqualTo(ORG_ID);
    assertThat(criteriaObject.get(EnvironmentGroupKeys.projectIdentifier)).isEqualTo(PRO_ID);
    assertThat(criteriaObject.get(EnvironmentGroupKeys.deleted)).isEqualTo(false);

    // CASE2: search term as null and deleted is true
    actualCriteria = environmentGroupService.formCriteria(ACC_ID, ORG_ID, PRO_ID, true, null, null, null, false);
    criteriaObject = actualCriteria.getCriteriaObject();
    assertThat(criteriaObject.get(EnvironmentGroupKeys.accountId)).isEqualTo(ACC_ID);
    assertThat(criteriaObject.get(EnvironmentGroupKeys.orgIdentifier)).isEqualTo(ORG_ID);
    assertThat(criteriaObject.get(EnvironmentGroupKeys.projectIdentifier)).isEqualTo(PRO_ID);
    assertThat(criteriaObject.get(EnvironmentGroupKeys.deleted)).isEqualTo(true);

    // CASE3: special character in search term
    assertThatThrownBy(
        () -> environmentGroupService.formCriteria(ACC_ID, ORG_ID, PRO_ID, false, "*", null, null, false))
        .isInstanceOf(InvalidRequestException.class);

    // CASE4: testing the search query
    assertThatCode(
        () -> environmentGroupService.formCriteria(ACC_ID, ORG_ID, PRO_ID, false, "searchTerm", null, null, false))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testFormCriteriaWithFilters() {
    String filterId = "filterId";
    EnvironmentGroupFilterPropertiesDTO filterPropertiesDTO = EnvironmentGroupFilterPropertiesDTO.builder().build();

    // case1: passing both identifier and filter
    assertThatThrownBy(()
                           -> environmentGroupService.formCriteria(
                               ACC_ID, ORG_ID, PRO_ID, false, null, filterId, filterPropertiesDTO, false));

    // case2: passing filter id only
    ArgumentCaptor<String> filterIdCapture = ArgumentCaptor.forClass(String.class);
    environmentGroupService.formCriteria(ACC_ID, ORG_ID, PRO_ID, false, null, filterId, null, false);
    verify(environmentGroupServiceHelper, times(1))
        .populateEnvGroupFilterUsingIdentifier(any(), any(), any(), any(), filterIdCapture.capture());
    assertThat(filterIdCapture.getValue()).isEqualTo(filterId);

    // case2: passing filter properties only
    ArgumentCaptor<EnvironmentGroupFilterPropertiesDTO> envGroupDTOCapture =
        ArgumentCaptor.forClass(EnvironmentGroupFilterPropertiesDTO.class);
    environmentGroupService.formCriteria(ACC_ID, ORG_ID, PRO_ID, false, null, null, filterPropertiesDTO, false);
    verify(environmentGroupServiceHelper, times(1)).populateEnvGroupFilter(any(), envGroupDTOCapture.capture());
    assertThat(envGroupDTOCapture.getValue()).isEqualTo(filterPropertiesDTO);
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testCreateOrgLevelEnvGroup() {
    EnvironmentGroupEntity savedOrgLevelEntity = getEnvironmentGroupEntity(ACC_ID, ORG_ID, null, ENV_GROUP_ID);
    doReturn(savedOrgLevelEntity).when(environmentGroupRepository).create(savedOrgLevelEntity);

    environmentGroupService.create(savedOrgLevelEntity);
    verify(environmentGroupRepository, times(1)).create(savedOrgLevelEntity);
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testGetOrgLevelEnvGroup() {
    environmentGroupService.get(ACC_ID, ORG_ID, PRO_ID, "org.OrgLevelEnvGroup", false);
    // fetch without the project id
    verify(environmentGroupRepository, times(1))
        .findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndDeletedNot(
            ACC_ID, ORG_ID, null, "OrgLevelEnvGroup", true);
  }

  @Test
  @Owner(developers = HINGER)
  @Category(UnitTests.class)
  public void testGetAccountLevelEnvGroup() {
    environmentGroupService.get(ACC_ID, ORG_ID, PRO_ID, "account.AccountLevelEnvGroup", false);
    // fetch without the org, project id
    verify(environmentGroupRepository, times(1))
        .findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndDeletedNot(
            ACC_ID, null, null, "AccountLevelEnvGroup", true);
  }
}
