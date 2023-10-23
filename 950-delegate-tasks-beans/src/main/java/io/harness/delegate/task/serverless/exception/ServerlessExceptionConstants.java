/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.serverless.exception;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.CDP)
@UtilityClass
public class ServerlessExceptionConstants {
  public final String NO_SERVERLESS_MANIFEST_FAILED = "Not able to fetch serverless manifest file";

  public final String NO_SERVERLESS_MANIFEST_HINT = "please add a serverless manifest file inside provided path: %s";

  public final String NO_SERVERLESS_MANIFEST_EXPLANATION = "not able to find a serverless manifest file "
      + "(serverless.yaml/serverless.yml/serverless.json) inside provided path: %s";

  public final String DOWNLOAD_FROM_ARTIFACTORY_FAILED = "Failed while trying to download artifact from artifactory"
      + " with identifier: %s";

  public final String DOWNLOAD_FROM_ARTIFACTORY_HINT = "Please review the Artifact Details and check the File/Folder "
      + "Path to the artifact. We recommend also checking for the artifact in the given path in your"
      + " Artifactory Repository. ";

  public final String DOWNLOAD_FROM_ARTIFACTORY_EXPLANATION = "Failed to download artifact: %s from"
      + " Artifactory: %s";

  public final String DOWNLOAD_FROM_S3_FAILED = "Failed while trying to download artifact from s3"
      + " with identifier: %s";

  public final String DOWNLOAD_FROM_S3_HINT = "Please review the Artifact Details and check the File/Folder "
      + "Path to the artifact. We recommend also checking for the artifact in the given path in your"
      + " S3 Repository. ";

  public final String DOWNLOAD_FROM_S3_EXPLANATION = "Failed to download artifact: %s from"
      + " S3 bucket: %s";

  public final String BLANK_ARTIFACT_PATH = "not able to find artifact path";

  public final String BLANK_ARTIFACT_PATH_HINT = "Please check artifactDirectory or artifactPath field";

  public final String BLANK_ARTIFACT_PATH_EXPLANATION = "artifact path is not present for artifactory identifier: %s";

  public final String SERVERLESS_MANIFEST_PROCESSING_FAILED = "failed to process serverless manifest file";

  public final String SERVERLESS_MANIFEST_PROCESSING_HINT =
      "Please check that the serverless manifest file is properly formatted in either JSON or YAML";

  public final String SERVERLESS_MANIFEST_PROCESSING_EXPLANATION = "serverless manifest file has an invalid yaml or "
      + "json content.";

  public final String SERVERLESS_COMMAND_FAILURE = "%s command failed.";

  public final String SERVERLESS_COMMAND_FAILURE_HINT = "Please check and fix %s command. ";

  public final String SERVERLESS_COMMAND_FAILURE_EXPLANATION = "%s command failed. Please check Pipeline Execution"
      + " logs for more details.";

  public final String SERVERLESS_GIT_FILES_DOWNLOAD_EXPLANATION = "Failed while trying to download files from manifest "
      + "store ConnectorDisconnectHandler: %s with identifier: %s";

  public final String SERVERLESS_GIT_FILES_DOWNLOAD_HINT = "Please check manifest store ConnectorDisconnectHandler with identifier: %s";

  public final String SERVERLESS_GIT_FILES_DOWNLOAD_FAILED = "Failed while trying to fetch files from manifest store ";

  public final String SERVERLESS_S3_FILES_DOWNLOAD_HINT = "Please check the following Harness S3 Manifest Inputs\n"
      + " Aws Credentials\n"
      + " S3 Bucket Name\n"
      + " Region\n"
      + " FilePath";

  public final String SERVERLESS_S3_FILES_DOWNLOAD_FAILED =
      "Failed while fetching the zip file [%s] from S3 bucket [%s] in region [%s]";

  public final String SERVERLESS_FETCH_DEPLOY_OUTPUT_FAILED = "failed to fetch deployment output";

  public final String SERVERLESS_FETCH_DEPLOY_OUTPUT_HINT = "failed to fetch deployment output";

  public final String SERVERLESS_FETCH_DEPLOY_OUTPUT_EXPLANATION =
      "not able to read cloudformation-template-update-stack.json "
      + "inside service directory to fetch deployment output";
}
