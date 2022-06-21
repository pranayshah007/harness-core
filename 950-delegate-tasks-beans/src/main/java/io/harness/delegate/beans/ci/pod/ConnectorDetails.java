/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.ci.pod;

import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;

@Data
@Builder
public class ConnectorDetails {
  @NotNull ConnectorConfigDTO connectorConfig;
  @NotNull ConnectorType connectorType;
  @NotNull String identifier;
  String orgIdentifier;
  String projectIdentifier;
  Set<String> delegateSelectors;
  @NotNull List<EncryptedDataDetail> encryptedDataDetails;
  SSHKeyDetails sshKeyDetails;
  @Singular("envToSecretEntry") Map<EnvVariableEnum, String> envToSecretsMap;
}
