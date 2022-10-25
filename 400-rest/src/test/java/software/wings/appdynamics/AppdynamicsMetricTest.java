/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.appdynamics;

import static io.harness.rule.OwnerRule.RAGHU;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;

import software.wings.service.impl.appdynamics.AppdynamicsMetric;
import software.wings.service.impl.appdynamics.AppdynamicsMetric.AppdynamicsMetricType;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * Created by rsingh on 4/19/17.
 */
public class AppdynamicsMetricTest extends CategoryTest {
  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testAppdynamicsMetricParse() throws Exception {
    final String metricResponse = "[{\"name\": \"Average Response Time (ms)\", \"type\": \"leaf\"},"
        + "{\"name\": \"Calls per Minute\",\"type\": \"folder\"},"
        + "{\"name\": \"Errors per Minute\",\"type\": \"leaf\"},"
        + "{\"name\": \"Number of Slow Calls\",\"type\": \"folder\"}]";

    final List<AppdynamicsMetric> metrics =
        JsonUtils.asObject(metricResponse, new TypeReference<List<AppdynamicsMetric>>() {});
    assertThat(metrics).hasSize(4);

    assertThat(metrics.get(0).getType()).isEqualTo(AppdynamicsMetricType.leaf);
    assertThat(metrics.get(0).getName()).isEqualTo("Average Response Time (ms)");

    assertThat(metrics.get(1).getType()).isEqualTo(AppdynamicsMetricType.folder);
    assertThat(metrics.get(1).getName()).isEqualTo("Calls per Minute");

    assertThat(metrics.get(2).getType()).isEqualTo(AppdynamicsMetricType.leaf);
    assertThat(metrics.get(2).getName()).isEqualTo("Errors per Minute");

    assertThat(metrics.get(3).getType()).isEqualTo(AppdynamicsMetricType.folder);
    assertThat(metrics.get(3).getName()).isEqualTo("Number of Slow Calls");
  }
}
