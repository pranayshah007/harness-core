/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.schema.type.secretManagers;

import static io.harness.security.encryption.EncryptionType.AWS_SECRETS_MANAGER;
import static io.harness.security.encryption.EncryptionType.AZURE_VAULT;
import static io.harness.security.encryption.EncryptionType.GCP_KMS;
import static io.harness.security.encryption.EncryptionType.KMS;
import static io.harness.security.encryption.EncryptionType.VAULT;
import static io.harness.security.encryption.EncryptionType.VAULT_SSH;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.security.encryption.EncryptionType;

import software.wings.graphql.schema.type.QLEnum;

import lombok.Getter;

@TargetModule(HarnessModule._380_CG_GRAPHQL)
public enum QLSecretManagerType implements QLEnum {
  AWS_KMS(KMS),
  AWS_SECRET_MANAGER(AWS_SECRETS_MANAGER),
  HASHICORP_VAULT(VAULT),
  AZURE_KEY_VAULT(AZURE_VAULT),
  GCP_SECRETS_MANAGER(EncryptionType.GCP_SECRETS_MANAGER),
  GOOGLE_KMS(GCP_KMS),
  CUSTOM(EncryptionType.CUSTOM),
  SSH(VAULT_SSH);

  @Getter private final EncryptionType encryptionType;

  QLSecretManagerType(EncryptionType encryptionType) {
    this.encryptionType = encryptionType;
  }

  @Override
  public String getStringValue() {
    return this.getEncryptionType().name();
  }
}
