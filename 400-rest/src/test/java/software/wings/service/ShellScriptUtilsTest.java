/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.LUCAS_SALES;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.service.impl.ShellScriptUtils;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDC)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class ShellScriptUtilsTest extends WingsBaseTest {
  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void testCommentOnlyScript() {
    String script = "#test\n#commentonly";
    String scriptWithLeadingSpaces = " #test\n #commentonly";
    boolean isNoopScript = ShellScriptUtils.isNoopScript(script);
    boolean isNoopScript2 = ShellScriptUtils.isNoopScript(scriptWithLeadingSpaces);
    assertThat(isNoopScript).isTrue();
    assertThat(isNoopScript2).isTrue();
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void testNullScript() {
    String script = null;
    String emptyScript = "         \n       ";
    boolean isNoopScript = ShellScriptUtils.isNoopScript(script);
    boolean isNoopScript2 = ShellScriptUtils.isNoopScript(emptyScript);
    assertThat(isNoopScript).isTrue();
    assertThat(isNoopScript2).isTrue();
  }

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void testRegularScript() {
    String script = "#test\nexecution";
    String script2 = "   \nexecution";
    boolean isNoopScript = ShellScriptUtils.isNoopScript(script);
    boolean isNoopScript2 = ShellScriptUtils.isNoopScript(script2);
    assertThat(isNoopScript).isFalse();
    assertThat(isNoopScript2).isFalse();
  }
}
