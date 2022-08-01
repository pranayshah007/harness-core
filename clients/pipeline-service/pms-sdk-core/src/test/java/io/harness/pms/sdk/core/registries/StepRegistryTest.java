/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.registries;

import static io.harness.rule.OwnerRule.PRASHANT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.PmsSdkCoreTestBase;
import io.harness.pms.sdk.core.steps.Step;
import io.harness.pms.sdk.core.steps.io.EmptyStepParameters;
import io.harness.registries.exceptions.DuplicateRegistryException;
import io.harness.registries.exceptions.UnregisteredKeyAccessException;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import lombok.Builder;
import lombok.Value;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class StepRegistryTest extends PmsSdkCoreTestBase {
  @Inject private StepRegistry stepRegistry;

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestRegistry() {
    StepType stepType = StepType.newBuilder().setType("DUMMY_TEST").setStepCategory(StepCategory.STEP).build();
    stepRegistry.register(stepType, new DummyStep());
    Step step = stepRegistry.obtain(stepType);
    assertThat(step).isNotNull();

    assertThatThrownBy(() -> stepRegistry.register(stepType, new DummyStep()))
        .isInstanceOf(DuplicateRegistryException.class);

    assertThatThrownBy(
        () -> stepRegistry.obtain(StepType.newBuilder().setType("RANDOM").setStepCategory(StepCategory.STEP).build()))
        .isInstanceOf(UnregisteredKeyAccessException.class);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestGetType() {
    assertThat(stepRegistry.getType()).isEqualTo(RegistryType.STEP.name());
  }

  @Value
  @Builder
  private static class DummyStep implements Step<EmptyStepParameters> {
    @Override
    public Class<EmptyStepParameters> getStepParametersClass() {
      return EmptyStepParameters.class;
    }
  }
}
