/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.mongo;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.mongo.IndexManager.Mode.AUTO;
import static io.harness.mongo.IndexManagerCollectionSession.createCollectionSession;
import static io.harness.mongo.IndexManagerSession.UNIQUE;
import static io.harness.rule.OwnerRule.GEORGE;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.PersistenceTestBase;
import io.harness.category.element.UnitTests;
import io.harness.mongo.IndexCreator.IndexCreatorBuilder;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import dev.morphia.Morphia;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class IndexManagerCollectionSessionTest extends PersistenceTestBase {
  @Inject HPersistence persistence;
  @Inject Morphia morphia;

  private IndexCreatorBuilder buildIndexCreator(DBCollection collection, String name, int i) {
    return IndexCreator.builder()
        .collection(collection)
        .keys(new BasicDBObject().append("a", i).append("b", 1))
        .options(new BasicDBObject().append("name", name));
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)

  public void testFindIndexByFields() {
    IndexManagerSession session =
        new IndexManagerSession(persistence.getDatastore(TestIndexEntity.class), emptyMap(), AUTO);
    DBCollection collection = persistence.getCollection(TestIndexEntity.class);

    IndexCreator indexCreator = buildIndexCreator(collection, "foo", 1).build();
    assertThat(createCollectionSession(collection).findIndexByFields(indexCreator)).isNull();

    session.create(indexCreator);

    DBObject dbObject = createCollectionSession(collection).findIndexByFields(indexCreator);
    assertThat(dbObject.get("name")).isEqualTo("foo");

    IndexCreator indexCreator2 = buildIndexCreator(collection, "foo", -1).build();
    assertThat(createCollectionSession(collection).findIndexByFields(indexCreator2)).isNotNull();
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)

  public void testFindIndexByFieldsAndDirection() {
    IndexManagerSession session =
        new IndexManagerSession(persistence.getDatastore(TestIndexEntity.class), emptyMap(), AUTO);
    DBCollection collection = persistence.getCollection(TestIndexEntity.class);

    IndexCreator indexCreator = buildIndexCreator(collection, "foo", 1).build();
    assertThat(createCollectionSession(collection).findIndexByFieldsAndDirection(indexCreator)).isNull();

    session.create(indexCreator);

    DBObject dbObject = createCollectionSession(collection).findIndexByFieldsAndDirection(indexCreator);
    assertThat(dbObject.get("name")).isEqualTo("foo");

    IndexCreator indexCreator2 = buildIndexCreator(collection, "foo", -1).build();
    assertThat(createCollectionSession(collection).findIndexByFieldsAndDirection(indexCreator2)).isNull();
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)

  public void testFindIndexByName() {
    IndexManagerSession session =
        new IndexManagerSession(persistence.getDatastore(TestIndexEntity.class), emptyMap(), AUTO);
    DBCollection collection = persistence.getCollection(TestIndexEntity.class);

    session.create(buildIndexCreator(collection, "index", 1).build());

    assertThat(createCollectionSession(collection).findIndexByName("index")).isNotNull();
    assertThat(createCollectionSession(collection).findIndexByName("foo")).isNull();
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)

  public void testIsRebuiltNeeded() {
    IndexManagerSession session =
        new IndexManagerSession(persistence.getDatastore(TestIndexEntity.class), emptyMap(), AUTO);
    DBCollection collection = persistence.getCollection(TestIndexEntity.class);

    IndexCreator indexCreator = buildIndexCreator(collection, "foo", 1).build();
    assertThat(createCollectionSession(collection).isRebuildNeeded(indexCreator)).isFalse();

    session.create(indexCreator);

    assertThat(createCollectionSession(collection).isRebuildNeeded(indexCreator)).isFalse();

    IndexCreator differentCreator = buildIndexCreator(collection, "foo", -1).build();
    assertThat(createCollectionSession(collection).isRebuildNeeded(differentCreator)).isTrue();

    IndexCreator differentNameCreator = buildIndexCreator(collection, "foo2", 1).build();
    assertThat(createCollectionSession(collection).isRebuildNeeded(differentNameCreator)).isTrue();

    IndexCreator differentUniqueFlagCreator = buildIndexCreator(collection, "foo", 1).build();
    differentUniqueFlagCreator.getOptions().put(UNIQUE, Boolean.TRUE);

    assertThat(createCollectionSession(collection).isRebuildNeeded(differentUniqueFlagCreator)).isTrue();
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)

  public void testIsCreateNeeded() {
    IndexManagerSession session =
        new IndexManagerSession(persistence.getDatastore(TestIndexEntity.class), emptyMap(), AUTO);
    DBCollection collection = persistence.getCollection(TestIndexEntity.class);

    IndexCreator indexCreator = buildIndexCreator(collection, "foo", 1).build();
    assertThat(createCollectionSession(collection).isCreateNeeded(indexCreator)).isTrue();

    session.create(indexCreator);

    assertThat(createCollectionSession(collection).isCreateNeeded(indexCreator)).isFalse();
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)

  public void obsoleteIndexes() {
    IndexManagerSession session =
        new IndexManagerSession(persistence.getDatastore(TestIndexEntity.class), emptyMap(), AUTO);
    DBCollection collection = persistence.getCollection(TestIndexEntity.class);

    String index2 = generateUuid();
    Set<String> names = ImmutableSet.<String>builder().add(index2).build();

    String index1 = generateUuid();
    session.create(buildIndexCreator(collection, index1, 1).build());
    session.create(buildIndexCreator(collection, index2, -1).build());

    List<String> obsoleteIndexes = createCollectionSession(collection).obsoleteIndexes(names);

    assertThat(obsoleteIndexes).containsExactly(index1);
  }
  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)

  public void testisUniqueFlag() {
    persistence.ensureIndexForTesting(TestIndexEntity.class);

    IndexManagerSession session =
        new IndexManagerSession(persistence.getDatastore(TestIndexEntity.class), emptyMap(), AUTO);

    DBCollection collection = persistence.getCollection(TestIndexEntity.class);
    DBObject index = createCollectionSession(collection).findIndexByName("index");
    assertThat(index).isNotNull();
    assertThat(IndexCreator.isUniqueIndex(index)).isFalse();

    DBObject uniqueIndex = createCollectionSession(collection).findIndexByName("uniqueTest_1");
    assertThat(uniqueIndex).isNotNull();
    assertThat(IndexCreator.isUniqueIndex(uniqueIndex)).isTrue();
  }
}
