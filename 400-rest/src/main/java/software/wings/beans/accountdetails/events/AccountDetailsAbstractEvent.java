/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.accountdetails.events;

import static io.harness.annotations.dev.HarnessTeam.GTM;
import static io.harness.audit.ResourceTypeConstants.NG_ACCOUNT_DETAILS;

import static software.wings.beans.accountdetails.AccountDetailsConstants.RESOURCE_NAME;

import io.harness.annotations.dev.OwnedBy;
import io.harness.event.Event;
import io.harness.ng.core.AccountScope;
import io.harness.ng.core.Resource;
import io.harness.ng.core.ResourceConstants;
import io.harness.ng.core.ResourceScope;

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@OwnedBy(GTM)
@SuperBuilder
@Getter
@NoArgsConstructor
public abstract class AccountDetailsAbstractEvent implements Event {
  private String accountIdentifier;

  @Override
  public ResourceScope getResourceScope() {
    return new AccountScope(accountIdentifier);
  }

  @Override
  public Resource getResource() {
    Map<String, String> labels = new HashMap<>();
    labels.put(ResourceConstants.LABEL_KEY_RESOURCE_NAME, RESOURCE_NAME);
    return Resource.builder().identifier(accountIdentifier).labels(labels).type(NG_ACCOUNT_DETAILS).build();
  }
}
