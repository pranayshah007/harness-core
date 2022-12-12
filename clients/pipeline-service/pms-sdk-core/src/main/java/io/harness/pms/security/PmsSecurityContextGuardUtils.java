/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.security;

import static io.harness.authorization.AuthorizationServiceHeader.PIPELINE_SERVICE;

import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.eraro.ErrorCode;
import io.harness.exception.AccessDeniedException;
import io.harness.exception.WingsException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExecutionPrincipalInfo;
import io.harness.pms.contracts.plan.TriggeredBy;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.rbac.PrincipalTypeProtoToPrincipalTypeMapper;
import io.harness.security.dto.ApiKeyPrincipal;
import io.harness.security.dto.Principal;
import io.harness.security.dto.ServiceAccountPrincipal;
import io.harness.security.dto.ServicePrincipal;
import io.harness.security.dto.UserPrincipal;

import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;

@OwnedBy(HarnessTeam.PIPELINE)
@UtilityClass
public class PmsSecurityContextGuardUtils {
  public Principal getPrincipalFromAmbiance(Ambiance ambiance) {
    ExecutionPrincipalInfo executionPrincipalInfo = ambiance.getMetadata().getPrincipalInfo();
    TriggeredBy triggeredBy = ambiance.getMetadata().getTriggerInfo().getTriggeredBy();

    return getPrincipal(AmbianceUtils.getAccountId(ambiance), executionPrincipalInfo, triggeredBy);
  }

  @NotNull
  public Principal getPrincipal(
      String accountId, ExecutionPrincipalInfo executionPrincipalInfo, TriggeredBy triggeredBy) {
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
        return new UserPrincipal(
            principal, triggeredBy.getExtraInfoMap().get("email"), triggeredBy.getIdentifier(), accountId);
      case SERVICE:
        return new ServicePrincipal(principal);
      case API_KEY:
        return new ApiKeyPrincipal(principal);
      case SERVICE_ACCOUNT:
        return new ServiceAccountPrincipal(
            principal, triggeredBy.getExtraInfoMap().get("email"), triggeredBy.getIdentifier(), accountId);
      default:
        throw new AccessDeniedException("Unknown Principal Type", WingsException.USER);
    }
  }
}
