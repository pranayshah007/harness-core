/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.events;

import static io.harness.delegate.utils.DelegateOutboxEventConstants.DELEGATE_TOKEN_CREATE_EVENT;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.ResourceTypeConstants;
import io.harness.beans.Scope;
import io.harness.delegate.dto.DelegateNgTokenDTO;
import io.harness.ng.core.Resource;
import io.harness.ng.core.ResourceScope;
import io.harness.ng.core.mapper.ResourceScopeMapper;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@OwnedBy(HarnessTeam.DEL)
@Getter
@SuperBuilder
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DelegateNgTokenCreateEvent extends AbstractDelegateConfigurationEvent {
  private DelegateNgTokenDTO token;

  @Override
  public Resource getResource() {
    return Resource.builder().identifier(token.getName()).type(ResourceTypeConstants.DELEGATE_TOKEN).build();
  }

  @Override
  public String getEventType() {
    return DELEGATE_TOKEN_CREATE_EVENT;
  }

  @Override
  @JsonIgnore
  public ResourceScope getResourceScope() {
    return ResourceScopeMapper.getResourceScope(
        Scope.of(token.getAccountIdentifier(), token.getOrgIdentifier(), token.getProjectIdentifier()));
  }
}
