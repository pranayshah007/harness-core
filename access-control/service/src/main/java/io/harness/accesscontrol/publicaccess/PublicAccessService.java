/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.publicaccess;

import io.harness.accesscontrol.resources.resourcetypes.ResourceType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.spec.server.accesscontrol.v1.model.Scope;

@OwnedBy(HarnessTeam.PL)
public interface PublicAccessService {
  boolean enable(String resourceIdentifier, ResourceType resourceType, Scope resourceScope);
}
