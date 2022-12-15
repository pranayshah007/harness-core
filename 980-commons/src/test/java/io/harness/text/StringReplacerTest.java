/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.text;

import static io.harness.rule.OwnerRule.GARVIT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.text.resolver.ExpressionResolver;
import io.harness.text.resolver.StringReplacer;

import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class StringReplacerTest extends CategoryTest {
  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testStringReplacer() {
    String source = "abc def <+gh <+ij <+kl> > > <+mn>>> op";
    String expected = "abc def 1 2>> op";
    DummyExpressionResolver resolver = new DummyExpressionResolver();
    String resp = replace(resolver, source);
    assertThat(resp).isEqualTo(expected);

    List<String> expressions = resolver.getExpressions();
    assertThat(expressions).isNotEmpty();
    assertThat(expressions).containsExactly("gh <+ij <+kl> > ", "mn");
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testStringReplacerSuffixEscape() {
    String source =
        "abc <+ de \\ f > <+gh <+ij <+kl.select(\"<book\\><title\\>Harry Potter</title\\></book\\>\")> > > <+abc \\> def>>> op";
    String expected = "abc 1 2 3>> op";
    DummyExpressionResolver resolver = new DummyExpressionResolver();
    String resp = replace(resolver, source);
    assertThat(resp).isEqualTo(expected);

    List<String> expressions = resolver.getExpressions();
    assertThat(expressions).isNotEmpty();
    assertThat(expressions)
        .containsExactly(
            " de \\ f ", "gh <+ij <+kl.select(\"<book><title>Harry Potter</title></book>\")> > ", "abc > def");
  }

  private String replace(ExpressionResolver resolver, String source) {
    StringReplacer stringReplacer = new StringReplacer(resolver, "<+", ">");
    String resp = stringReplacer.replace(source);
    assertThat(resp).isNotNull();
    return resp;
  }
}
