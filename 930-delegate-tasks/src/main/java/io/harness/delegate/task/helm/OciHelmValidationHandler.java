/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.helm;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.network.Http.connectableHost;
import static io.harness.state.StateConstants.DEFAULT_STEADY_STATE_TIMEOUT;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.ConnectorValidationResult.ConnectorValidationResultBuilder;
import io.harness.connector.task.ConnectorValidationHandler;
import io.harness.delegate.beans.connector.ConnectorValidationParams;
import io.harness.delegate.beans.connector.helm.OciHelmAuthType;
import io.harness.delegate.beans.connector.helm.OciHelmConnectorDTO;
import io.harness.delegate.beans.connector.helm.OciHelmValidationParams;
import io.harness.errorhandling.NGErrorHelper;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.k8s.model.HelmVersion;
import io.harness.security.encryption.SecretDecryptionService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.net.URI;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(CDP)
public class OciHelmValidationHandler implements ConnectorValidationHandler {
  private static final String WORKING_DIR_BASE = "./repository/helm-validation/";
  private static final HelmVersion defaultHelmVersion = HelmVersion.V380;
  private static final long DEFAULT_TIMEOUT_IN_MILLIS = Duration.ofMinutes(DEFAULT_STEADY_STATE_TIMEOUT).toMillis();

  @Inject private HelmTaskHelperBase helmTaskHelperBase;
  @Inject private NGErrorHelper ngErrorHelper;
  @Inject private SecretDecryptionService decryptionService;

  @Override
  public ConnectorValidationResult validate(
      ConnectorValidationParams connectorValidationParams, String accountIdentifier) {
    final OciHelmValidationParams helmValidationParams = (OciHelmValidationParams) connectorValidationParams;
    OciHelmConnectorDTO ociHelmConnectorDTO = helmValidationParams.getOciHelmConnectorDTO();
    String ociUrl = ociHelmConnectorDTO.getHelmRepoUrl();
    ConnectorValidationResultBuilder validationResultBuilder = ConnectorValidationResult.builder();
    try {
      log.info("Running OciHelmValidationHandler for account {} connector {}", accountIdentifier,
          connectorValidationParams.getConnectorName());

      decryptEncryptedDetails(helmValidationParams);
      if (OciHelmAuthType.ANONYMOUS.equals(ociHelmConnectorDTO.getAuth().getAuthType())) {
        URI parsedUri = helmTaskHelperBase.getParsedURI(ociUrl);
        if (!connectableHost(parsedUri.getHost(), parsedUri.getPort())) {
          throw new InvalidRequestException(format("Invalid oci url  %s", ociUrl));
        }
      } else if (OciHelmAuthType.USER_PASSWORD.equals(ociHelmConnectorDTO.getAuth().getAuthType())) {
        String workingDirectory = helmTaskHelperBase.createNewDirectoryAtPath(Paths.get(WORKING_DIR_BASE).toString());
        helmTaskHelperBase.loginOciRegistry(helmTaskHelperBase.getParsedUrlForUserNamePwd(ociUrl),
            helmTaskHelperBase.getOciHelmUsername(ociHelmConnectorDTO),
            helmTaskHelperBase.getOciHelmPassword(ociHelmConnectorDTO), HelmVersion.V380, DEFAULT_TIMEOUT_IN_MILLIS,
            workingDirectory, "");
        helmTaskHelperBase.cleanup(workingDirectory);
      } else {
        throw new InvalidRequestException(
            format("Invalid oci auth type  %s", ociHelmConnectorDTO.getAuth().getAuthType()));
      }
      validationResultBuilder.status(ConnectivityStatus.SUCCESS);
    } catch (Exception exception) {
      log.error("OciHelmValidationHandler execution failed with exception ", exception);
      validationResultBuilder.status(ConnectivityStatus.FAILURE);
      String errorMessage = ExceptionUtils.getMessage(exception);
      validationResultBuilder.errorSummary(ngErrorHelper.getErrorSummary(errorMessage))
          .errors(Collections.singletonList(ngErrorHelper.createErrorDetail(errorMessage)));
    }

    return validationResultBuilder.build();
  }

  private void decryptEncryptedDetails(OciHelmValidationParams helmValidationParams) {
    final List<DecryptableEntity> decryptableEntityList =
        helmValidationParams.getOciHelmConnectorDTO().getDecryptableEntities();

    for (DecryptableEntity entity : decryptableEntityList) {
      decryptionService.decrypt(entity, helmValidationParams.getEncryptionDataDetails());
      ExceptionMessageSanitizer.storeAllSecretsForSanitizing(entity, helmValidationParams.getEncryptionDataDetails());
    }
  }
}
