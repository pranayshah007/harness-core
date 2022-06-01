/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.aws;

import static io.harness.logging.CommandExecutionStatus.SUCCESS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.AwsClient;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsIAMRolesResponse;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsTaskParams;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.security.encryption.SecretDecryptionService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CDP)
public class AwsIAMDelegateTaskHelper {
  @Inject private SecretDecryptionService secretDecryptionService;
  @Inject private AwsClient awsApiHelperService;
  @Inject private AwsNgConfigMapper awsNgConfigMapper;

  public DelegateResponseData getIAMRoleList(AwsTaskParams awsTaskParams) {
    decryptRequestDTOs(awsTaskParams);
    AwsInternalConfig awsInternalConfig = getAwsInternalConfig(awsTaskParams);
    Map<String, String> roles = awsApiHelperService.listIAMRoles(awsInternalConfig);
    return AwsIAMRolesResponse.builder().roles(roles).commandExecutionStatus(SUCCESS).build();
  }

  private AwsInternalConfig getAwsInternalConfig(AwsTaskParams awsTaskParams) {
    AwsConnectorDTO awsConnectorDTO = awsTaskParams.getAwsConnector();
    AwsInternalConfig awsInternalConfig = awsNgConfigMapper.createAwsInternalConfig(awsConnectorDTO);
    awsInternalConfig.setDefaultRegion(awsTaskParams.getRegion());
    return awsInternalConfig;
  }

  private void decryptRequestDTOs(AwsTaskParams awsTaskParams) {
    AwsConnectorDTO awsConnectorDTO = awsTaskParams.getAwsConnector();
    if (awsConnectorDTO.getCredential() != null && awsConnectorDTO.getCredential().getConfig() != null) {
      secretDecryptionService.decrypt(
          (AwsManualConfigSpecDTO) awsConnectorDTO.getCredential().getConfig(), awsTaskParams.getEncryptionDetails());
      ExceptionMessageSanitizer.storeAllSecretsForSanitizing(
          (AwsManualConfigSpecDTO) awsConnectorDTO.getCredential().getConfig(), awsTaskParams.getEncryptionDetails());
    }
  }
}
