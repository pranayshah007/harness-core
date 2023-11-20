/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.webhook.errorhandler.handlers.github;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.ScmBadRequestException;
import io.harness.exception.ScmUnauthorizedException;
import io.harness.exception.WingsException;
import io.harness.ng.webhook.errorhandler.dtos.ErrorMetadata;
import io.harness.ng.webhook.errorhandler.handlers.ScmApiErrorHandler;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.SPG)
public class GithubUpsertWebhookScmApiErrorHandler implements ScmApiErrorHandler {
  @Override
  public void handleError(int statusCode, String errorMessage, ErrorMetadata errorMetadata) throws WingsException {
    switch (statusCode) {
      case 401:
        throw new ScmUnauthorizedException(
            String.format("The credentials provided in the Github connector %s are invalid or have expired. ",
                errorMetadata.getConnectorRef())
            + errorMessage);
      case 403:
        throw new ScmBadRequestException(
            "Please check if webhook read write permission is given to token/GithubApp. " + errorMessage);
      case 404:
        throw new ScmBadRequestException(
            "Please check if the requested Github repository exists and if webhook read-write permissions are given both to the token/GithubApp used in the connector as well as to the user to whom the token belongs. "
            + errorMessage);
      case 422:
        throw new ScmBadRequestException("Please check if the secret provided for webhook is valid. " + errorMessage);
      default:
        log.error(String.format("Error while performing upsert operation: [%s: %s]", statusCode, errorMessage));
        if (isEmpty(errorMessage)) {
          throw new ScmBadRequestException("Error while performing upsert operation.");
        } else {
          throw new ScmBadRequestException(errorMessage);
        }
    }
  }
}
