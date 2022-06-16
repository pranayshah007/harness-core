/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.acl.conditions;

import io.harness.accesscontrol.acl.PermissionCheck;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.google.inject.Singleton;
import java.util.Map;

@OwnedBy(HarnessTeam.PL)
@Singleton
public class ACLExpressionEvaluatorProvider {
  public ACLExpressionEvaluator get(PermissionCheck permissionCheck, Map<String, String> attributes) {
    return new ACLExpressionEvaluator(permissionCheck, attributes);
  }
}
