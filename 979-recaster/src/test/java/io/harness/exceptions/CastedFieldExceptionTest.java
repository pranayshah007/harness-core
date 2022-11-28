package io.harness.exceptions;

import static io.harness.rule.OwnerRule.VIVEK_DIXIT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class CastedFieldExceptionTest {
  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testCastedFieldExceptionConstructor() {
    String message = "CastedFieldException";
    CastedFieldException castedFieldException = new CastedFieldException(message);
    assertThat(castedFieldException).isNotNull();
  }
}
