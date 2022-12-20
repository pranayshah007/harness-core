/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.decryption.delegate;

import com.google.inject.Guice;
import com.google.inject.Injector;
import io.harness.decryption.delegate.module.DelegateDecryptionModule;
import io.harness.security.encryption.DelegateDecryptionService;

import com.google.inject.Inject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;

@RequiredArgsConstructor(onConstructor_ = @Inject)
@Slf4j
public class SecretsHandler {
  public static void main(String[] args) {
    final Injector injector = Guice.createInjector(new DelegateDecryptionModule());

    final var decrypt = injector.getInstance(DelegateDecryptionService.class).decrypt(Collections.emptyMap());
    log.info("Decrypted {}", decrypt);
  }
}
