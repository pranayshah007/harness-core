/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.validation;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotNull;
import lombok.Data;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class JavaxValidatorTest extends CategoryTest {
  @org.junit.Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testValidObject() {
    MyObject obj = new MyObject();
    obj.setName("John");
    obj.setEmail("john@example.com");
    JavaxValidator.validateOrThrow(obj);
    assert true;
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testInvalidObject() {
    MyObject obj = new MyObject();
    obj.setEmail("a_b");

    Assertions.assertThatExceptionOfType(InvalidRequestException.class)
        .isThrownBy(() -> JavaxValidator.validateOrThrow(obj))
        .withMessageContaining("name: must not be null")
        .withMessageContaining("email: must be a well-formed email address");
  }

  @Data
  static class MyObject {
    @NotNull String name;
    @Email String email;
  }
}
