/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.grpc;

import static io.harness.rule.OwnerRule.VITALIE;

import static junit.framework.TestCase.assertEquals;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.Collection;
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

  @Parameterized.Parameter public LinkedHashMap<String, String> logStreamingAbstractions;

  @Parameterized.Parameters
  public static Collection<LinkedHashMap<String, String>> data() {
    return Arrays.asList(
        new LinkedHashMap(Map.of("accountId", "accountIdValue", "orgId", "orgIdValue", "projectId", "projectIdValue")),
        new LinkedHashMap(Map.of("accountId", "accountIdValue", "orgId", "orgIdValue", "projectId", "")),
        new LinkedHashMap(Map.of("accountId", "accountIdValue", "orgId", "", "projectId", "")));
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void getAbstractionsMapShouldNotContainEmptyValues() {
    Map<String, String> result = DelegateServiceGrpcClient.getAbstractionsMap(logStreamingAbstractions);
    assertEquals(result.values().contains(""), false);
  }
}
