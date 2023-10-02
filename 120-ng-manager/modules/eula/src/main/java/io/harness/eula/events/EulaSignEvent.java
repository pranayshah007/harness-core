/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.eula.events;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.audit.ResourceTypeConstants.EULA;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eula.AgreementType;
import io.harness.event.Event;
import io.harness.ng.core.AccountScope;
import io.harness.ng.core.Resource;
import io.harness.ng.core.ResourceConstants;
import io.harness.ng.core.ResourceScope;

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;

@OwnedBy(PL)
@Getter
@NoArgsConstructor
public class EulaSignEvent implements Event {
  public static final String EULA_SIGNED = "EulaSigned";
  private AgreementType agreementType;
  private String accountIdentifier;

  public EulaSignEvent(String accountIdentifier, AgreementType agreementType) {
    this.accountIdentifier = accountIdentifier;
    this.agreementType = agreementType;
  }

  @Override
  public ResourceScope getResourceScope() {
    return new AccountScope(accountIdentifier);
  }

  @Override
  public Resource getResource() {
    Map<String, String> labels = new HashMap<>();
    labels.put(ResourceConstants.LABEL_KEY_RESOURCE_NAME, agreementType.name());
    return Resource.builder().type(EULA).identifier(agreementType.name()).labels(labels).build();
  }

  @Override
  public String getEventType() {
    return EULA_SIGNED;
  }
}