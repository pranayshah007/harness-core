/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.utils;

import static io.harness.rule.OwnerRule.ANSUMAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class PrometheusQueryUtilsTest extends CvNextGenTestBase {
  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void runAgainstMultiplePromQLQueries() throws IOException {
    Map<String, String> map = new HashMap<>();
    String serviceInstanceIdentifier = "node";
    List<String> queries =
        FileUtils.readLines(new File(getResourceFilePath("queries_promql.yml")), StandardCharsets.UTF_8);
    for (int i = 0; i < queries.size(); i++) {
      if (i % 2 != 0) {
        map.put(queries.get(i - 1), queries.get(i));
      }
    }
    for (String testQuery : map.keySet()) {
      assertThat(PrometheusQueryUtils.formGroupByQuery(testQuery, serviceInstanceIdentifier))
          .isEqualTo(map.get(testQuery));
    }
  }
}
