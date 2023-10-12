/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapoints.parser.kubernetes;

import static io.harness.rule.OwnerRule.VIKYATH_HAREKAL;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.exception.UnexpectedException;
import io.harness.rule.Owner;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

public class KubernetesLastDeployedParserTest extends CategoryTest {
  AutoCloseable openMocks;
  @InjectMocks KubernetesLastDeployedParser parser;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testParseValue() {
    assertNull(parser.parseValue(null));

    List<Map<String, String>> conditions = new ArrayList<>();
    conditions.add(Map.of("type", "Progressing", "status", "True", "reason", "NewReplicaSetAvailable", "lastUpdateTime",
        "2023-10-05T09:46:45Z"));
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX");
    Instant instant = Instant.from(formatter.parse("2023-10-05T09:46:45Z"));
    Instant currentTime = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.DAYS);
    Duration duration = Duration.between(instant, currentTime);
    long expectedDaysSince = duration.toDays();

    assertEquals(expectedDaysSince, parser.parseValue(conditions));
  }

  @Test(expected = UnexpectedException.class)
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testParseValueUnexpectedException() {
    List<Map<String, String>> conditions = new ArrayList<>();
    conditions.add(Map.of("type", "Progressing", "status", "True", "reason", "NewReplicaSetAvailable", "lastUpdateTime",
        "23-10-05T09:46:45Z"));
    parser.parseValue(conditions);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testCompare() {
    assertTrue(parser.compare(2L, 2L));
    assertFalse(parser.compare(1L, 2L));
    assertTrue(parser.compare(2L, null));
  }
}
