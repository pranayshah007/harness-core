/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.encryptors;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.encryptors.Encryptors.AWS_KMS_ENCRYPTOR;
import static io.harness.encryptors.Encryptors.GCP_KMS_ENCRYPTOR;
import static io.harness.encryptors.Encryptors.GLOBAL_AWS_KMS_ENCRYPTOR;
import static io.harness.encryptors.Encryptors.GLOBAL_GCP_KMS_ENCRYPTOR;
import static io.harness.encryptors.Encryptors.LOCAL_ENCRYPTOR;
import static io.harness.eraro.ErrorCode.SECRET_MANAGEMENT_ERROR;
import static io.harness.exception.WingsException.USER;
import static io.harness.security.encryption.EncryptionType.GCP_KMS;
import static io.harness.security.encryption.EncryptionType.KMS;
import static io.harness.security.encryption.EncryptionType.LOCAL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.SecretManagementDelegateException;
import io.harness.security.encryption.EncryptionConfig;
import io.harness.security.encryption.EncryptionType;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Singleton;
import com.google.inject.name.Names;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

@Singleton
@OwnedBy(PL)
public class KmsEncryptorsRegistry {
  private final Injector injector;
  private final Map<EncryptionType, Encryptors> registeredEncryptors;
  private final Map<EncryptionType, Encryptors> globalEncryptors;

  @Inject
  public KmsEncryptorsRegistry(Injector injector) {
    this.injector = injector;
    registeredEncryptors = new EnumMap<>(EncryptionType.class);
    registeredEncryptors.put(LOCAL, LOCAL_ENCRYPTOR);
    registeredEncryptors.put(KMS, AWS_KMS_ENCRYPTOR);
    registeredEncryptors.put(GCP_KMS, GCP_KMS_ENCRYPTOR);

    globalEncryptors = new EnumMap<>(EncryptionType.class);
    globalEncryptors.put(KMS, GLOBAL_AWS_KMS_ENCRYPTOR);
    globalEncryptors.put(GCP_KMS, GLOBAL_GCP_KMS_ENCRYPTOR);
    globalEncryptors.put(LOCAL, LOCAL_ENCRYPTOR);
  }

  public KmsEncryptor getKmsEncryptor(EncryptionConfig encryptionConfig) {
    Encryptors encryptor = encryptionConfig.isGlobalKms()
        ? globalEncryptors.get(encryptionConfig.getEncryptionType())
        : registeredEncryptors.get(encryptionConfig.getEncryptionType());
    return Optional.ofNullable(encryptor)
        .flatMap(type -> Optional.of(injector.getInstance(Key.get(KmsEncryptor.class, Names.named(type.getName())))))
        .<SecretManagementDelegateException>orElseThrow(() -> {
          throw new SecretManagementDelegateException(SECRET_MANAGEMENT_ERROR,
              String.format("No encryptor is registered for encryptor annotation %s", encryptor), USER);
        });
  }
}
