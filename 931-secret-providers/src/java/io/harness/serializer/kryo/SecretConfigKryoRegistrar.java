/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer.kryo;

import io.harness.serializer.KryoRegistrar;

import software.wings.beans.AwsSecretsManagerConfig;
import software.wings.beans.AzureVaultConfig;
import software.wings.beans.BaseVaultConfig;
import software.wings.beans.CustomSecretNGManagerConfig;
import software.wings.beans.GcpKmsConfig;
import software.wings.beans.GcpSecretsManagerConfig;
import software.wings.beans.KmsConfig;
import software.wings.beans.LocalEncryptionConfig;
import software.wings.beans.SSHVaultConfig;
import software.wings.beans.VaultConfig;

import com.esotericsoftware.kryo.Kryo;

public class SecretConfigKryoRegistrar implements KryoRegistrar {
  @Override
  public void register(final Kryo kryo) {
    kryo.register(AwsSecretsManagerConfig.class, 7178);
    kryo.register(AzureVaultConfig.class, 7205);
    kryo.register(BaseVaultConfig.class, 15014);
    kryo.register(CustomSecretNGManagerConfig.class, 40114);
    kryo.register(GcpKmsConfig.class, 7290);
    kryo.register(GcpSecretsManagerConfig.class, 72100);
    kryo.register(KmsConfig.class, 5183);
    kryo.register(LocalEncryptionConfig.class, 7180);
    kryo.register(SSHVaultConfig.class, 15012);
    kryo.register(VaultConfig.class, 5214);
    //    kryo.register(CustomSecretsManagerConfig.class, 7378); // brings in entire 930-delegate-tasks
  }
}
