/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.scmerrorhandling;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.ADITHYA;
import static io.harness.rule.OwnerRule.BHAVYA;
import static io.harness.rule.OwnerRule.MOHIT_GARG;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.exception.HintException;
import io.harness.exception.WingsException;
import io.harness.gitsync.common.beans.ScmApis;
import io.harness.gitsync.common.scmerrorhandling.dtos.ErrorMetadata;
import io.harness.gitsync.common.scmerrorhandling.handlers.ScmApiErrorHandler;
import io.harness.gitsync.common.scmerrorhandling.handlers.bitbucketcloud.BitbucketListRepoScmApiErrorHandler;
import io.harness.gitsync.common.scmerrorhandling.handlers.bitbucketserver.BitbucketServerListRepoScmApiErrorHandler;
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
    ScmApiErrorHandler scmApiErrorHandler = ScmApiErrorHandlingHelper.getScmAPIErrorHandler(
        ScmApis.LIST_REPOSITORIES, ConnectorType.BITBUCKET, "https://bitbucket.org/");
    assertThat(scmApiErrorHandler.getClass()).isEqualTo(BitbucketListRepoScmApiErrorHandler.class);

    scmApiErrorHandler = ScmApiErrorHandlingHelper.getScmAPIErrorHandler(
        ScmApis.LIST_REPOSITORIES, ConnectorType.BITBUCKET, "https://dev.harness.bitbucket.com/");
    assertThat(scmApiErrorHandler.getClass()).isEqualTo(BitbucketServerListRepoScmApiErrorHandler.class);

    scmApiErrorHandler = ScmApiErrorHandlingHelper.getScmAPIErrorHandler(
        ScmApis.LIST_REPOSITORIES, ConnectorType.GITHUB, "https://github.com/");
    assertThat(scmApiErrorHandler.getClass()).isEqualTo(GithubListRepoScmApiErrorHandler.class);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testProcessAndThrowErrorForEmptyMessage() throws WingsException {
    Exception exception = assertThrows(HintException.class, () -> {
      ScmApiErrorHandlingHelper.processAndThrowError(ScmApis.LIST_BRANCHES, ConnectorType.GITHUB, "https://github.com/",
          404, "", ErrorMetadata.builder().connectorRef("connectorRef").build());
    });
    assertEquals(ScmApiErrorHandlingHelper.DEFAULT_ERROR_MESSAGE, exception.getCause().getCause().getMessage());
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testIsFailureResponse() {
    assertThat(ScmApiErrorHandlingHelper.isFailureResponse(203, ConnectorType.AZURE_REPO)).isTrue();
    assertThat(ScmApiErrorHandlingHelper.isFailureResponse(200, ConnectorType.AZURE_REPO)).isFalse();
    assertThat(ScmApiErrorHandlingHelper.isFailureResponse(203, ConnectorType.CE_AZURE)).isFalse();
    assertThat(ScmApiErrorHandlingHelper.isFailureResponse(300, ConnectorType.GITHUB)).isTrue();
    assertThat(ScmApiErrorHandlingHelper.isFailureResponse(400, ConnectorType.GITHUB)).isTrue();
    assertThat(ScmApiErrorHandlingHelper.isFailureResponse(500, ConnectorType.GITHUB)).isTrue();
  }
}
