/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import static io.harness.rule.OwnerRule.VARSHA_LALWANI;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.category.element.UnitTests;
import io.harness.persistence.PersistentEntity;
import io.harness.persistence.UuidAware;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import com.mongodb.BulkWriteResult;
import dev.morphia.annotations.Id;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class CVNGMongoPeristenceTest extends CvNextGenTestBase {
  private static final String EXCLUDE_FIELD = "excludedField";

  @Inject private SRMPersistence persistence;

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testUpsertBatch() throws IllegalAccessException {
    // create test data
    TestEntity entity1 = new TestEntity();
    entity1.setUuid(UUID.randomUUID().toString());
    entity1.setName("Entity 1");
    entity1.setExcludedField("Excluded");

    TestEntity entity2 = new TestEntity();
    entity2.setName("Entity 2");

    List<TestEntity> entities = new ArrayList<>();
    entities.add(entity1);
    entities.add(entity2);

    List<String> excludeFields = new ArrayList<>();
    excludeFields.add(EXCLUDE_FIELD);

    // call method under test
    BulkWriteResult result = persistence.upsertBatch(TestEntity.class, entities, excludeFields);

    // assert results
    assertNotNull(result);
    assertEquals(2, result.getUpserts().size());

    // assert that UUIDs were generated for entities without UUIDs
    assertNotNull(entity1.getUuid());
    assertNotNull(entity2.getUuid());

    List<TestEntity> testEntities = persistence.createQuery(TestEntity.class).asList();
    entity1 = testEntities.get(0);
    // assert that excluded field was not updated
    assertEquals(null, entity1.getExcludedField());
    assertEquals("Entity 1", entity1.getName());
  }

  @Test
  @Owner(developers = VARSHA_LALWANI)
  @Category(UnitTests.class)
  public void testUpsertBatchWithoutUuid() {
    // create test data
    TestEntityWithoutUUID entity = new TestEntityWithoutUUID();
    entity.setName("Entity without UUID");

    List<TestEntityWithoutUUID> entities = new ArrayList<>();
    entities.add(entity);

    // call method under test and assert that an exception is thrown
    assertThatThrownBy(() -> persistence.upsertBatch(TestEntityWithoutUUID.class, entities, null))
        .isInstanceOf(IllegalAccessException.class)
        .hasMessage("Entity doesn't has uuid");
  }

  private static class TestEntity implements PersistentEntity, UuidAware {
    @Id private String uuid;
    private String name;
    private String excludedField;

    public String getUuid() {
      return uuid;
    }

    public void setUuid(String uuid) {
      this.uuid = uuid;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getExcludedField() {
      return excludedField;
    }

    public void setExcludedField(String excludedField) {
      this.excludedField = excludedField;
    }
  }

  private static class TestEntityWithoutUUID implements PersistentEntity {
    private String name;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }
  }
}
