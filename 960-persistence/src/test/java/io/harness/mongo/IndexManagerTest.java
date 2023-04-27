/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.mongo;

import static io.harness.mongo.IndexManager.Mode.AUTO;
import static io.harness.mongo.IndexManager.Mode.INSPECT;
import static io.harness.rule.OwnerRule.GEORGE;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.PersistenceTestBase;
import io.harness.category.element.UnitTests;
import io.harness.mongo.index.IndexType;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import com.mongodb.BasicDBObject;
import dev.morphia.Morphia;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class IndexManagerTest extends PersistenceTestBase {
  @Inject HPersistence persistence;
  @Inject IndexManager indexManager;

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)

  public void testMongoBehaviorCreateIndexWithExistingMatchingFields() {
    Morphia morphia = new Morphia();
    morphia.getMapper().getOptions().setMapSubPackages(true);
    morphia.mapPackage("io.harness");

    assertThatThrownBy(
        () -> indexManager.ensureIndexes(INSPECT, persistence.getDatastore(TestIndexEntity.class), morphia, null))
        .isInstanceOf(IndexManagerInspectException.class);

    indexManager.ensureIndexes(AUTO, persistence.getDatastore(TestIndexEntity.class), morphia, null);

    assertThatCode(
        () -> indexManager.ensureIndexes(INSPECT, persistence.getDatastore(TestIndexEntity.class), morphia, null))
        .doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testSubsequenceKeys() {
    BasicDBObject a1 = new BasicDBObject();
    a1.put("a", 1);

    BasicDBObject a_1 = new BasicDBObject();
    a_1.put("a", -1);

    BasicDBObject a1b1 = new BasicDBObject();
    a1b1.put("a", 1);
    a1b1.put("b", 1);

    assertThat(IndexCreator.subsequenceKeys(a1, a_1)).isFalse();
    assertThat(IndexCreator.subsequenceKeys(a_1, a1)).isFalse();

    assertThat(IndexCreator.subsequenceKeys(a1b1, a1)).isTrue();
    assertThat(IndexCreator.subsequenceKeys(a1b1, a_1)).isFalse();

    assertThat(IndexCreator.subsequenceKeys(a1, a1b1)).isFalse();
    assertThat(IndexCreator.subsequenceKeys(a_1, a1b1)).isFalse();
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testIndexTypeFromValue() {
    assertThat(IndexType.fromValue(Integer.valueOf(1))).isEqualTo(IndexType.ASC);
    assertThat(IndexType.fromValue(Float.valueOf(1))).isEqualTo(IndexType.ASC);
    assertThat(IndexType.fromValue(Double.valueOf(1.0))).isEqualTo(IndexType.ASC);
  }
}
