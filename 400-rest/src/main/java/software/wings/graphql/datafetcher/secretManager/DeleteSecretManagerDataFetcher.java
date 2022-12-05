/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.secretManager;

import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_SECRET_MANAGERS;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.SecretManagerConfig;
import io.harness.exception.InvalidRequestException;
import io.harness.secretmanagers.SecretManagerConfigService;
import io.harness.security.encryption.common.EncryptionType;

import software.wings.graphql.datafetcher.BaseMutatorDataFetcher;
import software.wings.graphql.datafetcher.MutationContext;
import software.wings.graphql.schema.mutation.secretManager.QLDeleteSecretManagerInput;
import software.wings.graphql.schema.mutation.secretManager.QLDeleteSecretManagerPayload;
import software.wings.security.annotations.AuthRule;

import com.google.inject.Inject;

@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class DeleteSecretManagerDataFetcher
    extends BaseMutatorDataFetcher<QLDeleteSecretManagerInput, QLDeleteSecretManagerPayload> {
  @Inject
  public DeleteSecretManagerDataFetcher() {
    super(QLDeleteSecretManagerInput.class, QLDeleteSecretManagerPayload.class);
  }

  @Inject private SecretManagerConfigService secretManagerConfigService;
  @Inject private SecretManagerDataFetcherRegistry secretManagerDataFetcherRegistry;

  @Override
  @AuthRule(permissionType = MANAGE_SECRET_MANAGERS)
  protected QLDeleteSecretManagerPayload mutateAndFetch(
      QLDeleteSecretManagerInput input, MutationContext mutationContext) {
    String secretManagerId = input.getSecretManagerId();
    String accountId = mutationContext.getAccountId();

    SecretManagerConfig secretManagerConfig = secretManagerConfigService.getSecretManager(accountId, secretManagerId);

    if (secretManagerConfig == null) {
      throw new InvalidRequestException("Secret manager with given id doesn't exist");
    }

    EncryptionType encryptionType = secretManagerConfig.getEncryptionType();

    SecretManagerMutationDataFetcher dataFetcher = secretManagerDataFetcherRegistry.getDataFetcher(encryptionType);

    dataFetcher.deleteSecretManager(accountId, secretManagerId);

    return QLDeleteSecretManagerPayload.builder().clientMutationId(input.getClientMutationId()).build();
  }
}
