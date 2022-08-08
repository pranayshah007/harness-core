/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.scm.utils;

import static io.harness.rule.OwnerRule.BHAVYA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;

public class ScmConnectorHelperTest extends CategoryTest {
  String filePath = "filePath";
  String branch = "branch";
  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testGetFileUrlForGithub_ifBranchNameIsNull() {
    try {
      ScmConnectorHelper.validateGetFileUrlParams(null, filePath);
    } catch (WingsException ex) {
      assertThat(ex).isInstanceOf(InvalidRequestException.class);
    }
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testGetFileUrlForGithub_ifFilePathIsEmpty() {
    try {
      ScmConnectorHelper.validateGetFileUrlParams(branch, "");
    } catch (WingsException ex) {
      assertThat(ex).isInstanceOf(InvalidRequestException.class);
    }
  }
}
