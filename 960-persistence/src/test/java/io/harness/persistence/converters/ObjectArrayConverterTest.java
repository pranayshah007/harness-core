/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.persistence.converters;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.PRASHANT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.PersistenceTestBase;
import io.harness.category.element.UnitTests;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ObjectArrayConverterTest extends PersistenceTestBase {
  @Inject private HPersistence persistence;

  @Test

  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testConverter() {
    ObjectArrayTestEntity entity =
        ObjectArrayTestEntity.builder().uuid(generateUuid()).array(new Object[] {"foo", "bar"}).build();
    String id = persistence.insert(entity);
    assertThat(id).isNotNull();
    ObjectArrayTestEntity savedEntity = persistence.get(ObjectArrayTestEntity.class, id);
    assertThat(savedEntity).isNotNull();
    assertThat(savedEntity.getUuid()).isEqualTo(id);
    assertThat(savedEntity.getArray()).hasSize(2);
    assertThat(savedEntity.getArray()[0]).isEqualTo("foo");
    assertThat(savedEntity.getArray()[1]).isEqualTo("bar");
  }

  @Test

  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testConverterForNull() {
    ObjectArrayTestEntity entity = ObjectArrayTestEntity.builder().uuid(generateUuid()).build();
    String id = persistence.insert(entity);
    assertThat(id).isNotNull();
    ObjectArrayTestEntity savedEntity = persistence.get(ObjectArrayTestEntity.class, id);
    assertThat(savedEntity).isNotNull();
    assertThat(savedEntity.getUuid()).isEqualTo(id);
    assertThat(savedEntity.getArray()).isNull();
  }

  @Test

  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testConverterForEmpty() {
    ObjectArrayTestEntity entity = ObjectArrayTestEntity.builder().uuid(generateUuid()).array(new Object[] {}).build();
    String id = persistence.insert(entity);
    assertThat(id).isNotNull();
    ObjectArrayTestEntity savedEntity = persistence.get(ObjectArrayTestEntity.class, id);
    assertThat(savedEntity).isNotNull();
    assertThat(savedEntity.getUuid()).isEqualTo(id);
    assertThat(savedEntity.getArray()).isEmpty();
  }
}
