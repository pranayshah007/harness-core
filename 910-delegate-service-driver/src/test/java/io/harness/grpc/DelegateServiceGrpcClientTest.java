/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.grpc;

import static io.harness.rule.OwnerRule.DEV_MITTAL;
import static io.harness.rule.OwnerRule.VITALIE;

import static junit.framework.TestCase.assertEquals;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(HarnessTeam.CDP)
@RunWith(Parameterized.class)
public class DelegateServiceGrpcClientTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Parameterized.Parameter public Map<String, String> logStreamingAbstractions;

  @Parameterized.Parameters
  public static Collection<Map<String, String>> data() {
    return Arrays.asList(
        Map.of("accountId", "accountIdValue", "orgId", "orgIdValue", "projectId", "projectIdValue"), new HashMap<>() {
          {
            put("accountId", "accountIdValue");
            put("orgId", null);
            put("projectId", null);
          }
        });
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void getAbstractionsMapShouldNotContainEmptyValues() {
    Map<String, String> result = DelegateServiceGrpcClient.getAbstractionsMap(logStreamingAbstractions);
    assertEquals(result.values().contains(null), false);
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void getAbstractionsMapShouldRetainOrder() {
    Map<String, String> map = new LinkedHashMap<>();
    map.put("b", "value1");
    map.put("d", "value4");
    map.put("a", "value3");
    map.put("c", "value2");

    Map<String, String> result = DelegateServiceGrpcClient.getAbstractionsMap(map);
    String order = "";
    for (String k : result.keySet()) {
      order += k;
    }
    assertEquals(order, "bdac");
  }
}