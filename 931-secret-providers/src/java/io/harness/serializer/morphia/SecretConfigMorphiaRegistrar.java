/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.serializer.morphia;

import io.harness.beans.SecretManagerConfig;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.morphia.MorphiaRegistrarHelperPut;

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

import java.util.Set;

public class SecretConfigMorphiaRegistrar implements MorphiaRegistrar {
  @Override
  public void registerClasses(final Set<Class> set) {
    set.add(AwsSecretsManagerConfig.class);
    set.add(AzureVaultConfig.class);
    set.add(BaseVaultConfig.class);
    set.add(CustomSecretNGManagerConfig.class);
    set.add(GcpKmsConfig.class);
    set.add(GcpSecretsManagerConfig.class);
    set.add(KmsConfig.class);
    set.add(LocalEncryptionConfig.class);
    set.add(SSHVaultConfig.class);
    set.add(VaultConfig.class);
    set.add(SecretManagerConfig.class);
    //    set.add(CustomSecretsManagerConfig.class); // pull in 930-delegate-tasks
  }

  @Override
  public void registerImplementationClasses(final MorphiaRegistrarHelperPut h, final MorphiaRegistrarHelperPut w) {
    w.put("beans.AwsSecretsManagerConfig", AwsSecretsManagerConfig.class);
    w.put("beans.AzureVaultConfig", AzureVaultConfig.class);
    w.put("beans.BaseVaultConfig", BaseVaultConfig.class);
    //    w.put("beans.CustomSecretNGManagerConfig", CustomSecretNGManagerConfig.class); // Wasn't registered before
    w.put("beans.GcpKmsConfig", GcpKmsConfig.class);
    w.put("beans.GcpSecretsManagerConfig", GcpSecretsManagerConfig.class);
    w.put("beans.KmsConfig", KmsConfig.class);
    w.put("beans.LocalEncryptionConfig", LocalEncryptionConfig.class);
    w.put("beans.SSHVaultConfig", SSHVaultConfig.class);
    w.put("beans.VaultConfig", VaultConfig.class);
    w.put("beans.SecretManagerConfig", SecretManagerConfig.class);
    //    w.put("security.encryption.secretsmanagerconfigs.CustomSecretsManagerConfig",
    //    CustomSecretsManagerConfig.class);
  }
}
