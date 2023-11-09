/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.changehandlers;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.changestreamsframework.ChangeEvent;
import io.harness.changestreamsframework.ChangeEvent.ChangeEventBuilder;
import io.harness.changestreamsframework.ChangeType;
import io.harness.entities.subscriptions.ChaosExperiments;

import com.mongodb.BasicDBObject;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ChaosExperimentsChangeDataHandlerTest {
  private ChaosExperimentsChangeDataHandler handler;
  private ChangeEventBuilder<ChaosExperiments> builder;

  @Before
  public void setUp() {
    handler = new ChaosExperimentsChangeDataHandler();
    builder = ChangeEvent.builder();
    builder.changeType(ChangeType.INSERT).uuid("very_unique_id");
  }

  @Test
  @Category(UnitTests.class)
  public void givenValidEntity_whenGetMapping_thenReturnFieldsInMap() {
    ChangeEvent<ChaosExperiments> changeEvent = builder.fullDocument(new BasicDBObject(Map.of())).build();

    Map<String, String> actual = handler.getColumnValueMapping(changeEvent, null);

    assertThat(actual).containsEntry("id", "very_unique_id");
  }

  @Test
  @Category(UnitTests.class)
  public void givenEntityWithManyFields_whenGetMapping_thenDoNotReturnTags() {
    Map<String, String> entityValues = Map.of("account_id", "acc_id", "created_by", "me", "infra_type", "Class C");
    ChangeEvent<ChaosExperiments> changeEvent = builder.fullDocument(new BasicDBObject(entityValues)).build();

    Map<String, String> actual = handler.getColumnValueMapping(changeEvent, null);

    assertThat(actual).containsEntry("id", "very_unique_id");
    assertThat(actual).containsEntry("account_id", "acc_id");
    assertThat(actual).containsEntry("created_by", "me");
    assertThat(actual).containsEntry("infra_type", "Class C");
  }

  @Test
  @Category(UnitTests.class)
  public void givenEntityWithTags_whenGetMapping_thenDoNotReturnTags() {
    Map<String, Object> entityValues = Map.of("tags", List.of("tags", "go", "here"));
    ChangeEvent<ChaosExperiments> changeEvent = builder.fullDocument(new BasicDBObject(entityValues)).build();

    Map<String, String> actual = handler.getColumnValueMapping(changeEvent, null);

    assertThat(actual).containsEntry("id", "very_unique_id");
    assertThat(actual).doesNotContainKey("tags");
  }

  @Test
  @Category(UnitTests.class)
  public void givenEntityWithNonStringValues_whenGetMapping_thenConvertValuesToString() {
    Map<String, Object> entityValues = Map.of("is_custom_experiment", false, "updated_at", 100L);
    ChangeEvent<ChaosExperiments> changeEvent = builder.fullDocument(new BasicDBObject(entityValues)).build();

    Map<String, String> actual = handler.getColumnValueMapping(changeEvent, null);

    assertThat(actual).containsEntry("id", "very_unique_id");
    assertThat(actual).containsEntry("is_custom_experiment", "false");
    assertThat(actual).containsEntry("updated_at", "100");
  }
}
