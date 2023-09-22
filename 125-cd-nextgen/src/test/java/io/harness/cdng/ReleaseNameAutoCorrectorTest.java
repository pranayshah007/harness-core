/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng;

import static io.harness.cdng.ReleaseNameAutoCorrector.countLowerCaseAlphaNumericChars;
import static io.harness.cdng.ReleaseNameAutoCorrector.makeDnsCompliant;
import static io.harness.cdng.ReleaseNameAutoCorrector.removeTrailingNonAlphaNumericChars;
import static io.harness.cdng.ReleaseNameAutoCorrector.replaceNonAlphaNumericChars;
import static io.harness.cdng.ReleaseNameAutoCorrector.startWithAlphabeticChar;
import static io.harness.rule.OwnerRule.ABHINAV2;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ReleaseNameAutoCorrectorTest extends CategoryTest {
  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void firstCharTests() {
    assertThat(startWithAlphabeticChar("")).isEmpty();
    assertThat(startWithAlphabeticChar("1")).isEmpty();
    assertThat(startWithAlphabeticChar("$")).isEmpty();
    assertThat(startWithAlphabeticChar("11")).isEqualTo("r1");
    assertThat(startWithAlphabeticChar("abcd")).isEqualTo("abcd");
    assertThat(startWithAlphabeticChar("1bcd")).isEqualTo("bcd");
    assertThat(startWithAlphabeticChar("%bcd")).isEqualTo("bcd");
    assertThat(startWithAlphabeticChar("%$&bcd")).isEqualTo("r$&bcd");
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testReplacementOfNonAlphaNumericChars() {
    // no replacement
    assertThat(replaceNonAlphaNumericChars("", "-")).isEmpty();
    assertThat(replaceNonAlphaNumericChars("1", "-")).isEqualTo("1");
    assertThat(replaceNonAlphaNumericChars("11", "-")).isEqualTo("11");
    assertThat(replaceNonAlphaNumericChars("a", "-")).isEqualTo("a");
    assertThat(replaceNonAlphaNumericChars("a1", "-")).isEqualTo("a1");
    assertThat(replaceNonAlphaNumericChars("abcd", "-")).isEqualTo("abcd");
    assertThat(replaceNonAlphaNumericChars("a-b-c1", "-")).isEqualTo("a-b-c1");

    assertThat(replaceNonAlphaNumericChars("a&&c", "-")).isEqualTo("a-c");
    assertThat(replaceNonAlphaNumericChars("a&&c%^&", "-")).isEqualTo("a-c-");
    assertThat(replaceNonAlphaNumericChars("!@#$%", "-")).isEqualTo("-");
    assertThat(replaceNonAlphaNumericChars("@-#-*", "-")).isEqualTo("-");
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testTrailingCharRemoval() {
    assertThat(removeTrailingNonAlphaNumericChars("")).isEmpty();
    assertThat(removeTrailingNonAlphaNumericChars("^&*")).isEmpty();
    assertThat(removeTrailingNonAlphaNumericChars("a1%^&")).isEqualTo("a1");
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testAlphaNumericCharCount() {
    assertThat(countLowerCaseAlphaNumericChars("")).isZero();
    assertThat(countLowerCaseAlphaNumericChars("!@#$%^")).isZero();
    assertThat(countLowerCaseAlphaNumericChars("ABCD1234")).isEqualTo(4);
    assertThat(countLowerCaseAlphaNumericChars("abcde")).isEqualTo(5);
    assertThat(countLowerCaseAlphaNumericChars("a1b1c1")).isEqualTo(6);
  }

  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testAutocorrection() {
    // invalid release names
    assertThat(makeDnsCompliant("")).isEmpty();
    assertThat(makeDnsCompliant(generate('*', 128))).isEmpty();
    assertThat(makeDnsCompliant("1")).isEmpty();
    assertThat(makeDnsCompliant("    ")).isEmpty();

    // autocorrected names
    assertThat(makeDnsCompliant("r*&elease&1234()")).isEqualTo("r-elease-1234");
    assertThat(makeDnsCompliant(generate('a', 128))).isEqualTo(generate('a', 63));
    assertThat(makeDnsCompliant("release@123#%")).isEqualTo("release-123");
    assertThat(makeDnsCompliant("@abc")).isEqualTo("abc");
    assertThat(makeDnsCompliant("@#abc")).isEqualTo("r-abc");
    assertThat(makeDnsCompliant("@#$%abc")).isEqualTo("r-abc");
    assertThat(makeDnsCompliant("@@ @@ @a@ @@ @@")).isEqualTo("r-a");
    assertThat(makeDnsCompliant("release ")).isEqualTo("release");
    assertThat(makeDnsCompliant(" 2 release ")).isEqualTo("r2-release");
    assertThat(makeDnsCompliant("release test 123")).isEqualTo("release-test-123");
    assertThat(makeDnsCompliant("RELEASE1234")).isEqualTo("release1234");
    assertThat(makeDnsCompliant("1234")).isEqualTo("r234");
  }

  private String generate(char x, int length) {
    return StringUtils.repeat(x, length);
  }
}
