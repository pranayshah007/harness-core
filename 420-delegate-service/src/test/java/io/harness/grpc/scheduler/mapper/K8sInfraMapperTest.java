/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.grpc.scheduler.mapper;

import static io.harness.annotations.dev.HarnessTeam.DEL;
import static io.harness.grpc.scheduler.datagen.InfraRequestTestFactory.createK8sInfraSpec;
import static io.harness.grpc.scheduler.datagen.InfraRequestTestFactory.createStep;
import static io.harness.grpc.scheduler.datagen.InfraRequestTestFactory.expectedInfra;
import static io.harness.grpc.scheduler.datagen.InfraRequestTestFactory.expectedStep;
import static io.harness.rule.OwnerRule.MARKO;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.LogConfig;
import io.harness.delegate.core.beans.K8SInfra;
import io.harness.rule.Owner;

import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(DEL)
@RunWith(MockitoJUnitRunner.class)
public class K8sInfraMapperTest {
  @Before
  public void setUp() throws Exception {}

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testK8SMapper() {
    final var logKey = "logKey";
    final var loggingToken = "loggingToken";
    final var taskIds = Map.of("step1", "taskId1", "step2", "taskId2");

    final var step1 = createStep("step1", "image1");
    final var step2 = createStep("step2", "image2");
    final var logConfig = LogConfig.newBuilder().setLogKey(logKey).build();
    final var infra = createK8sInfraSpec(step1, step2);

    final var actual = K8sInfraMapper.map(infra, taskIds, logConfig, loggingToken);

    // Expected
    final var task1 = expectedStep("taskId1", "image1");
    final var task2 = expectedStep("taskId2", "image2");
    final K8SInfra expected = expectedInfra(logKey, loggingToken, task1, task2);

    assertThat(actual).isEqualTo(expected);
  }
}
