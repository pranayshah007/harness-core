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

public class ChaosExperimentsTagsChangeDataHandlerTest {
  private ChaosExperimentsTagsChangeDataHandler handler;
  private ChangeEventBuilder<ChaosExperiments> builder;

  @Before
  public void setUp() {
    handler = new ChaosExperimentsTagsChangeDataHandler();
    builder = ChangeEvent.builder();
    builder.changeType(ChangeType.INSERT).uuid("very_unique_id");
  }

  @Test
  @Category(UnitTests.class)
  public void givenNothing_whenGetMapping_thenError() {
    Map<String, Object> entityValues = Map.of("tags", List.of("tags", "go", "here"));
    ChangeEvent<ChaosExperiments> changeEvent = builder.fullDocument(new BasicDBObject(entityValues)).build();

    List<Map<String, String>> actual = handler.getColumnValueMappings(changeEvent, null);

    assertThat(actual).hasSize(3);
    assertThat(actual.get(0)).containsEntry("parent_id", "very_unique_id");
    assertThat(actual.get(0)).containsEntry("tag", "tags");
    assertThat(actual.get(1)).containsEntry("parent_id", "very_unique_id");
    assertThat(actual.get(1)).containsEntry("tag", "go");
    assertThat(actual.get(2)).containsEntry("parent_id", "very_unique_id");
    assertThat(actual.get(2)).containsEntry("tag", "here");
  }

  @Test
  @Category(UnitTests.class)
  public void givenDuplicateTags_whenGetMapping_thenRemoveDuplicates() {
    Map<String, Object> entityValues = Map.of("tags", List.of("tags", "go", "here", "and", "here"));
    ChangeEvent<ChaosExperiments> changeEvent = builder.fullDocument(new BasicDBObject(entityValues)).build();

    List<Map<String, String>> actual = handler.getColumnValueMappings(changeEvent, null);

    assertThat(actual).containsOnlyOnce(Map.of("parent_id", "very_unique_id", "tag", "here"));
  }
}
