/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.mappers.awsmapper;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.entities.embedded.awsconnector.AwsAccessKeyCredential;
import io.harness.connector.entities.embedded.awsconnector.AwsConfig;
import io.harness.connector.entities.embedded.awsconnector.AwsConfig.AwsConfigBuilder;
import io.harness.connector.mappers.ConnectorDTOToEntityMapper;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.encryption.SecretRefHelper;
import io.harness.exception.InvalidRequestException;

import com.google.inject.Singleton;

@OwnedBy(HarnessTeam.DX)
@Singleton
public class AwsDTOToEntity implements ConnectorDTOToEntityMapper<AwsConnectorDTO, AwsConfig> {
  @Override
  public AwsConfig toConnectorEntity(AwsConnectorDTO connectorDTO) {
    final AwsCredentialDTO credential = connectorDTO.getCredential();
    final AwsCredentialType credentialType = credential.getAwsCredentialType();
    AwsConfigBuilder awsConfigBuilder;
    switch (credentialType) {
      case INHERIT_FROM_DELEGATE:
        awsConfigBuilder = buildInheritFromDelegate(credential);
        break;
      case MANUAL_CREDENTIALS:
        awsConfigBuilder = buildManualCredential(credential);
        break;
      case IRSA:
        awsConfigBuilder = buildIRSA(credential);
        break;
      default:
        throw new InvalidRequestException("Invalid Credential type.");
    }
    return awsConfigBuilder.crossAccountAccess(credential.getCrossAccountAccess())
        .testRegion(credential.getTestRegion())
        .build();
  }

  private AwsConfigBuilder buildInheritFromDelegate(AwsCredentialDTO connector) {
    return AwsConfig.builder().credentialType(AwsCredentialType.INHERIT_FROM_DELEGATE).credential(null);
  }

  private AwsConfigBuilder buildManualCredential(AwsCredentialDTO connector) {
    final AwsManualConfigSpecDTO config = (AwsManualConfigSpecDTO) connector.getConfig();
    final String secretKeyRef = SecretRefHelper.getSecretConfigString(config.getSecretKeyRef());
    final String accessKeyRef = SecretRefHelper.getSecretConfigString(config.getAccessKeyRef());
    AwsAccessKeyCredential accessKeyCredential = AwsAccessKeyCredential.builder()
                                                     .accessKey(config.getAccessKey())
                                                     .accessKeyRef(accessKeyRef)
                                                     .secretKeyRef(secretKeyRef)
                                                     .build();
    return AwsConfig.builder().credentialType(AwsCredentialType.MANUAL_CREDENTIALS).credential(accessKeyCredential);
  }

  private AwsConfigBuilder buildIRSA(AwsCredentialDTO connector) {
    return AwsConfig.builder().credentialType(AwsCredentialType.IRSA).credential(null);
  }
}
