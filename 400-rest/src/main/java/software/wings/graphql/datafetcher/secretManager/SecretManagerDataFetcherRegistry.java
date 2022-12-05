/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.secretManager;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.security.encryption.common.EncryptionType.CUSTOM;
import static io.harness.security.encryption.common.EncryptionType.VAULT;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.InvalidRequestException;
import io.harness.security.encryption.common.EncryptionType;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Singleton;
import com.google.inject.name.Names;
import java.util.EnumMap;
import java.util.Optional;

@OwnedBy(PL)
@Singleton
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class SecretManagerDataFetcherRegistry {
  private final Injector injector;
  private final EnumMap<EncryptionType, SecretManagerDataFetchers> registeredDataFetchers;

  @Inject
  public SecretManagerDataFetcherRegistry(Injector injector) {
    this.injector = injector;
    this.registeredDataFetchers = new EnumMap<>(EncryptionType.class);
    this.registeredDataFetchers.put(VAULT, SecretManagerDataFetchers.HASHICORP_VAULT_DATA_FETCHER);
    this.registeredDataFetchers.put(CUSTOM, SecretManagerDataFetchers.CUSTOM_SECRET_MANAGER_DATA_FETCHER);
  }

  public SecretManagerMutationDataFetcher getDataFetcher(EncryptionType encryptionType) {
    return Optional.ofNullable(this.registeredDataFetchers.get(encryptionType))
        .flatMap(type
            -> Optional.of(this.injector.getInstance(
                Key.get(SecretManagerMutationDataFetcher.class, Names.named(type.getName())))))
        .orElseThrow(() -> new InvalidRequestException("Secret manager type is not supported"));
  }
}
