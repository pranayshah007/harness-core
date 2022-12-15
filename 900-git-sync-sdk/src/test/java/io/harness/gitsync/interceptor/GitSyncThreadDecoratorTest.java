/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.interceptor;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.rule.OwnerRule.ABHINAV;
import static io.harness.rule.OwnerRule.ADITHYA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(DX)
public class GitSyncThreadDecoratorTest extends CategoryTest {
  GitSyncThreadDecorator gitSyncThreadDecorator = new GitSyncThreadDecorator();

  public static final String ERROR_MESSAGE = "Unsupported char '%' present in the filepath: abc%def.yaml";

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testDecorate() {
    MultivaluedMap<String, String> queryParameters = new MultivaluedHashMap<>();
    queryParameters.add("branch", "branch");
    final String branch = gitSyncThreadDecorator.getRequestParamFromContext("branch", null, queryParameters);
    assertThat(branch).isEqualTo("branch");

    MultivaluedMap<String, String> queryParameters_1 = new MultivaluedHashMap<>();

    queryParameters_1.add("branch", "5%2F10");
    final String branch_1 = gitSyncThreadDecorator.getRequestParamFromContext("branch", null, queryParameters_1);
    assertThat(branch_1).isEqualTo("5/10");

    queryParameters_1.add("newField", "ERR@#$%&!@#$%&()_+GHJ");
    final String new_field =
        gitSyncThreadDecorator.getRequestParamFromContextWithoutDecoding("newField", queryParameters_1);
    assertThat(new_field).isEqualTo("ERR@#$%&!@#$%&()_+GHJ");
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testDecorateSpecialChar() {
    MultivaluedMap<String, String> queryParameters = new MultivaluedHashMap<>();
    queryParameters.add("filePath", "abc%def.yaml");

    Exception exception = assertThrows(InvalidRequestException.class,
        () -> { gitSyncThreadDecorator.getRequestParamFromContext("filePath", null, queryParameters); });
    assertEquals(ERROR_MESSAGE, exception.getMessage());
  }
}
