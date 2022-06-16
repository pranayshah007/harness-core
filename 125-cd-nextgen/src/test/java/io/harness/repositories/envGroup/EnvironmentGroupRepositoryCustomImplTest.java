/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.envGroup;

import static io.harness.rule.OwnerRule.YOGESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGTestBase;
import io.harness.cdng.envGroup.beans.EnvironmentGroupEntity;
import io.harness.cdng.envGroup.beans.EnvironmentGroupEntity.EnvironmentGroupKeys;
import io.harness.data.structure.UUIDGenerator;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.persistance.GitAwarePersistence;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.services.impl.EnvironmentServiceImpl;
import io.harness.outbox.api.OutboxService;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.Optional;
import org.joor.Reflect;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;

public class EnvironmentGroupRepositoryCustomImplTest extends CDNGTestBase {
  private String ACC_ID = "accId";
  private String ORG_ID = "orgId";
  private String PRO_ID = "proId";

  @Mock private GitAwarePersistence gitAwarePersistence;
  @Mock private OutboxService outboxService;

  @Inject private MongoTemplate mongoTemplate;
  @Inject private EnvironmentServiceImpl environmentService;

  @InjectMocks private EnvironmentGroupRepositoryCustomImpl environmentGroupRepositoryCustom;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    Reflect.on(environmentGroupRepositoryCustom).set("mongoTemplate", mongoTemplate);
    Reflect.on(environmentGroupRepositoryCustom).set("environmentService", environmentService);

    environmentService.create(Environment.builder()
                                  .accountId(ACC_ID)
                                  .orgIdentifier(ORG_ID)
                                  .projectIdentifier(PRO_ID)
                                  .identifier("env1")
                                  .build());
    environmentService.create(Environment.builder()
                                  .accountId(ACC_ID)
                                  .orgIdentifier(ORG_ID)
                                  .projectIdentifier(PRO_ID)
                                  .identifier("env2")
                                  .build());
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testEnvNotPresent() {
    EnvironmentGroupEntity environmentGroupEntity = EnvironmentGroupEntity.builder()
                                                        .accountId(ACC_ID)
                                                        .orgIdentifier(ORG_ID)
                                                        .projectIdentifier(PRO_ID)
                                                        .identifier("envGroup")
                                                        .name("envGroup")
                                                        .envIdentifiers(Arrays.asList(UUIDGenerator.generateUuid()))
                                                        .color("col")
                                                        .createdAt(1L)
                                                        .lastModifiedAt(2L)
                                                        .yaml("yaml")
                                                        .build();
    assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> environmentGroupRepositoryCustom.create(environmentGroupEntity))
        .withMessageContaining("not present for this project");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testFindByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndDeletedNot() {
    EnvironmentGroupEntity environmentGroupEntity = EnvironmentGroupEntity.builder()
                                                        .accountId(ACC_ID)
                                                        .orgIdentifier(ORG_ID)
                                                        .projectIdentifier(PRO_ID)
                                                        .identifier("envGroup")
                                                        .name("envGroup")
                                                        .envIdentifiers(Arrays.asList("env1", "env2"))
                                                        .color("col")
                                                        .createdAt(1L)
                                                        .lastModifiedAt(2L)
                                                        .yaml("yaml")
                                                        .build();
    environmentGroupRepositoryCustom.create(environmentGroupEntity);

    Optional<EnvironmentGroupEntity> environmentGroup =
        environmentGroupRepositoryCustom.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndDeletedNot(
            ACC_ID, ORG_ID, PRO_ID, "envGroup", true);

    EnvironmentGroupEntity resultedEntity = environmentGroup.get();
    assertThat(resultedEntity.getEnvIdentifiers().size()).isEqualTo(2);
    assertThat(resultedEntity.getAccountId()).isEqualTo(ACC_ID);
    assertThat(resultedEntity.getOrgIdentifier()).isEqualTo(ORG_ID);
    assertThat(resultedEntity.getProjectIdentifier()).isEqualTo(PRO_ID);
    assertThat(resultedEntity.getIdentifier()).isEqualTo("envGroup");
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testCreate() {
    EnvironmentGroupEntity entity = getDummyEnvironmentEntity();

    EnvironmentGroupEntity resultedEntity = environmentGroupRepositoryCustom.create(entity);

    assertThat(resultedEntity.getEnvIdentifiers().size()).isEqualTo(2);
    assertThat(resultedEntity.getAccountId()).isEqualTo(ACC_ID);
    assertThat(resultedEntity.getOrgIdentifier()).isEqualTo(ORG_ID);
    assertThat(resultedEntity.getProjectIdentifier()).isEqualTo(PRO_ID);
    assertThat(resultedEntity.getIdentifier()).isEqualTo(entity.getIdentifier());
  }

  private EnvironmentGroupEntity getDummyEnvironmentEntity() {
    return EnvironmentGroupEntity.builder()
        .accountId(ACC_ID)
        .orgIdentifier(ORG_ID)
        .projectIdentifier(PRO_ID)
        .identifier(UUIDGenerator.generateUuid())
        .name("envGroup")
        .envIdentifiers(Arrays.asList("env1", "env2"))
        .color("col")
        .createdAt(1L)
        .lastModifiedAt(2L)
        .yaml("yaml")
        .build();
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testList() {
    EnvironmentGroupEntity entity1 = getDummyEnvironmentEntity();
    EnvironmentGroupEntity entity2 = getDummyEnvironmentEntity();
    environmentGroupRepositoryCustom.create(entity1);
    environmentGroupRepositoryCustom.create(entity2);

    Criteria criteria = new Criteria();
    Pageable pageRequest =
        PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, EnvironmentGroupEntity.EnvironmentGroupKeys.createdAt));

    Page<EnvironmentGroupEntity> page =
        environmentGroupRepositoryCustom.list(criteria, pageRequest, PRO_ID, ORG_ID, ACC_ID);
    assertThat(page.get().count()).isEqualTo(2L);
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testDeleteEnvGroup() {
    EnvironmentGroupEntity entity = getDummyEnvironmentEntity();
    environmentGroupRepositoryCustom.create(entity);

    EnvironmentGroupEntity entityWithDeleted = entity.withDeleted(true);

    boolean b = environmentGroupRepositoryCustom.deleteEnvGroup(entityWithDeleted);

    assertThat(b).isTrue();

    Optional<EnvironmentGroupEntity> entityFromDb =
        environmentGroupRepositoryCustom.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndDeletedNot(
            entity.getAccountId(), entity.getOrgIdentifier(), entity.getProjectIdentifier(), entity.getIdentifier(),
            true);
    assertThat(entityFromDb).isNotPresent();
  }

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testUpdate() {
    EnvironmentGroupEntity originalEntity = getDummyEnvironmentEntity();

    EnvironmentGroupEntity updatedEntity = originalEntity.withName("newName");

    environmentGroupRepositoryCustom.create(originalEntity);

    EnvironmentGroupEntity updated = environmentGroupRepositoryCustom.update(updatedEntity, originalEntity,
        Criteria.where(EnvironmentGroupKeys.accountId)
            .is(ACC_ID)
            .and(EnvironmentGroupKeys.orgIdentifier)
            .is(ORG_ID)
            .and(EnvironmentGroupKeys.projectIdentifier)
            .is(PRO_ID)
            .and(EnvironmentGroupKeys.identifier));
    assertThat(updated).isEqualToIgnoringGivenFields(originalEntity, "name", "version");
    assertThat(updated.getName()).isEqualTo("newName");
  }
}
