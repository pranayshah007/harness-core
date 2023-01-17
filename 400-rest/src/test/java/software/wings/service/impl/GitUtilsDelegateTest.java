/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.rule.OwnerRule.YOGESH;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.utils.nonkryos.GitUtilsDelegate;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class GitUtilsDelegateTest extends WingsBaseTest {
  @Inject private GitUtilsDelegate gitUtilsDelegate;

  @Test
  @Owner(developers = YOGESH)
  @Category(UnitTests.class)
  public void testGetRequestDataFromFile() {
    String nonExistentPath = "/thisPathDoesNotExistOnDelegate/nonExistentFile.yaml";
    assertThatThrownBy(() -> gitUtilsDelegate.getRequestDataFromFile(nonExistentPath))
        .isInstanceOf(RuntimeException.class);
  }
}
