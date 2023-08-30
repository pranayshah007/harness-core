/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package debezium.embedded;

import static io.harness.rule.OwnerRule.SHALINI;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import io.debezium.embedded.EmbeddedEngineChangeEvent;
import io.debezium.engine.Header;
import java.util.List;
import org.apache.kafka.connect.source.SourceRecord;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(HarnessTeam.PIPELINE)
@RunWith(MockitoJUnitRunner.class)
public class EmbeddedEngineChangeEventTest extends CategoryTest {
  @Mock SourceRecord sourceRecord;
  @InjectMocks EmbeddedEngineChangeEvent<String, String, List<Header>> embeddedEngineChangeEvent;
  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testKey() {
    EmbeddedEngineChangeEvent<String, String, List<Header>> embeddedEngineChangeEvent1 =
        new EmbeddedEngineChangeEvent<>("key", "value", null, sourceRecord);
    assertEquals("key", embeddedEngineChangeEvent1.key());
  }
  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testValue() {
    EmbeddedEngineChangeEvent<String, String, List<Header>> embeddedEngineChangeEvent1 =
        new EmbeddedEngineChangeEvent<>("key", "value", null, sourceRecord);
    assertEquals("value", embeddedEngineChangeEvent1.value());
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testRecord() {
    EmbeddedEngineChangeEvent<String, String, List<Header>> embeddedEngineChangeEvent1 =
        new EmbeddedEngineChangeEvent<>("key", "value", null, sourceRecord);
    assertEquals("value", embeddedEngineChangeEvent1.record());
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testDestination() {
    doReturn("destination").when(sourceRecord).topic();
    assertEquals("destination", embeddedEngineChangeEvent.destination());
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testSourceRecord() {
    assertThat(embeddedEngineChangeEvent.sourceRecord()).isInstanceOf(SourceRecord.class);
  }
}