/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.persistence;

import static io.harness.rule.OwnerRule.FERNANDOD;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.mongo.MongoConfig;
import io.harness.reflection.ReflectionUtils;
import io.harness.rule.Owner;

import com.mongodb.DBCursor;
import dev.morphia.query.MorphiaIterator;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class HIteratorTest<T> {
  @Mock private MorphiaIterator<T, T> iterator;
  @Mock private DBCursor cursor;

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldPrintLogForTotalCountWhenIteratorNull() {
    assertThat(readPrintLogForTotalCount(new HIterator(null))).isTrue();
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldPrintLogForTotalCountWhenCursorNull() {
    when(iterator.getCursor()).thenReturn(null);
    assertThat(readPrintLogForTotalCount(new HIterator(iterator))).isTrue();
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldPrintLogForTotalCountWhenLimitNotNoLimit() {
    when(cursor.getLimit()).thenReturn(10_000);
    when(iterator.getCursor()).thenReturn(cursor);
    assertThat(readPrintLogForTotalCount(new HIterator(iterator))).isTrue();
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldNotPrintLogForTotalCountWhenLimitIsNoLimit() {
    when(cursor.getLimit()).thenReturn(MongoConfig.NO_LIMIT);
    when(iterator.getCursor()).thenReturn(cursor);
    assertThat(readPrintLogForTotalCount(new HIterator(iterator))).isFalse();
  }

  private boolean readPrintLogForTotalCount(Object obj) {
    return (Boolean) ReflectionUtils.getFieldValue(obj, "shouldPrintLogForTotalCount");
  }
}
