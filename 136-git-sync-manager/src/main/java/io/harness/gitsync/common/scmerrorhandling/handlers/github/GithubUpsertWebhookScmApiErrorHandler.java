package io.harness.gitsync.common.scmerrorhandling.handlers.github;

import static io.harness.gitsync.common.scmerrorhandling.handlers.github.ScmErrorExplanations.INVALID_CONNECTOR_CREDS;
import static io.harness.gitsync.common.scmerrorhandling.handlers.github.ScmErrorHints.INVALID_CREDENTIALS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.ScmBadRequestException;
import io.harness.exception.ScmUnexpectedException;
import io.harness.exception.WingsException;
import io.harness.gitsync.common.scmerrorhandling.dtos.ErrorMetadata;
import io.harness.gitsync.common.scmerrorhandling.handlers.ScmApiErrorHandler;
import io.harness.gitsync.common.scmerrorhandling.util.ErrorMessageFormatter;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.SPG)
public class GithubUpsertWebhookScmApiErrorHandler implements ScmApiErrorHandler {
  @Override
  public void handleError(int statusCode, String errorMessage, ErrorMetadata errorMetadata) throws WingsException {
    switch (statusCode) {
      case 401:
        throw NestedExceptionUtils.hintWithExplanationException(
            ErrorMessageFormatter.formatMessage(INVALID_CREDENTIALS, errorMetadata),
            ErrorMessageFormatter.formatMessage(
                "The credentials provided in the Github connector<CONNECTOR> are invalid or have expired.",
                errorMetadata),
            new ScmBadRequestException(errorMessage));
      case 403:
        throw NestedExceptionUtils.hintWithExplanationException(
            ErrorMessageFormatter.formatMessage(
                "Please check if webhook read write permission is given to token/GithubApp.", errorMetadata),
            ErrorMessageFormatter.formatMessage(
                "Webhook read write permission is missing for given token/GithubApp", errorMetadata),
            new ScmBadRequestException(errorMessage));
      case 404:
        throw NestedExceptionUtils.hintWithExplanationException(
            ErrorMessageFormatter.formatMessage(
                ScmErrorHints.REPO_NOT_FOUND + "and if webhook read write permission is given to token/GithubApp",
                errorMetadata),
            ErrorMessageFormatter.formatMessage(
                "Provided Github repository<REPO> does not exist or has been deleted or webhook read write permission is missing for given token/GithubApp",
                errorMetadata),
            new ScmBadRequestException(errorMessage));
      case 422:
        throw NestedExceptionUtils.hintWithExplanationException(
            ErrorMessageFormatter.formatMessage(
                "Please check if the secret provided for webhook is valid.", errorMetadata),
            ErrorMessageFormatter.formatMessage("Secret provided for webhook is invalid.", errorMetadata),
            new ScmBadRequestException(errorMessage));
      default:
        log.error(String.format("Error while performing upsert operation: [%s: %s]", statusCode, errorMessage));
        throw new ScmUnexpectedException(errorMessage);
    }
  }
}
