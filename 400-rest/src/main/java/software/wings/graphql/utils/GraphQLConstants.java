/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.graphql.utils;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;

import lombok.experimental.UtilityClass;

@UtilityClass
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class GraphQLConstants {
  public static final String APP_ID_ARG = "applicationId";
  public static final String MAX_PAGE_SIZE_STR = "20";

  // Error messages
  public static final String FEATURE_NOT_ENABLED = "Feature not enabled";
  public static final String RATE_LIMIT_REACHED =
      "You've reached your account's rate limit for data queries. Please try again later.";
  public static final String INVALID_API_KEY = "Invalid Api Key";
  public static final String NOT_AUTHORIZED = "User not authorized";
  public static final String INVALID_TOKEN = "Invalid Token";
  public static final String CREATE_APPLICATION_API = "createApplication";
  public static final String DELETE_APPLICATION_API = "deleteApplication";
  public static final String HTTP_SERVLET_REQUEST = "httpServletRequest";
  public static final String GRAPHQL_QUERY_STRING = "graphqlQueryString";
  public static final String CREATE_USERGROUP_API = "createUserGroup";
  public static final String DELETE_USERGROUP_API = "deleteUserGroup";
  public static final String UPDATE_USERGROUP_API = "updateUserGroup";
  public static final String UPDATE_USERGROUP_PERMISSIONS_API = "updateUserGroupPermissions";
  public static final String ADD_USER_TO_USERGROUP = "addUserToUserGroup";
  public static final String REMOVE_USER_FROM_USERGROUP = "removeUserFromUserGroup";
  public static final String ADD_ACCOUNT_PERMISSION = "addAccountPermission";
  public static final String ADD_APP_PERMISSION = "addAppPermission";
  public static final String CREATE_API_KEY = "createApiKey";
  public static final String UPDATE_API_KEY = "updateApiKey";
  public static final String DELETE_API_KEY = "deleteApiKey";
  public static final String NOT_AUTHORIZED_SUPPORT_USER_FOR_MUTATION =
      "None of the graphql can contain mutation for support user";
}
