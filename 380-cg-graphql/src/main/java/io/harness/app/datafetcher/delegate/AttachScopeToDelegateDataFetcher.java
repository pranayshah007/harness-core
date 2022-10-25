/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.app.datafetcher.delegate;

import static io.harness.annotations.dev.HarnessTeam.DEL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.logging.logcontext.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_DELEGATES;

import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.app.schema.mutation.delegate.input.QLAttachScopeToDelegateInput;
import io.harness.app.schema.mutation.delegate.payload.QLAttachScopeToDelegatePayload;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.DelegateScope;
import io.harness.delegate.task.DelegateLogContext;
import io.harness.logging.logcontext.AccountLogContext;
import io.harness.logging.logcontext.AutoLogContext;
import io.harness.service.intfc.DelegateCache;

import software.wings.graphql.datafetcher.BaseMutatorDataFetcher;
import software.wings.graphql.datafetcher.MutationContext;
import software.wings.security.annotations.AuthRule;
import software.wings.service.intfc.DelegateScopeService;
import software.wings.service.intfc.DelegateService;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(DEL)
public class AttachScopeToDelegateDataFetcher
    extends BaseMutatorDataFetcher<QLAttachScopeToDelegateInput, QLAttachScopeToDelegatePayload> {
  @Inject DelegateScopeService delegateScopeService;
  @Inject DelegateService delegateService;
  @Inject DelegateCache delegateCache;

  @Inject
  public AttachScopeToDelegateDataFetcher(
      DelegateScopeService delegateScopeService, DelegateService delegateService, DelegateCache delegateCache) {
    super(QLAttachScopeToDelegateInput.class, QLAttachScopeToDelegatePayload.class);
    this.delegateScopeService = delegateScopeService;
    this.delegateService = delegateService;
    this.delegateCache = delegateCache;
  }

  @Override
  @AuthRule(permissionType = MANAGE_DELEGATES)
  public QLAttachScopeToDelegatePayload mutateAndFetch(
      QLAttachScopeToDelegateInput parameter, MutationContext mutationContext) {
    String delegateId = parameter.getDelegateId();
    String accountId = parameter.getAccountId();
    List<String> includeScopes = new ArrayList<>();
    if (parameter.getIncludeScopes() != null) {
      includeScopes = Arrays.asList(parameter.getIncludeScopes().getValues());
    }
    List<String> excludeScopes = new ArrayList<>();
    if (parameter.getExcludeScopes() != null) {
      excludeScopes = Arrays.asList(parameter.getExcludeScopes().getValues());
    }

    if (includeScopes.isEmpty() && excludeScopes.isEmpty()) {
      return QLAttachScopeToDelegatePayload.builder().message("No scopes to attach to delegate").build();
    }
    DelegateScopes delegateScopes =
        DelegateScopes.builder().includeScopeIds(includeScopes).excludeScopeIds(excludeScopes).build();

    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new DelegateLogContext(delegateId, OVERRIDE_ERROR)) {
      Delegate delegate = delegateCache.get(accountId, delegateId, true);
      if (delegate == null) {
        return QLAttachScopeToDelegatePayload.builder()
            .message("Unable to fetch delegate with delegate id " + delegateId)
            .build();
      }
      List<DelegateScope> currentIncludedDelegateScopes = delegate.getIncludeScopes();
      if (!includeScopes.isEmpty()) {
        delegate.setIncludeScopes(delegateScopes.getIncludeScopeIds()
                                      .stream()
                                      .map(name -> delegateScopeService.getByName(accountId, name))
                                      .filter(Objects::nonNull)
                                      .collect(toList()));
        // add existing included delegate scopes to list coz on before update operation we unset all
        if (delegate.getIncludeScopes() != null && currentIncludedDelegateScopes != null) {
          delegate.setIncludeScopes(
              Stream.concat(currentIncludedDelegateScopes.stream(), delegate.getIncludeScopes().stream())
                  .distinct()
                  .collect(toList()));
        }
      }
      List<DelegateScope> currentExcludedDelegateScopes = delegate.getExcludeScopes();
      if (!excludeScopes.isEmpty()) {
        delegate.setExcludeScopes(delegateScopes.getExcludeScopeIds()
                                      .stream()
                                      .map(name -> delegateScopeService.getByName(accountId, name))
                                      .filter(Objects::nonNull)
                                      .collect(toList()));
        // add existing excluded delegate scopes to list  coz on before update operation we unset all
        if (delegate.getExcludeScopes() != null && currentExcludedDelegateScopes != null) {
          delegate.setExcludeScopes(
              Stream.concat(currentExcludedDelegateScopes.stream(), delegate.getExcludeScopes().stream())
                  .distinct()
                  .collect(toList()));
        }
      }
      delegateService.updateScopes(delegate);

      List<String> updatedIncludeScopes = new ArrayList<>();
      if (!isEmpty(delegate.getIncludeScopes())) {
        delegate.getIncludeScopes().forEach(delegateScope -> updatedIncludeScopes.add(delegateScope.getName()));
      }
      List<String> updatedExcludeScopes = new ArrayList<>();
      if (!isEmpty(delegate.getExcludeScopes())) {
        delegate.getExcludeScopes().forEach(delegateScope -> updatedExcludeScopes.add(delegateScope.getName()));
      }
      String responseString = "Included scopes for delegate:  " + updatedIncludeScopes
          + " Excluded scopes for delegate: " + updatedExcludeScopes;
      return QLAttachScopeToDelegatePayload.builder().message(responseString).build();
    }
  }

  @Value
  @Builder
  protected static class DelegateScopes {
    List<String> includeScopeIds;
    List<String> excludeScopeIds;
  }
}
