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
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class DatadogQueryUtilsTest extends CvNextGenTestBase {
  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void runAgainstMultipleDatadogMetricsQueries() throws IOException {
    String serviceInstanceIdentifier = "pod_name";
    List<String> queries =
        FileUtils.readLines(new File(getResourceFilePath("queries_datadog_metrics.yml")), StandardCharsets.UTF_8);
    for (int i = 0; i + 2 < queries.size(); i += 3) {
      Pair<String, List<String>> stringListPair =
          DatadogQueryUtils.processCompositeQuery(queries.get(i), serviceInstanceIdentifier, true);
      String formulaStr = queries.get(i + 1);
      String queriesStr = queries.get(i + 2);
      assertThat(formulaStr).isEqualTo(stringListPair.getLeft());
      assertThat(queriesStr).isEqualTo(stringListPair.getRight().toString());
    }
  }
}
