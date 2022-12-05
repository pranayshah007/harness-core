/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.scim;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.scim.ScimListResponse;
import io.harness.scim.service.ScimSystemService;
import io.harness.scim.system.AuthenticationScheme;
import io.harness.scim.system.BulkConfig;
import io.harness.scim.system.ChangePasswordConfig;
import io.harness.scim.system.ETagConfig;
import io.harness.scim.system.FilterConfig;
import io.harness.scim.system.PatchConfig;
import io.harness.scim.system.ResourceType;
import io.harness.scim.system.ServiceProviderConfig;
import io.harness.scim.system.SortConfig;

import com.google.common.collect.Sets;
import java.util.Collections;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@NoArgsConstructor
@Slf4j
@OwnedBy(PL)
public class NGScimSystemServiceImpl implements ScimSystemService {
  public static final String DOCUMENTATION_URI = "https://docs.harness.io/category/fe0577j8ie-access-management";
  public static final String USER_SCHEMA = "urn:ietf:params:scim:schemas:core:2.0:User";
  public static final String GROUP_SCHEMA = "urn:ietf:params:scim:schemas:core:2.0:Group";

  @Override
  public ServiceProviderConfig getServiceProviderConfig(String accountIdentifier) {
    return new ServiceProviderConfig(DOCUMENTATION_URI, new PatchConfig(true), new BulkConfig(false, 1, 0),
        new FilterConfig(true, 1000), new ChangePasswordConfig(false), new SortConfig(false), new ETagConfig(false),
        Collections.singletonList(AuthenticationScheme.geBearerTokenAuth(true)));
  }

  @Override
  public ScimListResponse<ResourceType> getResourceTypes(String accountIdentifier) {
    ScimListResponse<ResourceType> resourceTypeScimListResponse = new ScimListResponse<>();

    resourceTypeScimListResponse.resource(getUserResourceType());
    resourceTypeScimListResponse.resource(getGroupResourceType());

    resourceTypeScimListResponse.startIndex(0);
    resourceTypeScimListResponse.itemsPerPage(resourceTypeScimListResponse.getResources().size());
    resourceTypeScimListResponse.totalResults(resourceTypeScimListResponse.getResources().size());
    return resourceTypeScimListResponse;
  }

  private ResourceType getUserResourceType() {
    String user = "User";
    return new ResourceType(
        Sets.newHashSet(USER_SCHEMA), user, DOCUMENTATION_URI, user, "/Users", "User Account", USER_SCHEMA);
  }

  private ResourceType getGroupResourceType() {
    String group = "Group";
    return new ResourceType(
        Sets.newHashSet(GROUP_SCHEMA), group, DOCUMENTATION_URI, group, "/Group", "Groups", GROUP_SCHEMA);
  }
}