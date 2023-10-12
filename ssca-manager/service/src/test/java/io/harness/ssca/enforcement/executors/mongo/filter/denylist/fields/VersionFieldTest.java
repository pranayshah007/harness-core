/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.enforcement.executors.mongo.filter.denylist.fields;

import static io.harness.rule.OwnerRule.INDER;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.SSCAManagerTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.SSCA)
public class VersionFieldTest extends SSCAManagerTestBase {
  VersionField versionField = new VersionField();

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void TestGetVersion() {
    Map<String, List<Integer>> versionToListOfIntegersMap = new HashMap<>();
    versionToListOfIntegersMap.put("1.2.3", List.of(1, 2, 3));
    versionToListOfIntegersMap.put("1.2", List.of(1, 2, -1));
    versionToListOfIntegersMap.put("1", List.of(1, -1, -1));
    versionToListOfIntegersMap.put("abc", List.of(-1, -1, -1));
    versionToListOfIntegersMap.put("", List.of(-1, -1, -1));
    versionToListOfIntegersMap.put("1.2.3-a", List.of(1, 2, -1));
    versionToListOfIntegersMap.put("1.2.3.x", List.of(-1, -1, -1));

    versionToListOfIntegersMap.forEach((k, v) -> assertThat(versionField.getVersion(k)).isEqualTo(v));
  }
}
