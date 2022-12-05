/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.secretmanagerclient.dto;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.security.encryption.common.AdditionalMetadata;
import io.harness.security.encryption.common.EncryptedDataParams;
import io.harness.security.encryption.common.EncryptedRecord;
import io.harness.security.encryption.common.EncryptionType;

import software.wings.settings.SettingVariableTypes;

import java.util.Set;
import lombok.Builder;
import lombok.Data;

@OwnedBy(PL)
@Data
@Builder
public class EncryptedDataMigrationDTO implements EncryptedRecord {
  private String uuid;
  private String name;
  private String path;
  private Set<EncryptedDataParams> parameters;
  private String encryptionKey;
  private char[] encryptedValue;
  private String kmsId;
  private EncryptionType encryptionType;
  private char[] backupEncryptedValue;
  private String backupEncryptionKey;
  private String backupKmsId;
  private EncryptionType backupEncryptionType;
  private boolean base64Encoded;
  private AdditionalMetadata additionalMetadata;
  private SettingVariableTypes type;

  private String accountIdentifier;
  private String orgIdentifier;
  private String projectIdentifier;
  private String identifier;
}
