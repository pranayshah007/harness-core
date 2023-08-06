/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.storeconfig;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.scm.ScmConnector;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.List;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

@Value
@Builder(toBuilder = true)
@OwnedBy(CDP)
@RecasterAlias("io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig")
public class GitStoreDelegateConfig implements StoreDelegateConfig {
  String branch;
  String commitId;
  String connectorName;
  String connectorId;
  @Singular List<String> paths;
  FetchType fetchType;

  ScmConnector gitConfigDTO;
  List<EncryptedDataDetail> encryptedDataDetails;
  List<EncryptedDataDetail> apiAuthEncryptedDataDetails;
  SSHKeySpecDTO sshKeySpecDTO;
  String manifestType;
  String manifestId;
  private boolean optimizedFilesFetch;

  @Override
  public StoreDelegateConfigType getType() {
    return StoreDelegateConfigType.GIT;
  }
}
