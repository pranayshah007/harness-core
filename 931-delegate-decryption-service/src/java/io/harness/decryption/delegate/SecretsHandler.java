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
import io.harness.security.encryption.EncryptionConfig;
import io.harness.serializer.KryoSerializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor(onConstructor_ = @Inject)
@Getter
@Slf4j
public class SecretsHandler {
  private static final String SECRET_INPUT_LOCATION = "/opt/harness/secrets/input/secrets.bin";
  private static final String SECRET_OUTPUT_LOCATION = "/opt/harness/secrets/out/secrets.json";

  private static final ObjectMapper mapper = new ObjectMapper();

  @Named("referenceFalseKryoSerializer") private final KryoSerializer kryoSerializer;
  private final DelegateDecryptionService decryptionService;

  public static void main(String[] args) {
    final var injector = Guice.createInjector(new DelegateDecryptionModule());
    final var secretsHandler = injector.getInstance(SecretsHandler.class);

    try {
      final var secretBytes = Files.readAllBytes(Path.of(SECRET_INPUT_LOCATION));
      final var secrets =
          (Map<EncryptionConfig, List<EncryptedRecord>>) secretsHandler.getKryoSerializer().asObject(secretBytes);

      log.info("Decrypting secrets from {} providers", secrets.size());

      final var decryptedSecrets = secretsHandler.getDecryptionService().decrypt(secrets);

      log.debug("Decrypted secrets with IDs {}", decryptedSecrets.keySet());
      final var json = mapper.writeValueAsBytes(decryptedSecrets);
      Files.write(Path.of(SECRET_OUTPUT_LOCATION), json);
      log.info("Secrets file written to {}", SECRET_OUTPUT_LOCATION);
    } catch (IOException e) {
      log.error("Failed to read/write the secrets file", e);
    } catch (Exception e) {
      log.error("General exception decrypting secrets", e);
    }
  }
}
