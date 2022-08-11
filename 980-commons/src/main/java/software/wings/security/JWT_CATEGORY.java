/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.security;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

@OwnedBy(PL)
public enum JWT_CATEGORY {
  MULTIFACTOR_AUTH(3 * 60 * 1000), // 3 mins
  SSO_REDIRECT(60 * 1000), // 1 min
  OAUTH_REDIRECT(3 * 60 * 1000), // 1 min
  PASSWORD_SECRET(4 * 60 * 60 * 1000), // 4 hrs
  ZENDESK_SECRET(4 * 60 * 60 * 1000), // 4 hrs
  EXTERNAL_SERVICE_SECRET(60 * 60 * 1000), // 1hr
  IDENTITY_SERVICE_SECRET(60 * 60 * 1000), // 1hr
  AUTH_SECRET(24 * 60 * 60 * 1000), // 24 hr
  JIRA_SERVICE_SECRET(7 * 24 * 60 * 60 * 1000), // 7 days
  INVITE_SECRET(7 * 24 * 60 * 60 * 1000), // 7 days
  MARKETPLACE_SIGNUP(24 * 60 * 60 * 1000), // 1 day
  API_KEY(10 * 60 * 1000), // 10 mins; API_KEY secret is not configured in config.yml!
  DATA_HANDLER_SECRET(60 * 60 * 1000),
  NEXT_GEN_MANAGER_SECRET(60 * 60 * 1000);
  private int validityDuration;

  JWT_CATEGORY(int validityDuration) {
    this.validityDuration = validityDuration;
  }

  public int getValidityDuration() {
    return validityDuration;
  }
}
