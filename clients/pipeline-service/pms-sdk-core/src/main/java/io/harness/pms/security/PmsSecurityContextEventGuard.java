/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.security;

import static io.harness.AuthorizationServiceHeader.PIPELINE_SERVICE;

import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.eraro.ErrorCode;
import io.harness.exception.AccessDeniedException;
import io.harness.exception.WingsException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExecutionPrincipalInfo;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.rbac.PrincipalTypeProtoToPrincipalTypeMapper;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.ApiKeyPrincipal;
import io.harness.security.dto.ServiceAccountPrincipal;
import io.harness.security.dto.ServicePrincipal;
import io.harness.security.dto.UserPrincipal;

@OwnedBy(HarnessTeam.PIPELINE)
public class PmsSecurityContextEventGuard implements AutoCloseable {
  public PmsSecurityContextEventGuard(Ambiance ambiance) {
    if (ambiance != null) {
      io.harness.security.dto.Principal principal = getPrincipalFromAmbiance(ambiance);
      if (principal != null) {
        SecurityContextBuilder.setContext(principal);
        SourcePrincipalContextBuilder.setSourcePrincipal(principal);
      }
    }
  }

  private io.harness.security.dto.Principal getPrincipalFromAmbiance(Ambiance ambiance) {
    ExecutionPrincipalInfo executionPrincipalInfo = ambiance.getMetadata().getPrincipalInfo();
    ExecutionTriggerInfo executionTriggerInfo = ambiance.getMetadata().getTriggerInfo();

    // NOTE: rbac should not be validated for triggers so all the resources should be validated with service principals
    if (!executionPrincipalInfo.getShouldValidateRbac()) {
      return new ServicePrincipal(PIPELINE_SERVICE.getServiceId());
    }

    String principal = executionPrincipalInfo.getPrincipal();
    if (EmptyPredicate.isEmpty(principal)) {
      throw new AccessDeniedException("Execution with empty principal found. Please contact harness customer care.",
          ErrorCode.NG_ACCESS_DENIED, WingsException.USER);
    }
    PrincipalType principalType = PrincipalTypeProtoToPrincipalTypeMapper.convertToAccessControlPrincipalType(
        executionPrincipalInfo.getPrincipalType());
    switch (principalType) {
      case USER:
        return new UserPrincipal(principal, executionTriggerInfo.getTriggeredBy().getExtraInfoMap().get("email"),
            executionTriggerInfo.getTriggeredBy().getIdentifier(), AmbianceUtils.getAccountId(ambiance));
      case SERVICE:
        return new ServicePrincipal(principal);
      case API_KEY:
        return new ApiKeyPrincipal(principal);
      case SERVICE_ACCOUNT:
        return new ServiceAccountPrincipal(principal,
            executionTriggerInfo.getTriggeredBy().getExtraInfoMap().get("email"),
            executionTriggerInfo.getTriggeredBy().getIdentifier());
      default:
        throw new AccessDeniedException("Unknown Principal Type", WingsException.USER);
    }
  }

  @Override
  public void close() throws Exception {
    SecurityContextBuilder.unsetCompleteContext();
  }
}
