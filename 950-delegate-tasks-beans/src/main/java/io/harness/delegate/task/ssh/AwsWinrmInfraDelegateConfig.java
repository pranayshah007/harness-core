/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.ssh;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.ng.core.dto.secrets.WinRmCredentialsSpecDTO;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Builder;
import lombok.Getter;

@Getter
@OwnedBy(HarnessTeam.CDP)
@RecasterAlias("io.harness.delegate.task.ssh.AwsWinrmInfraDelegateConfig")
public class AwsWinrmInfraDelegateConfig extends AwsInfraDelegateConfig implements WinRmInfraDelegateConfig {
  Set<String> hosts;
  List<EncryptedDataDetail> encryptionDataDetails;
  WinRmCredentialsSpecDTO winRmCredentials;

  @Builder(builderMethodName = "winrmAwsBuilder")
  public AwsWinrmInfraDelegateConfig(AwsConnectorDTO awsConnectorDTO,
      List<EncryptedDataDetail> connectorEncryptionDataDetails, String region, List<String> vpcIds,
      Map<String, String> tags, String autoScalingGroupName, List<EncryptedDataDetail> encryptionDataDetails,
      WinRmCredentialsSpecDTO winRmCredentials) {
    super(awsConnectorDTO, connectorEncryptionDataDetails, region, vpcIds, tags, autoScalingGroupName);
    this.encryptionDataDetails = encryptionDataDetails;
    this.winRmCredentials = winRmCredentials;
  }
}
