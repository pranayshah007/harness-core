/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.scopes.core;

import static io.harness.accesscontrol.scopes.harness.HarnessScopeParams.ACCOUNT_LEVEL_PARAM_NAME;
import static io.harness.accesscontrol.scopes.harness.HarnessScopeParams.ORG_LEVEL_PARAM_NAME;
import static io.harness.accesscontrol.scopes.harness.HarnessScopeParams.PROJECT_LEVEL_PARAM_NAME;
import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.accesscontrol.scopes.HarnessScopeLevel;
import io.harness.accesscontrol.scopes.ScopeDTO;
import io.harness.annotations.dev.OwnedBy;

import java.util.HashMap;
import java.util.Map;
import javax.validation.executable.ValidateOnExecution;
import lombok.experimental.UtilityClass;

@OwnedBy(PL)
@UtilityClass
@ValidateOnExecution
public class ScopeDTOMapper {
  public static ScopeDTO toDTO(Scope scope) {
    Map<String, String> params = new HashMap<>();
    Scope currentScope = scope;
    while (currentScope != null) {
      ScopeLevel scopeLevel = currentScope.getLevel();
      if (scopeLevel instanceof HarnessScopeLevel) {
        HarnessScopeLevel harnessScopeLevel = (HarnessScopeLevel) scopeLevel;
        params.put(harnessScopeLevel.getParamName(), currentScope.getInstanceId());
      }
      currentScope = currentScope.getParentScope();
    }
    return ScopeDTO.builder()
        .accountIdentifier(params.get(ACCOUNT_LEVEL_PARAM_NAME))
        .orgIdentifier(params.get(ORG_LEVEL_PARAM_NAME))
        .projectIdentifier(params.get(PROJECT_LEVEL_PARAM_NAME))
        .build();
  }
}
