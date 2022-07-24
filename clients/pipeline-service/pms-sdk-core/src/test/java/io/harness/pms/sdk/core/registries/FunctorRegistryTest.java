/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.registries;

import static io.harness.rule.OwnerRule.BRIJESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.sdk.core.PmsSdkCoreTestBase;
import io.harness.pms.sdk.core.execution.expression.ExpressionResult;
import io.harness.pms.sdk.core.execution.expression.SdkFunctor;
import io.harness.registries.exceptions.DuplicateRegistryException;
import io.harness.registries.exceptions.UnregisteredKeyAccessException;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import lombok.Builder;
import lombok.Value;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class FunctorRegistryTest extends PmsSdkCoreTestBase {
  @Inject private FunctorRegistry functorRegistry;

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void shouldTestRegistry() {
    String functorKey = "dummy";
    functorRegistry.register(functorKey, new DummySdkFunctor());
    SdkFunctor functor = functorRegistry.obtain(functorKey);
    assertThat(functor).isNotNull();

    assertThatThrownBy(() -> functorRegistry.register(functorKey, new DummySdkFunctor()))
        .isInstanceOf(DuplicateRegistryException.class);

    assertThatThrownBy(() -> functorRegistry.obtain("RANDOM")).isInstanceOf(UnregisteredKeyAccessException.class);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void shouldTestGetType() {
    assertThat(functorRegistry.getType()).isEqualTo(RegistryType.SDK_FUNCTOR.name());
  }

  @Value
  @Builder
  private static class DummySdkFunctor implements SdkFunctor {
    @Override
    public ExpressionResult get(Ambiance ambiance, String... args) {
      return null;
    }
  }
}
