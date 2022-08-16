/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.beans;

import static io.harness.rule.OwnerRule.KAMAL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.splunkconnector.SplunkConnectorDTO;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class SplunkDataCollectionInfoTest extends CategoryTest {
  @Before
  public void setup() throws IOException {}

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testGetDslEnvVariables() {
    SplunkDataCollectionInfo splunkDataCollectionInfo =
        SplunkDataCollectionInfo.builder().query("exception").serviceInstanceIdentifier("host").build();
    Map<String, Object> expected = new HashMap<>();
    expected.put("query", "exception");
    expected.put("serviceInstanceIdentifier", "$.host");
    expected.put("maxCount", 10000);
    assertThat(splunkDataCollectionInfo.getDslEnvVariables(SplunkConnectorDTO.builder().build())).isEqualTo(expected);
  }
}
