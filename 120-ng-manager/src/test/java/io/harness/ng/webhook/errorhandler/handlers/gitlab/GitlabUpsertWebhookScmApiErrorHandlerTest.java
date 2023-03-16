/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.webhook.errorhandler.handlers.gitlab;

import static io.harness.rule.OwnerRule.MEET;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.ScmBadRequestException;
import io.harness.exception.ScmUnauthorizedException;
import io.harness.exception.WingsException;
import io.harness.ng.webhook.errorhandler.dtos.ErrorMetadata;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

public class GitlabUpsertWebhookScmApiErrorHandlerTest extends CategoryTest {
  @InjectMocks GitlabUpsertWebhookScmApiErrorHandler gitlabUpsertWebhookScmApiErrorHandler;

  private static final String errorMessage = "errorMessage";
  private static final String connectorRef = "connectorRef";

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testHandleErrorOnUnauthorizedResponse() {
    try {
      gitlabUpsertWebhookScmApiErrorHandler.handleError(
          401, errorMessage, ErrorMetadata.builder().connectorRef(connectorRef).build());
    } catch (Exception ex) {
      WingsException exception = ExceptionUtils.cause(ScmUnauthorizedException.class, ex);
      assertThat(exception).isNotNull();
      assertThat(exception.getMessage())
          .isEqualTo("The credentials provided in the Gitlab connector " + connectorRef
              + " are invalid or have expired. " + errorMessage);
    }
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testHandleErrorOnForbiddenResponse() {
    try {
      gitlabUpsertWebhookScmApiErrorHandler.handleError(
          403, errorMessage, ErrorMetadata.builder().connectorRef(connectorRef).build());
    } catch (Exception ex) {
      WingsException exception = ExceptionUtils.cause(ScmBadRequestException.class, ex);
      assertThat(exception).isNotNull();
      assertThat(exception.getMessage())
          .isEqualTo("Please check if webhook read write permission is given to token used in connector " + connectorRef
              + ". " + errorMessage);
    }
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testHandleErrorOnNotFoundResponse() {
    try {
      gitlabUpsertWebhookScmApiErrorHandler.handleError(
          404, errorMessage, ErrorMetadata.builder().connectorRef(connectorRef).build());
    } catch (Exception ex) {
      WingsException exception = ExceptionUtils.cause(ScmBadRequestException.class, ex);
      assertThat(exception).isNotNull();
      assertThat(exception.getMessage())
          .isEqualTo(
              "Please check if the requested Gitlab repository exists and if webhook read write permission is given to token. "
              + errorMessage);
    }
  }
}
