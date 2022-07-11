/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer.kryo.serializers;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.PRASHANT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.OrchestrationTestBase;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.steps.StepType;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class LevelKryoSerializerTest extends OrchestrationTestBase {
  @Inject KryoSerializer kryoSerializer;

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testSerializer() {
    String runtimeId = generateUuid();
    String setupId = generateUuid();
    Level level = Level.newBuilder()
                      .setRuntimeId(runtimeId)
                      .setSetupId(setupId)
                      .setStepType(StepType.newBuilder().setType("DUMMY").build())
                      .setIdentifier("identifier")
                      .build();

    byte[] levelBytes = kryoSerializer.asBytes(level);
    assertThat(levelBytes).isNotEmpty();

    Level deserializedLevel = (Level) kryoSerializer.asObject(levelBytes);
    assertThat(deserializedLevel.getRuntimeId()).isEqualTo(runtimeId);
    assertThat(deserializedLevel.getSetupId()).isEqualTo(setupId);
    assertThat(deserializedLevel.getStepType()).isNotNull();
    assertThat(deserializedLevel.getStepType().getType()).isEqualTo("DUMMY");
  }
}
