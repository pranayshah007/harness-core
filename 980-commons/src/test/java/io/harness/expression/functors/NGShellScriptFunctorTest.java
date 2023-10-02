/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.expression.functors;

import static io.harness.rule.OwnerRule.SARTHAK_KASAT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.shell.ScriptType;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class NGShellScriptFunctorTest extends CategoryTest {
  private static final String ESCAPE_CHAR = "a'b\"c`d$e~f!g@h#i%j^k&l*m(n)o-p_r{s}t[]|;:u,v.w/x?y";
  public static final String ESCAPED_CHARS = "a\\'b\\\"c\\`d\\$e~f!g@h#i%j^k\\&l*m\\(n\\)o-p_r{s}t[]\\|\\;:u,v.w/x?y";

  @Test
  @Owner(developers = SARTHAK_KASAT)
  @Category(UnitTests.class)
  public void testEscapifyBash() {
    NGShellScriptFunctor shellScriptFunctor = new NGShellScriptFunctor(ScriptType.BASH);
    assertThat(shellScriptFunctor.escape(ESCAPE_CHAR)).isEqualTo(ESCAPED_CHARS);
  }

  @Test
  @Owner(developers = SARTHAK_KASAT)
  @Category(UnitTests.class)
  public void testEncloseString() {
    NGShellScriptFunctor shellScriptFunctor = new NGShellScriptFunctor(ScriptType.BASH);
    final String s = shellScriptFunctor.enclose("\'", "test");
    assertThat(s).isEqualTo("\'test\'");
  }

  @Test
  @Owner(developers = SARTHAK_KASAT)
  @Category(UnitTests.class)
  public void testEscapifyQuote() {
    NGShellScriptFunctor shellScriptFunctor = new NGShellScriptFunctor(ScriptType.BASH);
    final String s = shellScriptFunctor.escape("don't");
    assertThat(s).isEqualTo("don\\'t");
  }
  @Test
  @Owner(developers = SARTHAK_KASAT)
  @Category(UnitTests.class)
  public void testEscapeChars() {
    NGShellScriptFunctor shellScriptFunctor = new NGShellScriptFunctor(ScriptType.BASH);
    final String s = shellScriptFunctor.escapeChars("Use `host.name` for local host name.", "`U");
    assertThat(s).isEqualTo("\\Use \\`host.name\\` for local host name.");
  }
}
