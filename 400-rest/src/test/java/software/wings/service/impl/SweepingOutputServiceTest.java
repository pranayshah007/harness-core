/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.PRASHANT;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.beans.SweepingOutputInstance;
import io.harness.beans.SweepingOutputInstance.SweepingOutputInstanceBuilder;
import io.harness.category.element.UnitTests;
import io.harness.exception.WingsException;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;

import software.wings.WingsBaseTest;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class SweepingOutputServiceTest extends WingsBaseTest {
  @Inject SweepingOutputService sweepingOutputService;
  @Inject HPersistence persistence;
  @Inject KryoSerializer kryoSerializer;

  @Test
  @Owner(developers = PRASHANT, intermittent = true)
  @Category(UnitTests.class)

  public void shouldGetInstanceId() {
    persistence.ensureIndexForTesting(SweepingOutputInstance.class);

    final SweepingOutputInstanceBuilder builder =
        SweepingOutputInstance.builder()
            .name("jenkins")
            .appId(generateUuid())
            .pipelineExecutionId(generateUuid())
            .workflowExecutionId(generateUuid())
            .output(kryoSerializer.asDeflatedBytes(ImmutableMap.of("foo", "bar")));

    sweepingOutputService.save(builder.uuid(generateUuid()).build());
    assertThatThrownBy(() -> sweepingOutputService.save(builder.uuid(generateUuid()).build()))
        .isInstanceOf(WingsException.class);
  }
}
