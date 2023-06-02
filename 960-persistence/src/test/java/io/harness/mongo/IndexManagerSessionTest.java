/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.mongo;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.mongo.IndexManager.Mode.AUTO;
import static io.harness.mongo.IndexManagerCollectionSession.createCollectionSession;
import static io.harness.rule.OwnerRule.GEORGE;

import static java.time.Duration.ofSeconds;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.PersistenceTestBase;
import io.harness.category.element.UnitTests;
import io.harness.mongo.IndexCreator.IndexCreatorBuilder;
import io.harness.mongo.IndexManagerSession.Accesses;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import dev.morphia.Morphia;
import dev.morphia.mapping.MappedClass;
import java.time.Duration;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class IndexManagerSessionTest extends PersistenceTestBase {
  @Inject HPersistence persistence;

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)

  public void testMongoBehaviorCreateIndexWithExistingMatchingFields() {
    IndexManagerSession session =
        new IndexManagerSession(persistence.getDatastore(TestIndexEntity.class), emptyMap(), AUTO);
    DBCollection collection = persistence.getCollection(TestIndexEntity.class);

    String index1 = generateUuid();
    IndexCreator original = buildIndexCreator(collection, index1, 1).build();
    session.create(original);

    String index2 = generateUuid();
    IndexCreator indexCreator = buildIndexCreator(collection, index2, 1).build();

    DBObject dbObject = createCollectionSession(collection).findIndexByFields(indexCreator);
    assertThat(dbObject.get("name")).isEqualTo(index1);

    assertThat(session.rebuildIndex(createCollectionSession(collection), indexCreator, ofSeconds(0))).isTrue();

    DBObject dbObject2 = createCollectionSession(collection).findIndexByFields(indexCreator);
    assertThat(dbObject2.get("name")).isEqualTo(index2);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)

  public void testMongoBehaviorCreateIndexWithExistingNonMatchingFields() {
    IndexManagerSession session =
        new IndexManagerSession(persistence.getDatastore(TestIndexEntity.class), emptyMap(), AUTO);
    DBCollection collection = persistence.getCollection(TestIndexEntity.class);

    IndexCreator original = buildIndexCreator(collection, "foo", 1).build();
    session.create(original);

    IndexCreator indexCreator = buildIndexCreator(collection, "foo", -1).build();

    DBObject dbObject = createCollectionSession(collection).findIndexByFields(original);
    assertThat(dbObject.get("name")).isEqualTo("foo");

    assertThat(session.rebuildIndex(createCollectionSession(collection), indexCreator, ofSeconds(0))).isTrue();
    session.create(indexCreator);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)

  public void testMongoRebuiltIndexInTime() {
    IndexManagerSession session =
        new IndexManagerSession(persistence.getDatastore(TestIndexEntity.class), emptyMap(), AUTO);
    DBCollection collection = persistence.getCollection(TestIndexEntity.class);

    IndexCreator original = buildIndexCreator(collection, "foo", 1).build();
    session.create(original);

    assertThat(collection.getIndexInfo()).hasSize(2);

    IndexCreator indexCreator = buildIndexCreator(collection, "foo2", 1).build();

    assertThat(session.rebuildIndex(createCollectionSession(collection), indexCreator, ofSeconds(120))).isFalse();

    // The rebuild did not drop the original but created the temporary one
    assertThat(collection.getIndexInfo()).hasSize(3);

    DBObject dbObject2 = createCollectionSession(collection).findIndexByFields(indexCreator);
    assertThat(dbObject2.get("name")).isEqualTo("foo");

    assertThat(session.rebuildIndex(createCollectionSession(collection), indexCreator, ofSeconds(0))).isTrue();

    // The original is dropped and regenerated
    assertThat(collection.getIndexInfo()).hasSize(3);

    DBObject dbObject3 = createCollectionSession(collection).findIndexByFields(indexCreator);
    assertThat(dbObject3.get("name")).isEqualTo("foo2");
  }

  private IndexCreatorBuilder buildIndexCreator(DBCollection collection, String name, int i) {
    return IndexCreator.builder()
        .collection(collection)
        .keys(new BasicDBObject().append("a", i).append("b", 1))
        .options(new BasicDBObject().append("name", name));
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)

  public void testIndexCreators() {
    Morphia morphia = new Morphia();
    morphia.map(TestIndexEntity.class);

    Collection<MappedClass> mappedClasses = morphia.getMapper().getMappedClasses();

    MappedClass mappedClass = mappedClasses.iterator().next();

    DBCollection collection = persistence.getCollection(mappedClass.getClazz());

    Map<String, IndexCreator> creators = IndexManager.indexCreators(mappedClass, collection);

    assertThat(creators).hasSize(6);
    assertThat(creators.get("sparse_index").getOptions().get(IndexManagerSession.SPARSE)).isEqualTo(Boolean.TRUE);

    assertThat(creators.get("uniqueTest_1").getOptions().get(IndexManagerSession.UNIQUE)).isEqualTo(Boolean.TRUE);
    assertThat(creators.get("sparseTest_1").getOptions().get(IndexManagerSession.SPARSE)).isEqualTo(Boolean.TRUE);
    assertThat(creators.get("ttlTest_1").getOptions().get(IndexManagerSession.EXPIRE_AFTER_SECONDS))
        .isEqualTo(Integer.valueOf(11));

    IndexManagerSession session =
        new IndexManagerSession(persistence.getDatastore(TestIndexEntity.class), emptyMap(), AUTO);
    assertThat(session.createNewIndexes(createCollectionSession(collection), creators)).isEqualTo(creators.size());
    Date afterCreatingIndexes = new Date(System.currentTimeMillis() + Duration.ofSeconds(2).toMillis());

    Map<String, Accesses> accesses = IndexManagerSession.fetchIndexAccesses(collection);
    Date tooNew = new Date(System.currentTimeMillis() - Duration.ofDays(1).toMillis());
    assertThat(createCollectionSession(collection).isOkToDropIndexes(IndexManagerSession.tooNew(), accesses)).isFalse();
    assertThat(createCollectionSession(collection).isOkToDropIndexes(afterCreatingIndexes, accesses)).isTrue();
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)

  public void testCreateTwoFieldIndexesEntity() {
    Morphia morphia = new Morphia();
    morphia.map(TestTwoFieldIndexesEntity.class);
    Collection<MappedClass> mappedClasses = morphia.getMapper().getMappedClasses();
    MappedClass mappedClass =
        mappedClasses.stream().filter(mc -> mc.getClazz().equals(TestTwoFieldIndexesEntity.class)).findFirst().get();
    DBCollection collection = persistence.getCollection(mappedClass.getClazz());
    assertThatThrownBy(() -> IndexManager.indexCreators(mappedClass, collection))
        .isInstanceOf(IndexManagerInspectException.class)
        .hasMessageContaining("Only one field index can be used");
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)

  public void testUniqueFlagIndexEntity() {
    Morphia morphia = new Morphia();
    morphia.map(TestUniqueFlagIndexEntity.class);
    Collection<MappedClass> mappedClasses = morphia.getMapper().getMappedClasses();
    MappedClass mappedClass =
        mappedClasses.stream().filter(mc -> mc.getClazz().equals(TestUniqueFlagIndexEntity.class)).findFirst().get();
    DBCollection collection = persistence.getCollection(mappedClass.getClazz());
    assertThatThrownBy(() -> IndexManager.indexCreators(mappedClass, collection))
        .isInstanceOf(Error.class)
        .hasMessageContaining("have the same keys and values");
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)

  public void testTwoSameFieldsEntity() {
    Morphia morphia = new Morphia();
    morphia.map(TestTwoSameFieldsEntity.class);
    Collection<MappedClass> mappedClasses = morphia.getMapper().getMappedClasses();
    MappedClass mappedClass =
        mappedClasses.stream().filter(mc -> mc.getClazz().equals(TestTwoSameFieldsEntity.class)).findFirst().get();
    DBCollection collection = persistence.getCollection(mappedClass.getClazz());
    assertThatThrownBy(() -> IndexManager.indexCreators(mappedClass, collection))
        .isInstanceOf(Error.class)
        .hasMessageContaining("Index uniqueName has field name more than once");
  }
}
