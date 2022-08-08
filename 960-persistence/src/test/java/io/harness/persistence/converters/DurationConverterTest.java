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
import java.time.Duration;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class DurationConverterTest extends PersistenceTestBase {
  @Inject private HPersistence persistence;

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testConverter() {
    DurationTestEntity entity =
        DurationTestEntity.builder().uuid(generateUuid()).testDuration(Duration.ofSeconds(10)).build();
    String id = persistence.insert(entity);
    assertThat(id).isNotNull();
    DurationTestEntity savedEntity = persistence.get(DurationTestEntity.class, id);
    assertThat(savedEntity).isNotNull();
    assertThat(savedEntity.getUuid()).isEqualTo(id);
    assertThat(savedEntity.getTestDuration()).isEqualTo(Duration.ofSeconds(10));
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testConverterForNull() {
    DurationTestEntity entity = DurationTestEntity.builder().uuid(generateUuid()).build();
    String id = persistence.insert(entity);
    assertThat(id).isNotNull();
    DurationTestEntity savedEntity = persistence.get(DurationTestEntity.class, id);
    assertThat(savedEntity).isNotNull();
    assertThat(savedEntity.getUuid()).isEqualTo(id);
    assertThat(savedEntity.getTestDuration()).isNull();
  }
}
