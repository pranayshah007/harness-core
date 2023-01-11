/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.persistence;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.GEORGE;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.PersistenceTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.mongo.HObjectFactory;
import io.harness.mongo.MorphiaMove;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import dev.morphia.ObjectFactory;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PL)
public class MorphiaMoveTest extends PersistenceTestBase {
  @Inject private ObjectFactory objectFactory;
  @Inject private HPersistence persistence;

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  @SuppressWarnings("PMD")
  public void shouldCacheMissingClass() {
    TestHolderEntity entity =
        TestHolderEntity.builder()
            .uuid(generateUuid())
            .morphiaObj(
                HackMorphiaClass.builder().test("test").className("io.harness.persistence.MorphiaMissingClass").build())
            .build();
    String id = persistence.save(entity);
    assertThat(id).isNotNull();

    try {
      persistence.get(TestHolderEntity.class, id);
    } catch (Throwable ignore) {
      // do nothing
    }
    try {
      persistence.get(TestHolderEntity.class, id);
    } catch (Throwable ignore) {
      // do nothing
    }
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldReadOldClass() {
    TestHolderEntity entity =
        TestHolderEntity.builder()
            .uuid(generateUuid())
            .morphiaObj(
                HackMorphiaClass.builder().test("test").className("io.harness.persistence.MorphiaOldClass").build())
            .build();
    String id = persistence.save(entity);
    assertThat(id).isNotNull();

    final TestHolderEntity holderEntity = persistence.get(TestHolderEntity.class, id);
    assertThat(((MorphiaClass) holderEntity.getMorphiaObj()).getTest()).isEqualTo("test");
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldReadFutureClass() {
    ((HObjectFactory) objectFactory).setDatastore(persistence.getDatastore(TestHolderEntity.class));

    persistence.save(MorphiaMove.builder()
                         .target("io.harness.persistence.MorphiaFeatureClass")
                         .sources(ImmutableSet.of("io.harness.persistence.MorphiaClass"))
                         .build());

    TestHolderEntity entity =
        TestHolderEntity.builder()
            .uuid(generateUuid())
            .morphiaObj(
                HackMorphiaClass.builder().test("test").className("io.harness.persistence.MorphiaFeatureClass").build())
            .build();
    String id = persistence.save(entity);
    assertThat(id).isNotNull();

    final TestHolderEntity holderEntity = persistence.get(TestHolderEntity.class, id);
    assertThat(((MorphiaClass) holderEntity.getMorphiaObj()).getTest()).isEqualTo("test");
  }
}
