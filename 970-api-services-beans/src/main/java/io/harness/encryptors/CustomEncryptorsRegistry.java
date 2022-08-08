/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.encryptors;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.encryptors.Encryptors.CUSTOM_ENCRYPTOR;
import static io.harness.eraro.ErrorCode.SECRET_MANAGEMENT_ERROR;
import static io.harness.exception.WingsException.USER;
import static io.harness.security.encryption.EncryptionType.CUSTOM;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.SecretManagementDelegateException;
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
public class CustomEncryptorsRegistry {
  private final Injector injector;
  private final Map<EncryptionType, Encryptors> registeredEncryptors;

  @Inject
  public CustomEncryptorsRegistry(Injector injector) {
    this.injector = injector;
    registeredEncryptors = new EnumMap<>(EncryptionType.class);
    registeredEncryptors.put(CUSTOM, CUSTOM_ENCRYPTOR);
  }

  public CustomEncryptor getCustomEncryptor(EncryptionType encryptionType) {
    return Optional.ofNullable(registeredEncryptors.get(encryptionType))
        .flatMap(type -> Optional.of(injector.getInstance(Key.get(CustomEncryptor.class, Names.named(type.getName())))))
        .<SecretManagementDelegateException>orElseThrow(() -> {
          throw new SecretManagementDelegateException(SECRET_MANAGEMENT_ERROR,
              String.format("No encryptor is registered for encryption type %s", encryptionType), USER);
        });
  }
}
