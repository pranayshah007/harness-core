/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.audit;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

@OwnedBy(PL)
@UtilityClass
public class ResourceTypeConstants {
  public static final String ORGANIZATION = "ORGANIZATION";
  public static final String PROJECT = "PROJECT";
  public static final String USER_GROUP = "USER_GROUP";
  public static final String SECRET = "SECRET";
  public static final String RESOURCE_GROUP = "RESOURCE_GROUP";
  public static final String USER = "USER";
  public static final String ROLE = "ROLE";
  public static final String ROLE_ASSIGNMENT = "ROLE_ASSIGNMENT";
  public static final String PIPELINE = "PIPELINE";
  public static final String TRIGGER = "TRIGGER";
  public static final String TEMPLATE = "TEMPLATE";
  public static final String INPUT_SET = "INPUT_SET";
  public static final String DELEGATE_CONFIGURATION = "DELEGATE_CONFIGURATION";
  public static final String SERVICE = "SERVICE";
  public static final String ENVIRONMENT = "ENVIRONMENT";
  public static final String ENVIRONMENT_GROUP = "ENVIRONMENT_GROUP";
  public static final String DELEGATE = "DELEGATE";
  public static final String DELEGATE_GROUPS = "DELEGATE_GROUPS";
  public static final String SERVICE_ACCOUNT = "SERVICE_ACCOUNT";
  public static final String CONNECTOR = "CONNECTOR";
  public static final String API_KEY = "API_KEY";
  public static final String TOKEN = "TOKEN";
  public static final String DELEGATE_TOKEN = "DELEGATE_TOKEN";
  public static final String GOVERNANCE_POLICY = "GOVERNANCE_POLICY";
  public static final String GOVERNANCE_POLICY_SET = "GOVERNANCE_POLICY_SET";
  public static final String FILE = "FILE";
  public static final String VARIABLE = "VARIABLE";
  public static final String CHAOS_HUB = "CHAOS_HUB";
  public static final String MONITORED_SERVICE = "MONITORED_SERVICE";
  public static final String CHAOS_AGENT = "CHAOS_AGENT";
  public static final String CHAOS_WORKFLOW = "CHAOS_WORKFLOW";
  public static final String SERVICE_LEVEL_OBJECTIVE = "SERVICE_LEVEL_OBJECTIVE";
}
