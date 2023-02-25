/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service;

import static io.harness.rule.OwnerRule.ABHINAV;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class MigrationResourceServiceTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();
  @InjectMocks MigrationResourceService migrationResourceService;

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void connectGraph() {
    // Assuming two groups
    // A,A,B,A,A
    int[] list = {0, 1, 2, 3, 4};
    for (int i = 0; i < list.length; i++) {
      for (int j = i + 1; j < list.length; j++) {
        if (i == 2 || j == 2) {
          continue;
        }
        migrationResourceService.connect(list, i, j);
      }
    }
    for (int i = 0; i < list.length; i++) {
      if (i == 2) {
        assertThat(list[i]).isEqualTo(2);
        continue;
      }
      assertThat(list[i]).isEqualTo(0);
    }
  }
}
