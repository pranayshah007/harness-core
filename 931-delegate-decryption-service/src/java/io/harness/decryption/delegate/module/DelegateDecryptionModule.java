/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.decryption.delegate.module;

import io.harness.decryption.delegate.module.kryo.SecretsKryoModule;
import io.harness.encryptors.CustomEncryptor;
import io.harness.encryptors.Encryptors;
import io.harness.encryptors.KmsEncryptor;
import io.harness.encryptors.VaultEncryptor;
import io.harness.encryptors.clients.AwsKmsEncryptor;
import io.harness.encryptors.clients.AwsSecretsManagerEncryptor;
import io.harness.encryptors.clients.AzureVaultEncryptor;
import io.harness.encryptors.clients.GcpKmsEncryptor;
import io.harness.encryptors.clients.GcpSecretsManagerEncryptor;
import io.harness.encryptors.clients.HashicorpVaultEncryptor;
import io.harness.encryptors.clients.LocalEncryptor;
import io.harness.encryptors.clients.NoopCustomEncryptor;
import io.harness.secrets.SecretsDelegateCacheHelperService;
import io.harness.secrets.SecretsDelegateCacheService;
import io.harness.secrets.SecretsDelegateCacheServiceImpl;
import io.harness.secrets.noop.NoopSecretsDelegateCacheHelperService;
import io.harness.security.encryption.DelegateDecryptionService;
import io.harness.threading.ThreadPool;

import software.wings.service.impl.security.DelegateDecryptionServiceImpl;
import software.wings.service.impl.security.EncryptionServiceImpl;
import software.wings.service.intfc.security.EncryptionService;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public class DelegateDecryptionModule extends AbstractModule {
  @Provides
  @Singleton
  @Named("asyncExecutor")
  public ExecutorService asyncExecutor() {
    return ThreadPool.create(10, 400, 1, TimeUnit.SECONDS,
        new ThreadFactoryBuilder().setNameFormat("async-%d").setPriority(Thread.MIN_PRIORITY).build());
  }

  @Override
  protected void configure() {
    install(new SecretsKryoModule());

    bind(DelegateDecryptionService.class).to(DelegateDecryptionServiceImpl.class);
    bind(EncryptionService.class).to(EncryptionServiceImpl.class);
    bind(SecretsDelegateCacheService.class).to(SecretsDelegateCacheServiceImpl.class);
    bind(SecretsDelegateCacheHelperService.class).to(NoopSecretsDelegateCacheHelperService.class);

    //    bind(TimeLimiter.class).toInstance(HTimeLimiter.create());

    bindSecretEncryptors();
  }

  private void bindSecretEncryptors() {
    binder()
        .bind(VaultEncryptor.class)
        .annotatedWith(Names.named(Encryptors.HASHICORP_VAULT_ENCRYPTOR.getName()))
        .to(HashicorpVaultEncryptor.class);

    binder()
        .bind(VaultEncryptor.class)
        .annotatedWith(Names.named(Encryptors.AWS_VAULT_ENCRYPTOR.getName()))
        .to(AwsSecretsManagerEncryptor.class);

    binder()
        .bind(VaultEncryptor.class)
        .annotatedWith(Names.named(Encryptors.AZURE_VAULT_ENCRYPTOR.getName()))
        .to(AzureVaultEncryptor.class);

    binder()
        .bind(VaultEncryptor.class)
        .annotatedWith(Names.named(Encryptors.GCP_VAULT_ENCRYPTOR.getName()))
        .to(GcpSecretsManagerEncryptor.class);

    binder()
        .bind(KmsEncryptor.class)
        .annotatedWith(Names.named(Encryptors.AWS_KMS_ENCRYPTOR.getName()))
        .to(AwsKmsEncryptor.class);

    binder()
        .bind(KmsEncryptor.class)
        .annotatedWith(Names.named(Encryptors.GCP_KMS_ENCRYPTOR.getName()))
        .to(GcpKmsEncryptor.class);

    binder()
        .bind(KmsEncryptor.class)
        .annotatedWith(Names.named(Encryptors.LOCAL_ENCRYPTOR.getName()))
        .to(LocalEncryptor.class);

    binder()
        .bind(KmsEncryptor.class)
        .annotatedWith(Names.named(Encryptors.GLOBAL_GCP_KMS_ENCRYPTOR.getName()))
        .to(GcpKmsEncryptor.class);

    binder()
        .bind(KmsEncryptor.class)
        .annotatedWith(Names.named(Encryptors.GLOBAL_AWS_KMS_ENCRYPTOR.getName()))
        .to(AwsKmsEncryptor.class);
    // Custom secret managers are not supported yet
    binder()
        .bind(CustomEncryptor.class)
        .annotatedWith(Names.named(Encryptors.CUSTOM_ENCRYPTOR.getName()))
        .to(NoopCustomEncryptor.class);

    binder()
        .bind(CustomEncryptor.class)
        .annotatedWith(Names.named(Encryptors.CUSTOM_ENCRYPTOR_NG.getName()))
        // Use ng encryptor
        .to(NoopCustomEncryptor.class);
  }
}
