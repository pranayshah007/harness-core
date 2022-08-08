/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.storeconfig;

import io.harness.delegate.beans.connector.helm.OciHelmConnectorDTO;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class OciHelmStoreDelegateConfig implements StoreDelegateConfig {
  String repoName;
  String basePath;
  String repoDisplayName;
  OciHelmConnectorDTO ociHelmConnector;
  List<EncryptedDataDetail> encryptedDataDetails;
  boolean helmOciEnabled;

  @Override
  public StoreDelegateConfigType getType() {
    return StoreDelegateConfigType.OCI_HELM;
  }
}
