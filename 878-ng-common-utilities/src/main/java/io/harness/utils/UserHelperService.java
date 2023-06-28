/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.utils;

import static io.harness.remote.client.CGRestUtils.getResponse;
import static io.harness.security.PrincipalContextData.PRINCIPAL_CONTEXT;

import io.harness.client.NgConnectorManagerClient;
import io.harness.context.GlobalContext;
import io.harness.exception.InvalidRequestException;
import io.harness.manage.GlobalContextManager;
import io.harness.security.PrincipalContextData;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.PrincipalType;
import io.harness.security.dto.UserPrincipal;

import com.google.inject.Inject;

public class UserHelperService {
  @Inject NgConnectorManagerClient ngConnectorManagerClient;

  public boolean isHarnessSupportUser(String userId) {
    return getResponse(ngConnectorManagerClient.isHarnessSupportUser(userId));
  }

  public UserPrincipal getUserPrincipalOrThrow() {
    GlobalContext globalContext = GlobalContextManager.obtainGlobalContext();
    if (globalContext == null || !(globalContext.get(PRINCIPAL_CONTEXT) instanceof PrincipalContextData)
        || !(((PrincipalContextData) globalContext.get(PRINCIPAL_CONTEXT)).getPrincipal() instanceof UserPrincipal)) {
      throw new InvalidRequestException("Not authorized to update in current context");
    }
    return (UserPrincipal) ((PrincipalContextData) globalContext.get(PRINCIPAL_CONTEXT)).getPrincipal();
  }

  public String getUserId() {
    if (SourcePrincipalContextBuilder.getSourcePrincipal() != null
        && SourcePrincipalContextBuilder.getSourcePrincipal().getType() == PrincipalType.USER) {
      UserPrincipal userPrincipal = (UserPrincipal) SourcePrincipalContextBuilder.getSourcePrincipal();
      return userPrincipal.getName();
    }
    return null;
  }
}
