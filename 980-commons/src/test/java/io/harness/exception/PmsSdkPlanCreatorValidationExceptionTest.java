/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.exception;

import static io.harness.rule.OwnerRule.FERNANDOD;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.Collections;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class PmsSdkPlanCreatorValidationExceptionTest {
  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldHasMessage() {
    assertThat(new PmsSdkPlanCreatorValidationException(Collections.emptyMap(), Collections.emptyMap()).getMessage())
        .isEqualTo("Plan creators has unsupported filters or unsupported variables");
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldNotAcceptNullOnFilters() {
    assertThatThrownBy(() -> new PmsSdkPlanCreatorValidationException(null, Collections.emptyMap()))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldNotAcceptNullOnVariables() {
    assertThatThrownBy(() -> new PmsSdkPlanCreatorValidationException(Collections.emptyMap(), null))
        .isInstanceOf(NullPointerException.class);
  }
}
