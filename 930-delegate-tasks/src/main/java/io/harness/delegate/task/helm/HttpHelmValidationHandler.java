/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.helm;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.UUIDGenerator.convertBase64UuidToCanonicalForm;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.state.StateConstants.DEFAULT_STEADY_STATE_TIMEOUT;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.ConnectorValidationResult.ConnectorValidationResultBuilder;
import io.harness.connector.task.ConnectorValidationHandler;
import io.harness.delegate.beans.connector.ConnectorValidationParams;
import io.harness.delegate.beans.connector.helm.HttpHelmValidationParams;
import io.harness.errorhandling.NGErrorHelper;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.k8s.model.HelmVersion;
import io.harness.security.encryption.SecretDecryptionService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(CDP)
public class HttpHelmValidationHandler implements ConnectorValidationHandler {
  private static final String WORKING_DIR_BASE = "./repository/helm-validation/";
  private static final HelmVersion defaultHelmVersion = HelmVersion.V3;
  private static final long DEFAULT_TIMEOUT_IN_MILLIS = Duration.ofMinutes(DEFAULT_STEADY_STATE_TIMEOUT).toMillis();

  @Inject private HelmTaskHelperBase helmTaskHelperBase;
  @Inject private NGErrorHelper ngErrorHelper;
  @Inject private SecretDecryptionService decryptionService;

  @Override
  public ConnectorValidationResult validate(
      ConnectorValidationParams connectorValidationParams, String accountIdentifier) {
    final HttpHelmValidationParams helmValidationParams = (HttpHelmValidationParams) connectorValidationParams;
    ConnectorValidationResultBuilder validationResultBuilder = ConnectorValidationResult.builder();
    try {
      log.info("Running HttpHelmValidationHandler for account {} connector {}", accountIdentifier,
          connectorValidationParams.getConnectorName());

      decryptEncryptedDetails(helmValidationParams);

      String workingDirectory = helmTaskHelperBase.createNewDirectoryAtPath(Paths.get(WORKING_DIR_BASE).toString());
      helmTaskHelperBase.initHelm(workingDirectory, defaultHelmVersion, DEFAULT_TIMEOUT_IN_MILLIS);
      String repoName = convertBase64UuidToCanonicalForm(generateUuid());

      helmTaskHelperBase.addRepo(repoName, helmValidationParams.getConnectorName(),
          helmValidationParams.getHttpHelmConnectorDTO().getHelmRepoUrl(),
          helmTaskHelperBase.getHttpHelmUsername(helmValidationParams.getHttpHelmConnectorDTO()),
          helmTaskHelperBase.getHttpHelmPassword(helmValidationParams.getHttpHelmConnectorDTO()), workingDirectory,
          defaultHelmVersion, DEFAULT_TIMEOUT_IN_MILLIS, "");

      helmTaskHelperBase.removeRepo(repoName, workingDirectory, defaultHelmVersion, DEFAULT_TIMEOUT_IN_MILLIS);
      helmTaskHelperBase.cleanup(workingDirectory);
      validationResultBuilder.status(ConnectivityStatus.SUCCESS);
    } catch (IOException exception) {
      log.warn("HttpHelmValidationHandler execution failed with exception ", exception);
      validationResultBuilder.status(ConnectivityStatus.FAILURE);
      String errorMessage = ExceptionUtils.getMessage(exception);
      validationResultBuilder.errorSummary(ngErrorHelper.getErrorSummary(errorMessage))
          .errors(Collections.singletonList(ngErrorHelper.createErrorDetail(errorMessage)));
    }

    return validationResultBuilder.build();
  }

  private void decryptEncryptedDetails(HttpHelmValidationParams helmValidationParams) {
    final List<DecryptableEntity> decryptableEntityList =
        helmValidationParams.getHttpHelmConnectorDTO().getDecryptableEntities();

    for (DecryptableEntity entity : decryptableEntityList) {
      decryptionService.decrypt(entity, helmValidationParams.getEncryptionDataDetails());
      ExceptionMessageSanitizer.storeAllSecretsForSanitizing(entity, helmValidationParams.getEncryptionDataDetails());
    }
  }
}