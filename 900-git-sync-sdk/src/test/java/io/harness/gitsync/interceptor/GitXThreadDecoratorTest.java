/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.interceptor;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.MANKRIT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PL)
public class GitXThreadDecoratorTest extends CategoryTest {
  GitXThreadDecorator gitXThreadDecorator = new GitXThreadDecorator();

  @Test
  @Owner(developers = MANKRIT)
  @Category(UnitTests.class)
  public void testDecorate() {
    MultivaluedMap<String, String> queryParameters = new MultivaluedHashMap<>();
    queryParameters.add("branch", "branch");
    final String branch = gitXThreadDecorator.getRequestParamFromContext("branch", queryParameters);
    assertThat(branch).isEqualTo("branch");

    MultivaluedMap<String, String> queryParameters_1 = new MultivaluedHashMap<>();

    queryParameters_1.add("branch", "5%2F10");
    final String branch_1 = gitXThreadDecorator.getRequestParamFromContext("branch", queryParameters_1);
    assertThat(branch_1).isEqualTo("5/10");

    queryParameters_1.add("new_field", "ERR@#$%&!@#$%&()_+GHJ");
    final String new_field =
        gitXThreadDecorator.getRequestParamFromContextWithoutDecoding("new_field", queryParameters_1);
    assertThat(new_field).isEqualTo("ERR@#$%&!@#$%&()_+GHJ");
  }
}
