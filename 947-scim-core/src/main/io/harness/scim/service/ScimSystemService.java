/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.scim.service;

import io.harness.scim.ScimListResponse;
import io.harness.scim.system.ResourceType;
import io.harness.scim.system.ServiceProviderConfig;

public interface ScimSystemService {
  ServiceProviderConfig getServiceProviderConfig(String accountIdentifier);
  ScimListResponse<ResourceType> getResourceTypes(String accountIdentifier);
}
