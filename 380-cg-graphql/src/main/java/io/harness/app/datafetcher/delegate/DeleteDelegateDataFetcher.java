/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.app.datafetcher.delegate;

import static io.harness.annotations.dev.HarnessTeam.DEL;
import static io.harness.logging.logcontext.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_DELEGATES;

import io.harness.annotations.dev.OwnedBy;
import io.harness.app.schema.mutation.delegate.input.QLDeleteDelegateInput;
import io.harness.app.schema.mutation.delegate.payload.QLDeleteDelegatePayload;
import io.harness.delegate.task.DelegateLogContext;
import io.harness.logging.logcontext.AccountLogContext;
import io.harness.logging.logcontext.AutoLogContext;

import software.wings.graphql.datafetcher.BaseMutatorDataFetcher;
import software.wings.graphql.datafetcher.MutationContext;
import software.wings.security.annotations.AuthRule;
import software.wings.service.intfc.DelegateService;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(DEL)
public class DeleteDelegateDataFetcher extends BaseMutatorDataFetcher<QLDeleteDelegateInput, QLDeleteDelegatePayload> {
  @Inject private DelegateService delegateService;

  @Inject
  public DeleteDelegateDataFetcher(DelegateService delegateService) {
    super(QLDeleteDelegateInput.class, QLDeleteDelegatePayload.class);
    this.delegateService = delegateService;
  }

  @Override
  @AuthRule(permissionType = MANAGE_DELEGATES)
  public QLDeleteDelegatePayload mutateAndFetch(QLDeleteDelegateInput parameter, MutationContext mutationContext) {
    String accountId = parameter.getAccountId();
    String delegateId = parameter.getDelegateId();
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new DelegateLogContext(delegateId, OVERRIDE_ERROR)) {
      delegateService.delete(accountId, delegateId);
      return new QLDeleteDelegatePayload(mutationContext.getAccountId(), "Delegate deleted");
    }
  }
}
