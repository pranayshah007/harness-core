package io.harness.exceptions;

import static io.harness.rule.OwnerRule.VIVEK_DIXIT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class DuplicateAliasExceptionTest {
  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testDuplicateAliasExceptionConstructorWithThrowable() {
    String message = "DuplicateAliasException";
    String internalMessage = "InvalidRequestException";
    DuplicateAliasException duplicateAliasException =
        new DuplicateAliasException(message, new InvalidRequestException(internalMessage));
    assertThat(duplicateAliasException).isNotNull();
  }
}
