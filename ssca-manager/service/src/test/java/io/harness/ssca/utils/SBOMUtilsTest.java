/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.utils;

import static io.harness.rule.OwnerRule.ARPITJ;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.SSCAManagerTestBase;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;

public class SBOMUtilsTest extends SSCAManagerTestBase {
  @Before
  public void setup() throws IllegalAccessException {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testParseDateTime_ISOFormat() {
    String input = "2023-10-23T20:10:03+00:00";
    ZonedDateTime zonedDateTime = SBOMUtils.parseDateTime(input);
    assertThat(zonedDateTime.toInstant().toEpochMilli()).isEqualTo(1698091803000l);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testParseDateTime_RFCFormat() {
    String input = "2023-10-23T20:10:03Z";
    ZonedDateTime zonedDateTime = SBOMUtils.parseDateTime(input);
    assertThat(zonedDateTime.toInstant().toEpochMilli()).isEqualTo(1698091803000l);
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGetVersionInfo() {
    Map<String, List<Integer>> versionToListOfIntegersMap = new HashMap<>();
    versionToListOfIntegersMap.put(null, List.of(-1, -1, -1));
    versionToListOfIntegersMap.put("", List.of(-1, -1, -1));
    versionToListOfIntegersMap.put("abc", List.of(-1, -1, -1));
    versionToListOfIntegersMap.put("1", List.of(-1, -1, -1));
    versionToListOfIntegersMap.put("1.2", List.of(-1, -1, -1));
    versionToListOfIntegersMap.put("1.2.3", List.of(1, 2, 3));
    versionToListOfIntegersMap.put("1.2.3-a", List.of(1, 2, 3));
    versionToListOfIntegersMap.put("1.2.3.x", List.of(-1, -1, -1));
    versionToListOfIntegersMap.put("version:1.2", List.of(-1, -1, -1));
    versionToListOfIntegersMap.put("version:1.2.3", List.of(1, 2, 3));

    versionToListOfIntegersMap.forEach((k, v) -> assertThat(SBOMUtils.getVersionInfo(k)).isEqualTo(v));
  }
}
