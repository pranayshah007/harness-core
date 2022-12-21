/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.decryption.delegate;

import io.harness.decryption.delegate.module.DelegateDecryptionModule;
import io.harness.security.encryption.DelegateDecryptionService;
import io.harness.security.encryption.EncryptedRecord;
import io.harness.security.encryption.EncryptedRecordData;
import io.harness.security.encryption.EncryptionConfig;

import software.wings.beans.LocalEncryptionConfig;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor(onConstructor_ = @Inject)
@Slf4j
public class SecretsHandler {
  public static void main(String[] args) {
    final Injector injector = Guice.createInjector(new DelegateDecryptionModule());

    final LocalEncryptionConfig config =
        LocalEncryptionConfig.builder().name("name").accountId("__GLOBAL_ACCOUNT_ID__").build();
    final var data = EncryptedRecordData.builder()
                         .name("testLdapRecord")
                         .encryptedValue("encryptedTestPassword".toCharArray())
                         .kmsId("ACCOUNT_ID")
                         .build();
    final var secrets =
        Collections.<EncryptionConfig, List<EncryptedRecord>>singletonMap(config, Collections.singletonList(data));

    final var decrypt = injector.getInstance(DelegateDecryptionService.class).decrypt(secrets);
    log.info("Decrypted {}", decrypt);
  }
}
