/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.publicaccess.utils;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.spec.server.accesscontrol.v1.model.PublicAccessRequest;
import io.harness.spec.server.accesscontrol.v1.model.Scope;

@OwnedBy(PL)
public class PublicAccessClientUtils {
  public static PublicAccessRequest getPublicAccessRequest(
      String accountId, String orgId, String projectId, String resourceType, String resourceId) {
    PublicAccessRequest publicAccessRequest = new PublicAccessRequest();
    publicAccessRequest.setResourceType(resourceType);
    publicAccessRequest.setResourceIdentifier(resourceId);
    Scope scope = new Scope();
    scope.setOrg(orgId);
    scope.setAccount(accountId);
    scope.setProject(projectId);

    publicAccessRequest.setResourceScope(scope);
    return publicAccessRequest;
  }
}
