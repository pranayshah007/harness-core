/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.k8s.exception;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

@OwnedBy(CDP)
@UtilityClass
public class KubernetesExceptionMessages {
  public final String DRY_RUN_MANIFEST_FAILED = "Dry run of manifest failed";
  public final String READ_MANIFEST_FAILED = "Failed to read manifest: [%s]";
  public final String APPLY_MANIFEST_FAILED = "Apply manifest failed";
  public final String APPLY_NO_FILEPATH_SPECIFIED = "No file specified in the state";
  public final String WAIT_FOR_STEADY_STATE_FAILED = "Wait for steady state failed.";

  public final String NO_WORKLOADS_FOUND = "Missing managed workload in kubernetes manifest";
  public final String MULTIPLE_WORKLOADS = "More than one workloads found in the manifests";
  public final String NO_SERVICE_FOUND = "Missing service in kubernetes manifest";
  public final String MULTIPLE_SERVICES = "Multiple unmarked services found in manifest";
  public final String BG_CONFLICTING_SERVICE = "Found conflicting service [%s] in the cluster";
  public final String BG_SWAP_SERVICES_FAILED = "Failed to swap services [%s, %s]";

  public final String ROLLBACK_CLI_FAILED = "Failed to rollback resource %s in namespace %s to revision %s";

  public final String FAILED_TO_READ_MANIFEST_FILE = "Failed to read file at path [%s]";

  public final String CHARACTER_LIMIT_ERROR = ".*must be no more than \\d+ characters.*";
  public final String INVALID_CHARACTERS_ERROR = ".*must consist of lower case alphanumeric characters.*";

  public final String UNABLE_TO_FIND_FILE_IN_MANIFEST_DIRECTORY_FORMAT =
      "Unable to find file path %s in manifest directory";
}
