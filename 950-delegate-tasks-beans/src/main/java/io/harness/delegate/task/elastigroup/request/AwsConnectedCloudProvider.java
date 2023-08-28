/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.elastigroup.request;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(CDP)
@RecasterAlias("io.harness.delegate.task.elastigroup.request.AwsConnectedCloudProvider")
public class AwsConnectedCloudProvider implements ConnectedCloudProvider {
  private String connectorRef;
  private ConnectorInfoDTO connectorInfoDTO;
  private List<EncryptedDataDetail> encryptionDetails;
  private String region;
}
