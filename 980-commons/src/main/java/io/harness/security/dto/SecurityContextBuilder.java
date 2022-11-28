/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.security;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.security.PrincipalContextData.PRINCIPAL_CONTEXT;

import io.harness.annotations.dev.OwnedBy;
import io.harness.context.GlobalContext;
import io.harness.govern.Switch;
import io.harness.manage.GlobalContextManager;
import io.harness.security.dto.ApiKeyPrincipal;
import io.harness.security.dto.Principal;
import io.harness.security.dto.PrincipalType;
import io.harness.security.dto.ServiceAccountPrincipal;
import io.harness.security.dto.ServicePrincipal;
import io.harness.security.dto.UserPrincipal;

import com.auth0.jwt.interfaces.Claim;
import java.util.Map;
import lombok.experimental.UtilityClass;

@OwnedBy(PL)
@UtilityClass
public class SecurityContextBuilder {
  public static final String PRINCIPAL_TYPE = "type";
  public static final String PRINCIPAL_NAME = "name";
  public static final String ACCOUNT_ID = "accountId";
  public static final String EMAIL = "email";
  public static final String USERNAME = "username";

  public Principal getPrincipalFromClaims(Map<String, Claim> claimMap) {
    Principal principal = null;
    if (claimMap.get(PRINCIPAL_TYPE) != null) {
      PrincipalType type = claimMap.get(PRINCIPAL_TYPE).as(PrincipalType.class);
      switch (type) {
        case USER:
          principal = UserPrincipal.getPrincipal(claimMap);
          break;
        case API_KEY:
          principal = ApiKeyPrincipal.getPrincipal(claimMap);
          break;
        case SERVICE:
          principal = ServicePrincipal.getPrincipal(claimMap);
          break;
        case SERVICE_ACCOUNT:
          principal = ServiceAccountPrincipal.getPrincipal(claimMap);
          break;
        default:
          Switch.unhandled(type);
      }
    }
    return principal;
  }

  public void setContext(Map<String, Claim> claimMap) {
    Principal principal = getPrincipalFromClaims(claimMap);
    setContext(principal);
  }

  public void setContext(Principal principal) {
    if (!GlobalContextManager.isAvailable()) {
      GlobalContextManager.set(new GlobalContext());
    }
    GlobalContextManager.upsertGlobalContextRecord(PrincipalContextData.builder().principal(principal).build());
  }

  public Principal getPrincipal() {
    PrincipalContextData principalContextData = GlobalContextManager.get(PRINCIPAL_CONTEXT);
    if (principalContextData == null) {
      return null;
    }
    return principalContextData.getPrincipal();
  }

  public void unsetCompleteContext() {
    GlobalContextManager.unset();
  }
}
