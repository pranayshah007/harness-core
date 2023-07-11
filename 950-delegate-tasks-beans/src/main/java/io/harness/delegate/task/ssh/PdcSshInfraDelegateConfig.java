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
import io.harness.delegate.beans.connector.pdcconnector.PhysicalDataCenterConnectorDTO;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.List;
import java.util.Set;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@OwnedBy(HarnessTeam.CDP)
@RecasterAlias("io.harness.delegate.task.ssh.PdcSshInfraDelegateConfig")
public class PdcSshInfraDelegateConfig implements SshInfraDelegateConfig {
  Set<String> hosts;
  PhysicalDataCenterConnectorDTO physicalDataCenterConnectorDTO;
  List<EncryptedDataDetail> encryptionDataDetails;
  SSHKeySpecDTO sshKeySpecDto;
}
