/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.validator;

import static io.harness.rule.OwnerRule.FERNANDOD;
import static io.harness.rule.OwnerRule.SAMARTH;
import static io.harness.rule.OwnerRule.TATHAGAT;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.regex.Pattern;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class NGRegexValidatorConstantsTest extends CategoryTest {
  private final Pattern timeoutPattern = Pattern.compile(NGRegexValidatorConstants.TIMEOUT_PATTERN);

  @Test
  @Owner(developers = SAMARTH)
  @Category(UnitTests.class)
  public void testTimeoutPattern() {
    // Valid cases
    assertTrue(timeoutPattern.matcher("1m").matches());
    assertTrue(timeoutPattern.matcher("1m20s").matches());
    assertTrue(timeoutPattern.matcher("1m 20s").matches());

    // Runtime
    assertTrue(timeoutPattern.matcher("<+input>").matches());
    assertTrue(timeoutPattern.matcher("<+input>.allowedValues()").matches());
    assertTrue(timeoutPattern.matcher("<+input>.regex()").matches());
    assertTrue(timeoutPattern.matcher("<+2+8>s").matches());
    assertTrue(timeoutPattern.matcher("<+random>").matches());

    // Invalid cases
    assertFalse(timeoutPattern.matcher("1m  20s").matches());
    assertFalse(timeoutPattern.matcher("1m 8").matches());
    assertFalse(timeoutPattern.matcher("18").matches());
    assertFalse(timeoutPattern.matcher("m").matches());
    assertFalse(timeoutPattern.matcher("20mm").matches());
    assertFalse(timeoutPattern.matcher("m20m").matches());
    assertFalse(timeoutPattern.matcher(" 1m").matches());
    assertFalse(timeoutPattern.matcher("1m ").matches());
    assertFalse(timeoutPattern.matcher("1 m").matches());
    assertFalse(timeoutPattern.matcher("1a").matches());
    assertFalse(timeoutPattern.matcher("random").matches());
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void timeoutEmptyAllowed() {
    assertTrue(timeoutPattern.matcher("").matches());
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void timeoutWhitespaceNotAllowed() {
    assertFalse(timeoutPattern.matcher("   ").matches());
    assertFalse(timeoutPattern.matcher(" ABC ").matches());
    assertFalse(timeoutPattern.matcher(" ABC").matches());
    assertFalse(timeoutPattern.matcher("ABC ").matches());
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testNonEmptyStringPattern() {
    final Pattern nonEmptyStringPattern = Pattern.compile(NGRegexValidatorConstants.NON_EMPTY_STRING_PATTERN);
    assertTrue(nonEmptyStringPattern.matcher("ABC ").matches());
    assertTrue(nonEmptyStringPattern.matcher("1").matches());
    assertTrue(nonEmptyStringPattern.matcher("org.serviceRef").matches());
    assertTrue(nonEmptyStringPattern.matcher("serviceRef_1").matches());
    assertTrue(nonEmptyStringPattern.matcher("#_1").matches());

    assertTrue(nonEmptyStringPattern.matcher("<+input>").matches());
    assertTrue(nonEmptyStringPattern.matcher("<+input>.allowedValues()").matches());
    assertTrue(nonEmptyStringPattern.matcher("<+input>.regex()").matches());
    assertTrue(nonEmptyStringPattern.matcher("<+2+8>s").matches());
    assertTrue(nonEmptyStringPattern.matcher("<+random>").matches());

    assertFalse(nonEmptyStringPattern.matcher("  ").matches());
    assertFalse(nonEmptyStringPattern.matcher("").matches());
  }
}
