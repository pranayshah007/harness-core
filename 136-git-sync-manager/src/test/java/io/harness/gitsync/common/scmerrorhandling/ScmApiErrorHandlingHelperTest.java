/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.scmerrorhandling;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.BHAVYA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.exception.HintException;
import io.harness.exception.WingsException;
import io.harness.gitsync.common.beans.ScmApis;
import io.harness.gitsync.common.scmerrorhandling.handlers.ScmApiErrorHandler;
import io.harness.gitsync.common.scmerrorhandling.handlers.bitbucket.BitbucketListRepoScmApiErrorHandler;
import io.harness.gitsync.common.scmerrorhandling.handlers.github.GithubListRepoScmApiErrorHandler;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;

@OwnedBy(PL)
public class ScmApiErrorHandlingHelperTest extends CategoryTest {
  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testGetScmApiErrorHandler() {
    ScmApiErrorHandler scmApiErrorHandler =
        ScmApiErrorHandlingHelper.getScmAPIErrorHandler(ScmApis.LIST_REPOSITORIES, ConnectorType.BITBUCKET);
    assertThat(scmApiErrorHandler.getClass()).isEqualTo(BitbucketListRepoScmApiErrorHandler.class);

    scmApiErrorHandler =
        ScmApiErrorHandlingHelper.getScmAPIErrorHandler(ScmApis.LIST_REPOSITORIES, ConnectorType.GITHUB);
    assertThat(scmApiErrorHandler.getClass()).isEqualTo(GithubListRepoScmApiErrorHandler.class);
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testProcessAndThrowErrorForListRepo() throws WingsException {
    assertThatThrownBy(()
                           -> ScmApiErrorHandlingHelper.processAndThrowError(
                               ScmApis.LIST_REPOSITORIES, ConnectorType.BITBUCKET, 403, "Not Authorised"))
        .isInstanceOf(HintException.class);
  }

  @Test
  @Owner(developers = BHAVYA)
  @Category(UnitTests.class)
  public void testProcessAndThrowErrorForListBranches() throws WingsException {
    assertThatThrownBy(()
                           -> ScmApiErrorHandlingHelper.processAndThrowError(
                               ScmApis.LIST_BRANCHES, ConnectorType.GITHUB, 404, "Repo Not Found"))
        .isInstanceOf(HintException.class);
  }
}
