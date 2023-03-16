/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.app.datafetcher.delegate;

import static io.harness.annotations.dev.HarnessTeam.DEL;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_DELEGATES;

import io.harness.annotations.dev.OwnedBy;
import io.harness.app.schema.mutation.delegate.input.QLDelegateApproveRejectInput;
import io.harness.app.schema.mutation.delegate.payload.QLDelegateApproveRejectPayload;
import io.harness.app.schema.type.delegate.QLDelegate;
import io.harness.app.schema.type.delegate.QLDelegate.QLDelegateBuilder;
import io.harness.app.schema.type.delegate.QLDelegateApproval;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.DelegateApproval;
import io.harness.delegate.task.DelegateLogContext;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;

import software.wings.graphql.datafetcher.BaseMutatorDataFetcher;
import software.wings.graphql.datafetcher.MutationContext;
import software.wings.security.annotations.AuthRule;
import software.wings.service.intfc.DelegateService;

import com.google.inject.Inject;
import io.jsonwebtoken.lang.Assert;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(DEL)
public class DelegateApprovalDataFetcher
    extends BaseMutatorDataFetcher<QLDelegateApproveRejectInput, QLDelegateApproveRejectPayload> {
  @Inject DelegateService delegateService;

  @Inject
  public DelegateApprovalDataFetcher(DelegateService delegateService) {
    super(QLDelegateApproveRejectInput.class, QLDelegateApproveRejectPayload.class);
    this.delegateService = delegateService;
  }

  @Override
  @AuthRule(permissionType = MANAGE_DELEGATES)
  public QLDelegateApproveRejectPayload mutateAndFetch(
      QLDelegateApproveRejectInput parameter, MutationContext mutationContext) {
    String delegateId = parameter.getDelegateId();
    String accountId = parameter.getAccountId();
    DelegateApproval delegateApproval = QLDelegateApproval.toDelegateApproval(parameter.getDelegateApproval());
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new DelegateLogContext(delegateId, OVERRIDE_ERROR)) {
      Delegate delegate = delegateService.updateApprovalStatus(accountId, delegateId, delegateApproval);
      Assert.notNull(delegate, "Unable to perform the operation");
      QLDelegateBuilder qlDelegateBuilder = QLDelegate.builder();
      DelegateController.populateQLDelegate(delegate, qlDelegateBuilder);
      return new QLDelegateApproveRejectPayload(mutationContext.getAccountId(), qlDelegateBuilder.build());
    }
  }
}
