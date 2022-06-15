/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.ssh.exception;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.CDP)
@UtilityClass
public class SshExceptionConstants {
  public final String EMPTY_ARTIFACT_PATH = "not able to find artifact path";
  public final String EMPTY_ARTIFACT_PATH_HINT = "Please check artifactDirectory or artifactPath field";
  public final String EMPTY_ARTIFACT_PATH_EXPLANATION = "artifact path is missing for artifactory identifier: %s";

  public final String ARTIFACT_DOWNLOAD_FAILED = "Failed while trying to download artifact from %s repository"
      + " with identifier: %s";
  public final String ARTIFACT_DOWNLOAD_HINT = "Please review the Artifact Details and check the File/Folder "
      + "Path to the artifact. We recommend also checking for the artifact in the given path in your"
      + " %s repository. ";
  public final String ARTIFACT_DOWNLOAD_EXPLANATION = "Failed to download artifact with id: %s from"
      + " %s repository";

  public final String ARTIFACT_NOT_FOUND = "File %s could not be found";
  public final String ARTIFACT_NOT_FOUND_HINT = "Failed to locate file %s after download. ";
  public final String ARTIFACT_NOT_FOUND_EXPLANATION = "Downloaded file %s could not be found local file system."
      + " Please retry the operation either check artifact details.";

  public final String NO_DESTINATION_PATH_SPECIFIED = "destination path not specified in copy command unit";
  public final String NO_DESTINATION_PATH_SPECIFIED_HINT =
      "Please provide the destination path of the step copy command unit: %s";
  public final String NO_DESTINATION_PATH_SPECIFIED_EXPLANATION =
      "destination path is missing from step copy command unit: %s";

  public final String NO_CONFIG_FILE_PROVIDED = "Config file not provided for copy config command unit";
  public final String NO_CONFIG_FILE_PROVIDED_HINT = "Please provide the config file with the service definition";
  public final String NO_CONFIG_FILE_PROVIDED_EXPLANATION =
      "Selected copy config option requires config file to be specified with the service definition";

  public final String ARTIFACT_SIZE_EXCEEDED = "Artifact file size exceeds 4GB. Not downloading file.";
  public final String ARTIFACT_SIZE_EXCEEDED_HINT = "Please make sure the file size is not exceeding 4GB.";
  public final String ARTIFACT_SIZE_EXCEEDED_EXPLANATION =
      "Artifact file size should not exceed  4GB. Artifact with id %s size is: %s";
}
