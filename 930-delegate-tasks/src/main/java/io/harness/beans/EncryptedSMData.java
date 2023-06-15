/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.security.encryption.AdditionalMetadata;
import io.harness.security.encryption.EncryptedDataParams;
import io.harness.security.encryption.EncryptionType;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.Set;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonTypeName("encryptedSMData")
@OwnedBy(HarnessTeam.CDP)
public class EncryptedSMData {
  String uuid;
  String name;
  String path;
  Set<EncryptedDataParams> parameters;
  String encryptionKey;
  char[] encryptedValue;
  String kmsId;
  EncryptionType encryptionType;
  char[] backupEncryptedValue;
  String backupEncryptionKey;
  String backupKmsId;
  EncryptionType backupEncryptionType;
  boolean base64Encoded;
  AdditionalMetadata additionalMetadata;
}
