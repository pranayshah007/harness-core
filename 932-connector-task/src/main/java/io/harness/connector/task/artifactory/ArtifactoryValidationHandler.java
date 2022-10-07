/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.task.artifactory;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.artifactory.ArtifactoryClientImpl;
import io.harness.artifactory.ArtifactoryConfigRequest;
import io.harness.beans.DecryptableEntity;
import io.harness.connector.ConnectivityStatus;
import io.harness.connector.ConnectorValidationResult;
import io.harness.connector.helper.DecryptionHelper;
import io.harness.connector.task.ConnectorValidationHandler;
import io.harness.delegate.beans.connector.ConnectorValidationParams;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryUsernamePasswordAuthDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryValidationParams;
import io.harness.security.encryption.EncryptedDataDetail;

import com.google.inject.Inject;
import java.util.List;

public class ArtifactoryValidationHandler implements ConnectorValidationHandler {
  @Inject DecryptionHelper decryptionHelper;
  @Inject ArtifactoryClientImpl artifactoryService;
  @Inject ArtifactoryRequestMapper artifactoryRequestMapper;

  @Override
  public ConnectorValidationResult validate(
      ConnectorValidationParams connectorValidationParams, String accountIdentifier) {
    final ArtifactoryValidationParams artifactoryValidationParams =
        (ArtifactoryValidationParams) connectorValidationParams;
    final ArtifactoryConnectorDTO artifactoryConnectorDTO =
        ((ArtifactoryValidationParams) connectorValidationParams).getArtifactoryConnectorDTO();
    final List<EncryptedDataDetail> encryptedDataDetails = artifactoryValidationParams.getEncryptedDataDetails();
    if (isNotEmpty(encryptedDataDetails)) {
      DecryptableEntity decryptedCredentials =
          decryptionHelper.decrypt(artifactoryConnectorDTO.getAuth().getCredentials(), encryptedDataDetails);
      artifactoryConnectorDTO.getAuth().setCredentials((ArtifactoryUsernamePasswordAuthDTO) decryptedCredentials);
    }
    final ArtifactoryConfigRequest artifactoryConfigRequest =
        artifactoryRequestMapper.toArtifactoryRequest(artifactoryConnectorDTO);
    ConnectorValidationResult connectorValidationResult;
    boolean running = artifactoryService.validateArtifactServer(artifactoryConfigRequest);
    if (running) {
      connectorValidationResult = ConnectorValidationResult.builder()
                                      .status(ConnectivityStatus.SUCCESS)
                                      .testedAt(System.currentTimeMillis())
                                      .build();
    } else {
      connectorValidationResult = ConnectorValidationResult.builder()
                                      .status(ConnectivityStatus.FAILURE)
                                      .testedAt(System.currentTimeMillis())
                                      .build();
    }
    return connectorValidationResult;
  }
}
