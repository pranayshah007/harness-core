/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.persistence;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.persistence.HPersistence.upsertReturnNewOptions;
import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.rule.OwnerRule.ABHINAV;
import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.rule.TestUserProvider.testUserProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import io.harness.PersistenceTestBase;
import io.harness.beans.EmbeddedUser;
import io.harness.category.element.UnitTests;
import io.harness.persistence.TestEntity.TestEntityKeys;
import io.harness.persistence.TestEntityCreatedAware.TestEntityCreatedAwareKeys;
import io.harness.persistence.TestEntityCreatedLastUpdatedAware.TestEntityCreatedLastUpdatedAwareKeys;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import com.mongodb.DuplicateKeyException;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.util.List;
import java.util.stream.IntStream;
import org.assertj.core.util.Lists;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class HPersistenceTest extends PersistenceTestBase {
  @Inject private HPersistence persistence;

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldSave() {
    TestEntity entity = TestEntity.builder().uuid(generateUuid()).test("foo").build();
    String id = persistence.save(entity);
    assertThat(id).isNotNull();
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldUpsert() {
    String uuid = generateUuid();
    Query<TestEntityCreatedAware> query =
        persistence.createQuery(TestEntityCreatedAware.class).filter(TestEntityCreatedAwareKeys.uuid, uuid);
    UpdateOperations<TestEntityCreatedAware> updateOperations1 =
        persistence.createUpdateOperations(TestEntityCreatedAware.class)
            .set(TestEntityCreatedAwareKeys.uuid, uuid)
            .set(TestEntityCreatedAwareKeys.test, "foo");

    try {
      testUserProvider.setActiveUser(EmbeddedUser.builder().name("user1").build());

      TestEntityCreatedAware entity1 = persistence.upsert(query, updateOperations1, upsertReturnNewOptions);
      assertThat(entity1).isNotNull();

      UpdateOperations<TestEntityCreatedAware> updateOperations2 =
          persistence.createUpdateOperations(TestEntityCreatedAware.class).set(TestEntityCreatedAwareKeys.test, "bar");

      testUserProvider.setActiveUser(EmbeddedUser.builder().name("user2").build());
      TestEntityCreatedAware entity2 = persistence.upsert(query, updateOperations2, upsertReturnNewOptions);
      assertThat(entity2).isNotNull();

      assertThat(entity1.getCreatedAt()).isEqualTo(entity2.getCreatedAt());
      assertThat(entity1.getCreatedBy().getName()).isEqualTo(entity2.getCreatedBy().getName());
    } finally {
      testUserProvider.setActiveUser(null);
    }
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldSaveList() {
    List<TestEntity> list = Lists.newArrayList();
    IntStream.range(0, 5).forEach(i -> {
      TestEntity entity = TestEntity.builder().uuid(generateUuid()).test("foo" + i).build();
      list.add(entity);
    });
    List<String> ids = persistence.save(list);
    assertThat(ids).isNotNull().hasSize(list.size()).doesNotContainNull();
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldSaveIgnoringDuplicateKeysList() {
    List<TestEntity> list = Lists.newArrayList();
    IntStream.range(0, 5).forEach(i -> {
      TestEntity entity = TestEntity.builder().uuid(generateUuid()).test("shouldSaveIgnoringDuplicateKeysList").build();
      list.add(entity);
    });

    persistence.save(list.get(0));
    persistence.saveIgnoringDuplicateKeys(list);

    final List<TestEntity> testEntities = persistence.createQuery(TestEntity.class, excludeAuthority)
                                              .filter(TestEntityKeys.test, "shouldSaveIgnoringDuplicateKeysList")
                                              .asList();

    assertThat(testEntities).hasSize(5);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldInsert() {
    TestEntity entity = TestEntity.builder().uuid(generateUuid()).test("foo").build();
    String id = persistence.insert(entity);
    assertThat(id).isNotNull();
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldNotInsertTwice() {
    TestEntity entity = TestEntity.builder().uuid(generateUuid()).test("foo").build();
    persistence.insert(entity);

    assertThatThrownBy(() -> persistence.insert(entity)).isInstanceOf(DuplicateKeyException.class);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldInsertIgnoringDuplicateKeys() {
    TestEntity entity = TestEntity.builder().uuid(generateUuid()).test("foo").build();
    persistence.insert(entity);

    String id = persistence.insertIgnoringDuplicateKeys(entity);
    assertThat(id).isNotNull();
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldGet() {
    TestEntity entity = TestEntity.builder().uuid(generateUuid()).test("shouldGet").build();
    persistence.save(entity);

    final TestEntity obtainedEntity = persistence.get(TestEntity.class, entity.getUuid());
    assertThat(obtainedEntity.getTest()).isEqualTo("shouldGet");
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldDeleteUuid() {
    TestEntity entity = TestEntity.builder().uuid(generateUuid()).test("shouldDeleteUuid").build();

    persistence.save(entity);

    final Query<TestEntity> query =
        persistence.createQuery(TestEntity.class, excludeAuthority).filter(TestEntityKeys.test, "shouldDeleteUuid");

    List<TestEntity> testEntities = query.asList();
    assertThat(testEntities).hasSize(1);

    persistence.delete(TestEntity.class, entity.getUuid());

    testEntities = query.asList();
    assertThat(testEntities).hasSize(0);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldDeleteQuery() {
    TestEntity entity = TestEntity.builder().uuid(generateUuid()).test("shouldDeleteQuery").build();

    persistence.save(entity);

    final Query<TestEntity> query =
        persistence.createQuery(TestEntity.class, excludeAuthority).filter(TestEntityKeys.test, "shouldDeleteQuery");

    List<TestEntity> testEntities = query.asList();
    assertThat(testEntities).hasSize(1);

    persistence.delete(query);

    testEntities = query.asList();
    assertThat(testEntities).hasSize(0);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldDeleteEntity() {
    TestEntity entity = TestEntity.builder().uuid(generateUuid()).test("shouldDeleteEntity").build();

    persistence.save(entity);

    final Query<TestEntity> query =
        persistence.createQuery(TestEntity.class, excludeAuthority).filter(TestEntityKeys.test, "shouldDeleteEntity");

    List<TestEntity> testEntities = query.asList();
    assertThat(testEntities).hasSize(1);

    persistence.delete(entity);

    testEntities = query.asList();
    assertThat(testEntities).hasSize(0);
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void shouldUpdate() {
    TestEntityCreatedLastUpdatedAware entity =
        TestEntityCreatedLastUpdatedAware.builder().uuid(generateUuid()).test("foo").build();

    try {
      testUserProvider.setActiveUser(EmbeddedUser.builder().name("user1").build());
      String id = persistence.save(entity);
      final TestEntityCreatedLastUpdatedAware testEntity = persistence.get(TestEntityCreatedLastUpdatedAware.class, id);
      assertThat(testEntity.getCreatedAt()).isNotZero();
      assertThat(testEntity.getLastUpdatedAt()).isNotZero();
      assertThat(testEntity.getLastUpdatedBy()).isNotNull();
      assertThat(testEntity.getCreatedBy()).isNotNull();

      final UpdateOperations<TestEntityCreatedLastUpdatedAware> entityUpdateOperations =
          persistence.createUpdateOperations(TestEntityCreatedLastUpdatedAware.class)
              .set(TestEntityCreatedLastUpdatedAwareKeys.test, "bar");
      testUserProvider.setActiveUser(EmbeddedUser.builder().name("user2").build());
      persistence.update(testEntity, entityUpdateOperations);

      final TestEntityCreatedLastUpdatedAware testEntityAfterUpdate =
          persistence.get(TestEntityCreatedLastUpdatedAware.class, id);
      assertThat(testEntity.getCreatedAt()).isEqualTo(testEntityAfterUpdate.getCreatedAt());
      assertThat(testEntityAfterUpdate.getTest()).isEqualTo("bar");
      assertThat(testEntity.getLastUpdatedAt()).isLessThan(testEntityAfterUpdate.getLastUpdatedAt());
      assertThat(testEntity.getCreatedBy()).isEqualTo(testEntityAfterUpdate.getCreatedBy());
      assertThat(testEntityAfterUpdate.getLastUpdatedBy().getName()).isEqualTo("user2");
    } finally {
      testUserProvider.setActiveUser(null);
    }
  }
}
