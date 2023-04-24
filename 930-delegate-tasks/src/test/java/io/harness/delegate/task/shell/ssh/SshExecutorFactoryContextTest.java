/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.shell.ssh;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.VITALIE;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(CDP)
@RunWith(MockitoJUnitRunner.class)
public class SshExecutorFactoryContextTest extends CategoryTest {
  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testEvaluateVariable() {
    SshExecutorFactoryContext ctx = SshExecutorFactoryContext.builder().build();
    ctx.getEnvironmentVariables().put("key1", "val1");
    ctx.getEnvironmentVariables().put("key2", "val2");

    String text = "before1 $key1 after1 before2 $key2 after2";
    String expected = "before1 val1 after1 before2 val2 after2";

    String result = ctx.evaluateVariable(text);
    assertThat(result).isEqualTo(expected);
  }

  @Test
  @Owner(developers = VITALIE)
  @Category(UnitTests.class)
  public void testAddEnvVariables() {
    SshExecutorFactoryContext ctx = SshExecutorFactoryContext.builder().build();
    ctx.getEnvironmentVariables().put("key1", "val1");
    Map<String, String> mapToAdd = new HashMap<>();
    mapToAdd.put("key2", "val2");
    ctx.addEnvVariables(mapToAdd);

    assertThat(ctx.getEnvironmentVariables().get("key1")).isEqualTo("val1");
    assertThat(ctx.getEnvironmentVariables().get("key2")).isEqualTo("val2");
  }
}
