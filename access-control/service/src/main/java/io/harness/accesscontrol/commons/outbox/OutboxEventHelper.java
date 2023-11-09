/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.commons.outbox;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.accesscontrol.scopes.AccessControlResourceScope;
import io.harness.accesscontrol.scopes.core.Scope;
import io.harness.accesscontrol.scopes.core.ScopeService;
import io.harness.accesscontrol.scopes.harness.ScopeMapper;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.beans.ResourceScopeDTO;
import io.harness.outbox.OutboxEvent;

import com.google.inject.Inject;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(PL)
@Slf4j
public class OutboxEventHelper {
  private final ScopeService scopeService;

  @Inject
  public OutboxEventHelper(ScopeService scopeService) {
    this.scopeService = scopeService;
  }

  public Pair<Optional<ResourceScopeDTO>, Optional<Scope>> getScopes(OutboxEvent outboxEvent) {
    Optional<ResourceScopeDTO> resourceScopeDTO;
    Optional<Scope> scope;
    if (isEventV2(outboxEvent)) {
      scope = Objects.nonNull(outboxEvent.getResourceScope()) && !isEmpty(outboxEvent.getResourceScope().getScope())
          ? Optional.of(scopeService.buildScopeFromScopeIdentifier(outboxEvent.getResourceScope().getScope()))
          : Optional.empty();
      resourceScopeDTO = scope.map(ScopeMapper::toResourceScopeDTO);
    } else {
      resourceScopeDTO = Optional.of(ResourceScopeDTO.fromResourceScope(outboxEvent.getResourceScope()));
      scope = resourceScopeDTO.map(ScopeMapper::toScope);
    }
    return Pair.of(resourceScopeDTO, scope);
  }

  public boolean isEventV2(OutboxEvent outboxEvent) {
    return outboxEvent.getResourceScope() instanceof AccessControlResourceScope;
  }
}
