/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.task.aws;

import static io.harness.utils.FieldWithPlainTextOrSecretValueHelper.getSecretAsStringFromPlainTextOrSecretRef;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.AwsAccessKeyCredential;
import io.harness.aws.AwsConfig;
import io.harness.aws.CrossAccountAccess;
import io.harness.connector.helper.DecryptionHelper;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.beans.connector.awsconnector.CrossAccountAccessDTO;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.govern.Switch;
import io.harness.security.encryption.EncryptedDataDetail;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;

@OwnedBy(HarnessTeam.CI)
@Singleton
public class AwsNgConfigMapper {
  @Inject private DecryptionHelper decryptionHelper;

  private CrossAccountAccess mapCrossAccountAccess(CrossAccountAccessDTO crossAccountAccess) {
    if (crossAccountAccess == null) {
      return null;
    }
    return CrossAccountAccess.builder()
        .crossAccountRoleArn(crossAccountAccess.getCrossAccountRoleArn())
        .externalId(crossAccountAccess.getExternalId())
        .build();
  }

  public AwsConfig mapAwsConfigWithDecryption(
      AwsConnectorDTO awsConnectorDTO, List<EncryptedDataDetail> encryptionDetails) {
    AwsCredentialDTO credential = awsConnectorDTO.getCredential();
    AwsCredentialType awsCredentialType = awsConnectorDTO.getCredential().getAwsCredentialType();
    boolean executeOnDelegate = awsConnectorDTO.getExecuteOnDelegate();
    AwsConfig awsConfig = null;

    if (!executeOnDelegate
        && (awsCredentialType == AwsCredentialType.INHERIT_FROM_DELEGATE
            || awsCredentialType == AwsCredentialType.IRSA)) {
      throw new InvalidRequestException(
          format("Connector with credential type %s does not support validation through harness", awsCredentialType));
    }

    switch (awsCredentialType) {
      case MANUAL_CREDENTIALS:
        AwsManualConfigSpecDTO config = (AwsManualConfigSpecDTO) credential.getConfig();
        config = (AwsManualConfigSpecDTO) decryptionHelper.decrypt(config, encryptionDetails);
        final SecretRefData secretKeyRef = config.getSecretKeyRef();
        awsConfig = AwsConfig.builder()
                        .crossAccountAccess(mapCrossAccountAccess(credential.getCrossAccountAccess()))
                        .awsAccessKeyCredential(AwsAccessKeyCredential.builder()
                                                    .accessKey(getSecretAsStringFromPlainTextOrSecretRef(
                                                        config.getAccessKey(), config.getAccessKeyRef()))
                                                    .secretKey(getDecryptedValueWithNullCheck(secretKeyRef))
                                                    .build())
                        .build();
        break;
      case INHERIT_FROM_DELEGATE:
        awsConfig = AwsConfig.builder()
                        .isEc2IamCredentials(true)
                        .crossAccountAccess(mapCrossAccountAccess(credential.getCrossAccountAccess()))
                        .build();
        break;
      case IRSA:
        awsConfig = AwsConfig.builder()
                        .isIRSA(true)
                        .crossAccountAccess(mapCrossAccountAccess(credential.getCrossAccountAccess()))
                        .build();
        break;
      default:
        Switch.unhandled(awsCredentialType);
    }
    return awsConfig;
  }

  private char[] getDecryptedValueWithNullCheck(SecretRefData passwordRef) {
    if (passwordRef != null) {
      return passwordRef.getDecryptedValue();
    }
    return null;
  }
}
