/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.azure.client;

import io.harness.azure.model.AzureConfig;

import software.wings.helpers.ext.azure.AzureIdentityAccessTokenResponse;

import com.microsoft.azure.management.graphrbac.BuiltInRole;
import com.microsoft.azure.management.graphrbac.RoleAssignment;
import java.util.List;

public interface AzureAuthorizationClient {
  /**
   * Creates a role assignment.
   *
   * @param azureConfig AzureConfig
   * @param subscriptionId The role assignment scope.
   * @param objectId The principal or object ID.
   * @param roleAssignmentName The name of the role assignment to create. It can be any valid GUID.
   * @param builtInRole The role definition ID.
   * @return RoleAssignment
   */
  RoleAssignment roleAssignmentAtSubscriptionScope(AzureConfig azureConfig, String subscriptionId, String objectId,
      String roleAssignmentName, BuiltInRole builtInRole);

  /**
   * Get Role definition by scope and role name.
   *
   * @param azureConfig
   * @param scope
   * @param roleName
   * @return
   */
  List<RoleAssignment> getRoleDefinition(AzureConfig azureConfig, String scope, String roleName);

  /**
   * Validate azure connection with a provided clientId, tenantId, secret and environment type. Will throw exception if
   *  connection can't be made
   * @param azureConfig all information required for Azure connection
   */
  boolean validateAzureConnection(AzureConfig azureConfig);

  /**
   * Authenticate within Azure and get access token in return
   * @param azureConfig all information required for Azure connection
   */
  AzureIdentityAccessTokenResponse getUserAccessToken(AzureConfig azureConfig);
}
