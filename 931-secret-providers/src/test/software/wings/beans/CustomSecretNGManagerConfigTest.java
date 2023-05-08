/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.MEENAKSHI;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.SelectorCapability;
import io.harness.rule.Owner;

import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PL)
public class CustomSecretNGManagerConfigTest extends CategoryTest {
  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testFetchRequiredExecutionCapabilities_onDelegateTrue() {
    CustomSecretNGManagerConfig config =
        CustomSecretNGManagerConfig.builder().delegateSelectors(Set.of("tag1")).onDelegate(true).build();
    List<ExecutionCapability> requiredExecutionCapabilities = config.fetchRequiredExecutionCapabilities(null);
    assertThat(requiredExecutionCapabilities.size()).isEqualTo(1);
    assertThat(requiredExecutionCapabilities.get(0) instanceof SelectorCapability).isTrue();
  }
  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testFetchRequiredExecutionCapabilities_onDelegateFalse() {
    CustomSecretNGManagerConfig config =
        CustomSecretNGManagerConfig.builder().delegateSelectors(Set.of("tag1")).onDelegate(false).build();
    List<ExecutionCapability> requiredExecutionCapabilities = config.fetchRequiredExecutionCapabilities(null);
    assertThat(requiredExecutionCapabilities.size()).isEqualTo(1);
    assertThat(((SelectorCapability) requiredExecutionCapabilities.get(0)).getSelectors()).isEqualTo(Set.of("tag1"));
  }

  @Test
  @Owner(developers = MEENAKSHI)
  @Category(UnitTests.class)
  public void testFetchRequiredExecutionCapabilities_noDelegateSelector() {
    CustomSecretNGManagerConfig config = CustomSecretNGManagerConfig.builder().onDelegate(false).build();
    List<ExecutionCapability> requiredExecutionCapabilities = config.fetchRequiredExecutionCapabilities(null);
    assertThat(requiredExecutionCapabilities.size()).isEqualTo(0);
  }
}
