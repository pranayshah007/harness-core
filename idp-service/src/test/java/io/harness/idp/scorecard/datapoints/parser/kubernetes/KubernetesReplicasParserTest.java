/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapoints.parser.kubernetes;

import static io.harness.idp.common.Constants.DATA_POINT_VALUE_KEY;
import static io.harness.idp.common.Constants.DSL_RESPONSE;
import static io.harness.idp.common.Constants.ERROR_MESSAGE_KEY;
import static io.harness.idp.common.Constants.KUBERNETES_IDENTIFIER;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.REPLICAS;
import static io.harness.rule.OwnerRule.VIKYATH_HAREKAL;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.rule.Owner;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class KubernetesReplicasParserTest extends CategoryTest {
  AutoCloseable openMocks;
  @InjectMocks KubernetesReplicasParser parser;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testParseValue() {
    assertEquals(2, parser.parseValue(2.0));
    assertNull(parser.parseValue(null));
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testCompare() {
    assertTrue(parser.compare(2, 2));
    assertFalse(parser.compare(2, 1));
    assertTrue(parser.compare(2, null));
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testParseDataPoint() {
    DataPointEntity dp = DataPointEntity.builder()
                             .dataSourceIdentifier(KUBERNETES_IDENTIFIER)
                             .identifier(REPLICAS)
                             .outcomeExpression("kubernetes.workload.spec.replicas")
                             .build();
    Map<String, Object> data =
        Map.of(DSL_RESPONSE, Map.of("cluster", List.of("workload", Map.of("spec", Map.of("replicas", 2.0)))));
    Map<String, Object> response = (Map<String, Object>) parser.parseDataPoint(data, dp, Collections.emptyList());
    assertEquals(2, response.get(DATA_POINT_VALUE_KEY));
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testParseDataPointUnAuthorized() {
    String errorMessage = "401 Unauthorized";
    DataPointEntity dp = DataPointEntity.builder().build();
    Map<String, Object> data = Map.of(DSL_RESPONSE, Map.of(ERROR_MESSAGE_KEY, errorMessage));
    Map<String, Object> response = (Map<String, Object>) parser.parseDataPoint(data, dp, Collections.emptyList());
    assertEquals(errorMessage, response.get(ERROR_MESSAGE_KEY));
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testParseDataPointMissingData() {
    String errorMessage = "Missing Data";
    DataPointEntity dp = DataPointEntity.builder().build();
    Map<String, Object> data = Map.of(DSL_RESPONSE, Map.of(ERROR_MESSAGE_KEY, errorMessage));
    Map<String, Object> response = (Map<String, Object>) parser.parseDataPoint(data, dp, Collections.emptyList());
    assertEquals(errorMessage, response.get(ERROR_MESSAGE_KEY));
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testParseDataPointMissingResponseData() {
    String errorMessage = "Missing Data";
    DataPointEntity dp = DataPointEntity.builder().build();
    Map<String, Object> data = Map.of(ERROR_MESSAGE_KEY, errorMessage);
    Map<String, Object> response = (Map<String, Object>) parser.parseDataPoint(data, dp, Collections.emptyList());
    assertEquals(errorMessage, response.get(ERROR_MESSAGE_KEY));
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testParseDataPointExpressionNotMatchingData() {
    DataPointEntity dp = DataPointEntity.builder()
                             .dataSourceIdentifier(KUBERNETES_IDENTIFIER)
                             .identifier(REPLICAS)
                             .outcomeExpression("kubernetes.workload.spec.replicas")
                             .build();
    Map<String, Object> data = Map.of(DSL_RESPONSE, Map.of("abc", List.of("workload", Map.of("replicas", 2.0))));
    Map<String, Object> response = (Map<String, Object>) parser.parseDataPoint(data, dp, Collections.emptyList());
    assertEquals("Missing Data for cluster: abc", response.get(ERROR_MESSAGE_KEY));
  }
}
