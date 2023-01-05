/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.store;

import static io.harness.rule.OwnerRule.GEORGE;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.PersistenceTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.persistence.HPersistence;
import io.harness.persistence.Store;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import dev.morphia.AdvancedDatastore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PL)
public class EntityStoreTest extends PersistenceTestBase {
  @Inject private HPersistence persistence;

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void testGetDatastore() {
    persistence.register(Store.builder().name("foo").build(), "mongodb://localhost:27017/dummy");
    final AdvancedDatastore datastore = persistence.getDatastore(TestPersistentEntity.class);
    assertThat(datastore.getDB().getName()).isEqualTo("dummy");
  }
}
