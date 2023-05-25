/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.sanitizer;

import static io.harness.rule.OwnerRule.BHAVYA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class HtmlInputSanitizerTest extends CategoryTest {
  private HtmlInputSanitizer htmlInputSanitizer;

  @Before
  public void initialize() {
    htmlInputSanitizer = new HtmlInputSanitizer();
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testHtmlInputSanitizer() {
    String name = "Evil<img src=https://poc.shellcode.se/spongebob-ninja.jpg><h1>Hacker</h1><svg/onload=alert()>";
    assertThat(htmlInputSanitizer.sanitizeInput(name)).isEqualTo("EvilHacker");
  }
}
