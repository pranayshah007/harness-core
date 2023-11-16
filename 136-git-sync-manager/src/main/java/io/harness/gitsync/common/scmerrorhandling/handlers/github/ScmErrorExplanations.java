/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.common.scmerrorhandling.handlers.github;
import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;

import lombok.experimental.UtilityClass;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_GITX})
@UtilityClass
@OwnedBy(PL)
public class ScmErrorExplanations {
  public static final String FILE_NOT_FOUND =
      "The requested file path<FILEPATH> doesn't exist in git. Possible reasons can be:\n"
      + "1. The requested file path doesn't exist for given branch<BRANCH> and repo<REPO>\n"
      + "2. The given branch<BRANCH> or repo<REPO> is invalid";
  public static final String INVALID_CONNECTOR_CREDS =
      "The credentials provided in the Github connector<CONNECTOR> are invalid or have expired or are not configured with SSO. ";
  public static final String REPO_NOT_FOUND = "Provided Github repository<REPO> does not exist or has been deleted.";
  public static final String BRANCH_NOT_FOUND = "Provided Github branch<BRANCH> does not exist or has been deleted.";
  public static final String OAUTH_ACCESS_DENIED =
      "If you are using OAUTH, access to Github may have been revoked or token is invalid or expired.";
  public static final String EXPLANATION_REQUEST_TIMED_OUT =
      "Request to the Github Server to fetch file<FILEPATH> from repo<REPO> and branch<BRANCH> timed out.\n"
      + "This might have occurred due to the following reasons:\n"
      + "1. Network connectivity issues from delegate to the Github server\n"
      + "2. Github server experiencing higher traffic than usual, or a downtime at the moment.";
}
