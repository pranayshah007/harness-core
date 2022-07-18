/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.registries.facilitator;

import static io.harness.rule.OwnerRule.PRASHANT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.sdk.core.PmsSdkCoreTestBase;
import io.harness.pms.sdk.core.execution.events.node.facilitate.Facilitator;
import io.harness.pms.sdk.core.execution.events.node.facilitate.FacilitatorResponse;
import io.harness.pms.sdk.core.registries.FacilitatorRegistry;
import io.harness.pms.sdk.core.registries.RegistryType;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.registries.exceptions.DuplicateRegistryException;
import io.harness.registries.exceptions.UnregisteredKeyAccessException;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import lombok.Builder;
import lombok.Value;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class FacilitatorRegistryTest extends PmsSdkCoreTestBase {
  @Inject private FacilitatorRegistry facilitatorRegistry;

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestRegistry() {
    FacilitatorType facilitatorType = FacilitatorType.newBuilder().setType("Type1").build();
    facilitatorRegistry.register(facilitatorType, new Type1Facilitator());
    Facilitator facilitator = facilitatorRegistry.obtain(facilitatorType);
    assertThat(facilitator).isNotNull();

    assertThatThrownBy(() -> facilitatorRegistry.register(facilitatorType, new Type1Facilitator()))
        .isInstanceOf(DuplicateRegistryException.class);

    assertThatThrownBy(() -> facilitatorRegistry.obtain(FacilitatorType.newBuilder().setType("SKIP").build()))
        .isInstanceOf(UnregisteredKeyAccessException.class);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestGetType() {
    assertThat(facilitatorRegistry.getType()).isEqualTo(RegistryType.FACILITATOR.name());
  }

  @Value
  @Builder
  private static class Type1Facilitator implements Facilitator {
    @Override
    public FacilitatorResponse facilitate(
        Ambiance ambiance, StepParameters stepParameters, byte[] parameters, StepInputPackage inputPackage) {
      return null;
    }
  }
}
