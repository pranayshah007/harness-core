/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.scopes.core;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import org.hibernate.validator.constraints.NotEmpty;

@OwnedBy(HarnessTeam.PL)
@Data
@Builder
public class Scope {
  public static final String PATH_DELIMITER = "/";
  public static final String SCOPE_DELIMITER = "$";

  @NotNull ScopeLevel level;
  @NotEmpty String instanceId;
  String instanceName;
  Scope parentScope;

  @Override
  public String toString() {
    String identifier = PATH_DELIMITER + level.getResourceType() + PATH_DELIMITER + instanceId;
    if (parentScope != null) {
      return parentScope.toString().concat(identifier);
    }
    return identifier;
  }
}
