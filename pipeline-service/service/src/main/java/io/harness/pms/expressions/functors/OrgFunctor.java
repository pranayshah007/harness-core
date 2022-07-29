/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expressions.functors;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.EngineFunctorException;
import io.harness.expression.LateBindingValue;
import io.harness.network.SafeHttpCall;
import io.harness.ng.core.dto.OrganizationResponse;
import io.harness.organization.remote.OrganizationClient;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;

import java.util.Optional;

@OwnedBy(PIPELINE)
public class OrgFunctor implements LateBindingValue {
  private final OrganizationClient organizationClient;
  private final Ambiance ambiance;

  public OrgFunctor(OrganizationClient organizationClient, Ambiance ambiance) {
    this.organizationClient = organizationClient;
    this.ambiance = ambiance;
  }

  @Override
  public Object bind() {
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String orgIdentifier = AmbianceUtils.getOrgIdentifier(ambiance);
    if (EmptyPredicate.isEmpty(accountId) || EmptyPredicate.isEmpty(orgIdentifier)) {
      return null;
    }

    try {
      Optional<OrganizationResponse> resp =
          SafeHttpCall.execute(organizationClient.getOrganization(orgIdentifier, accountId)).getData();
      return resp.map(OrganizationResponse::getOrganization).orElse(null);
    } catch (Exception ex) {
      throw new EngineFunctorException(String.format("Invalid organization: %s", orgIdentifier), ex);
    }
  }
}
