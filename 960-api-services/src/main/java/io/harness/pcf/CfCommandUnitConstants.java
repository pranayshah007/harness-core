/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pcf;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

@OwnedBy(CDP)
public interface CfCommandUnitConstants {
  String FetchFiles = "Download Manifest Files";
  String FetchGitFiles = "Download Git Manifest Files";
  String FetchCustomFiles = "Download Custom Manifest Files";
  String VerifyManifests = "Verifying Manifests";
  String CheckExistingApps = "Check Existing Applications";
  String PcfSetup = "Setup Application";
  String Wrapup = "Wrap up";
  String Pcfplugin = "Execute CF Command";
  String Downsize = "Downsize Application";
  String Upsize = "Upsize Application";
  String FetchCommandScript = "Download Command Script";
  String SwapRoutesForNewApplication = "Swap Routes For New Application";
  String SwapRoutesForExistingApplication = "Swap Routes For Existing Application";
  String Rename = "Renaming Apps";
  String SwapRollback = "Swap Rollback";
  String Deploy = "Deploy";
  String Rollback = "Rollback";
  String RouteMapping = "Route Mapping";
}
