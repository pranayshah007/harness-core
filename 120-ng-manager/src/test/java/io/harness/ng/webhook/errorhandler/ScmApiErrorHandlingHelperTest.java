/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.webhook.errorhandler;
import static io.harness.rule.OwnerRule.MEET;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ScmBadRequestException;
import io.harness.exception.ScmUnauthorizedException;
import io.harness.ng.webhook.constants.ScmApis;
import io.harness.ng.webhook.errorhandler.handlers.DefaultScmApiErrorHandler;
import io.harness.ng.webhook.errorhandler.utils.ScmApiErrorHandlerFactory;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class ScmApiErrorHandlingHelperTest extends CategoryTest {
  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testProcessAndThrowError() {
    assertThatThrownBy(()
                           -> ScmApiErrorHandlingHelper.processAndThrowError(
                               ScmApis.UPSERT_WEBHOOK, ConnectorType.GITHUB, "https://github.com", 401, "errorMessage"))
        .isInstanceOf(ScmUnauthorizedException.class)
        .hasMessage("The credentials provided in the Github connector null are invalid or have expired. errorMessage");

    assertThatThrownBy(()
                           -> ScmApiErrorHandlingHelper.processAndThrowError(ScmApis.UPSERT_WEBHOOK,
                               ConnectorType.GITHUB, "https://github.com", 401, "errorMessage", null))
        .isInstanceOf(ScmUnauthorizedException.class)
        .hasMessage("The credentials provided in the Github connector null are invalid or have expired. errorMessage");

    assertThatThrownBy(()
                           -> ScmApiErrorHandlingHelper.processAndThrowError(ScmApis.UPSERT_WEBHOOK,
                               ConnectorType.BITBUCKET, "https://bitbucket.org", 401, "errorMessage"))
        .isInstanceOf(ScmUnauthorizedException.class)
        .hasMessage("Please check if credentials provided in Bitbucket connector null are correct. errorMessage");

    assertThatThrownBy(()
                           -> ScmApiErrorHandlingHelper.processAndThrowError(
                               ScmApis.UPSERT_WEBHOOK, ConnectorType.GITLAB, "https://gitlab.com", 401, "errorMessage"))
        .isInstanceOf(ScmUnauthorizedException.class)
        .hasMessage("The credentials provided in the Gitlab connector null are invalid or have expired. errorMessage");

    assertThatThrownBy(()
                           -> ScmApiErrorHandlingHelper.processAndThrowError(
                               ScmApis.UPSERT_WEBHOOK, ConnectorType.AZURE, "https://azure.com", 401, "errorMessage"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Unknown connector type Azure");

    assertThatThrownBy(()
                           -> ScmApiErrorHandlingHelper.processAndThrowError(ScmApis.UPSERT_WEBHOOK,
                               ConnectorType.AZURE_REPO, "https://azure.com", 400, "errorMessage"))
        .isInstanceOf(ScmBadRequestException.class)
        .hasMessage("Please check if the requested Azure repository exists. errorMessage");
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testGetScmAPIErrorHandler() {
    Mockito.mockStatic(ScmApiErrorHandlerFactory.class);
    when(ScmApiErrorHandlerFactory.getHandler(any(), any())).thenReturn(null);
    assertThat(ScmApiErrorHandlingHelper
                   .getScmAPIErrorHandler(ScmApis.UPSERT_WEBHOOK, ConnectorType.GITHUB, "https://github.com")
                   .getClass())
        .isEqualTo(DefaultScmApiErrorHandler.class);
  }
}
