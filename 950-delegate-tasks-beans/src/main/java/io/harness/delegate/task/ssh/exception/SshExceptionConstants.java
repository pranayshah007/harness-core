/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.ssh.exception;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;

import lombok.experimental.UtilityClass;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRADITIONAL})
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
  public final String NO_DESTINATION_DOWNLOAD_ARTIFACT_PATH_SPECIFIED =
      "destination path not specified in download artifact command unit";
  public final String NO_DESTINATION_PATH_SPECIFIED_HINT =
      "Please provide the destination path of the step copy command unit: %s";
  public final String NO_DESTINATION_DOWNLOAD_PATH_SPECIFIED_HINT =
      "Please provide the destination path of the step download artifact command unit: %s";
  public final String NO_DESTINATION_PATH_SPECIFIED_EXPLANATION =
      "destination path is missing from step copy command unit: %s";
  public final String NO_DESTINATION_DOWNLOAD_ARTIFACT_PATH_SPECIFIED_EXPLANATION =
      "destination path is missing from step download artifact command unit: %s";

  public final String ARTIFACT_SIZE_EXCEEDED = "Artifact file size exceeds 4GB. Not downloading file.";
  public final String ARTIFACT_SIZE_EXCEEDED_HINT = "Please make sure the file size is not exceeding 4GB.";
  public final String ARTIFACT_SIZE_EXCEEDED_EXPLANATION =
      "Artifact file size should not exceed  4GB. Artifact with id %s size is: %s";

  public final String ARTIFACT_CONFIGURATION_NOT_FOUND = "Missing artifact details";
  public final String ARTIFACT_CONFIGURATION_NOT_FOUND_HINT =
      "Please provide artifact details with the service definition";
  public final String ARTIFACT_CONFIGURATION_NOT_FOUND_EXPLANATION =
      "Selected copy artifact option requires artifact details to be specified with the service definition";

  public final String JENKINS_ARTIFACT_DOWNLOAD_FAILED = "Failed while trying to download Jenkins Artifact"
      + " with identifier: %s";
  public final String JENKINS_ARTIFACT_DOWNLOAD_HINT = "Please review the Jenkins Artifact Details and check "
      + "Path to the artifact. We recommend also checking for the artifact on Jenkins server";
  public final String JENKINS_ARTIFACT_DOWNLOAD_EXPLANATION = "Failed to download Jenkins Artifact with id: %s";

  public final String SCRIPT_EXECUTION_FAILED = "Failed to execute script command unit";
  public final String SCRIPT_EXECUTION_FAILED_HINT = "Please ensure the specified workingDir is available on the host";
  public final String SCRIPT_EXECUTION_FAILED_EXPLANATION =
      "Selected workingDir path should exist and accessible on the host in order to use it with the script command unit";
  public final String COPY_ARTIFACT_NOT_SUPPORTED_FOR_CUSTOM_ARTIFACT =
      "Copy Artifact is not supported for Custom Repository artifacts";
  public final String COPY_ARTIFACT_NOT_SUPPORTED_FOR_WINRM = "Copy Artifact is not supported for Winrm";
  public final String DOWNLOAD_ARTIFACT_NOT_SUPPORTED_FOR_CUSTOM_ARTIFACT =
      "Download Artifact is not supported for Custom Repository artifacts";
  public final String COPY_ARTIFACT_NOT_SUPPORTED_FOR_CUSTOM_ARTIFACT_HINT =
      "Please make sure there is no copy artifact command unit specified";
  public final String COPY_ARTIFACT_NOT_SUPPORTED_FOR_WINRM_HINT =
      "Please make sure there is no copy artifact command unit specified";
  public final String DOWNLOAD_ARTIFACT_NOT_SUPPORTED_FOR_CUSTOM_ARTIFACT_HINT =
      "Please make sure there is no download artifact command unit specified";
  public final String COPY_ARTIFACT_NOT_SUPPORTED_FOR_CUSTOM_ARTIFACT_EXPLANATION =
      "Copy Artifact is not supported for Custom Repository artifacts defined in service";
  public final String DOWNLOAD_ARTIFACT_NOT_SUPPORTED_FOR_CUSTOM_ARTIFACT_EXPLANATION =
      "Download Artifact is not supported for Custom Repository artifacts defined in service";
  public final String UNDECRYPTABLE_CONFIG_FILE_PROVIDED = "Could not decrypt the encrypted secret config file %s";
  public final String UNDECRYPTABLE_CONFIG_FILE_PROVIDED_HINT =
      "Please provide a valid encrypted config file instead of %s";
  public final String UNDECRYPTABLE_CONFIG_FILE_PROVIDED_EXPLANATION =
      "Encrypted config file %s could not be decrypted";
  public final String S3_ARTIFACT_DOWNLOAD_FAILED = "Failed while trying to download S3 artifact with path: %s "
      + "from bucket: %s";
  public final String S3_ARTIFACT_DOWNLOAD_HINT = "Please review the S3 Artifact Details and check the "
      + "S3 artifact path and bucket.";
  public final String S3_ARTIFACT_DOWNLOAD_EXPLANATION = "Failed to download S3 artifact with path: %s from"
      + " %s bucket";

  public final String NEXUS_ARTIFACT_DOWNLOAD_FAILED = "Failed while downloading Nexus Artifact"
      + " with identifier: %s";
  public final String NEXUS_ARTIFACT_DOWNLOAD_HINT = "Please review the Nexus Artifact Details and check the"
      + " repository and package details. We recommend also checking for the artifact on Nexus server";
  public final String NEXUS_ARTIFACT_DOWNLOAD_EXPLANATION = "Failed to download Nexus Artifact with id: %s";

  public final String DOWNLOAD_ARTIFACT_NOT_SUPPORTED_FOR_ARTIFACT =
      "Download Artifact is not supported for %s artifacts";
  public final String DOWNLOAD_ARTIFACT_NOT_SUPPORTED_FOR_ARTIFACT_EXPLANATION =
      "Download Artifact is not supported for %s artifacts defined in service";

  public final String SSH_INVALID_CREDENTIALS_HINT = "Please provide correct %s credentials.";
  public final String SSH_INVALID_CREDENTIALS_EXPLANATION = "Provided %s credentials are not authorized.";

  public final String INVALID_STORE_DELEGATE_CONFIG_TYPE_FAILED = "Invalid store type for config provided";
  public final String INVALID_STORE_DELEGATE_CONFIG_TYPE_HINT = "Please provide a valid config store type.";
  public final String INVALID_STORE_DELEGATE_CONFIG_TYPE_EXPLANATION =
      "Provided store type `%s` is not supported for Ssh/WinRm.";
  public final String FAILED_TO_COPY_WINRM_CONFIG_FILE_HINT =
      "Possible reasons for failure: \n1. Destination path is not a valid path. \n2. Missing permissions to perform operation on destination path. \n3. Invalid file content, file might contain invalid characters which are causing failures to translate via PowerShell. \n4. Disk volume might not be mounted.\n5. Please check console execution logs for detailed PowerShell error.";
  public final String FAILED_TO_COPY_CONFIG_FILE_EXPLANATION =
      "Failed to copy config file: %s to the destination path: %s";
  public final String FAILED_TO_COPY_CONFIG_FILE = "Failed to copy config file: %s.";
  public final String FAILED_TO_COPY_SSH_CONFIG_FILE_HINT =
      "Possible reasons for failure: \n1. Destination path is not a valid path. \n2. Missing permissions to perform operation on destination path. \n3. Disk volume might not be mounted.\n4. Please check console execution logs for detailed scp error.";
  public final String FAILED_TO_COPY_ARTIFACT_HINT =
      "Possible reasons for failure: \n1. Destination path is not a valid path. \n2. Missing permissions to perform operation on destination path. \n3. File is corrupted and integrity check fails over scp. \n4. Disk volume might not be mounted.\n5. Please check console execution logs for detailed scp error.";
  public final String FAILED_TO_COPY_ARTIFACT_HINT_EXPLANATION =
      "Failed to copy artifact file: %s to the destination path: %s";
  public final String FAILED_TO_COPY_ARTIFACT = "Failed to copy artifact file: %s.";

  public final String GITHUB_PACKAGE_ARTIFACT_DOWNLOAD_FAILED = "Failed while downloading GithubPackage Artifact"
      + " with identifier: %s";
  public final String GITHUB_PACKAGE_ARTIFACT_DOWNLOAD_HINT =
      "Please review the GithubPackage Artifact Details and check the"
      + " repository and package details. We recommend also checking for the artifact on Github server";
  public final String GITHUB_PACKAGE_ARTIFACT_DOWNLOAD_EXPLANATION =
      "Failed to download GithubPackage Artifact with id: %s";

  public final String COPY_AND_DOWNLOAD_ARTIFACT_NOT_SUPPORTED_FOR_GITHUB_PACKAGE_ARTIFACT_HINT =
      "Please make sure there is no copy or download artifact command unit specified";
  public final String COPY_AND_DOWNLOAD_ARTIFACT_NOT_SUPPORTED_FOR_GITHUB_PACKAGE_ARTIFACT_EXPLANATION =
      "Copy and Download Artifact for package type `%s` is not supported for Github Package Repository artifacts defined in service";
  public final String COPY_AND_DOWNLOAD_ARTIFACT_NOT_SUPPORTED_FOR_GITHUB_PACKAGE_ARTIFACT_FAILED =
      "Copy and Download Artifact for package type `%s` is not supported for Github Package Repository artifacts";

  public final String COPY_ARTIFACT_NOT_SUPPORTED_FOR_AZURE_UNIVERSAL_PACKAGE_ARTIFACT_HINT =
      "Please make sure there is no copy artifact command unit specified and use download command unit instead";
  public final String COPY_ARTIFACT_NOT_SUPPORTED_FOR_AZURE_UNIVERSAL_PACKAGE_ARTIFACT_EXPLANATION =
      "Copy Artifact for package type `universal` is not supported for Azure artifacts defined in service. Please use download artifact and make sure Azure CLI and devops extension is installed";
  public final String COPY_ARTIFACT_NOT_SUPPORTED_FOR_AZURE_UNIVERSAL_PACKAGE_ARTIFACT_FAILED =
      "Copy Artifact for package type `universal` is not supported for Azure artifacts";

  public final String AZURE_CLI_INSTALLATION_CHECK_FAILED = "Azure CLI or devops extension not installed";
  public final String AZURE_CLI_INSTALLATION_CHECK_HINT =
      "Please install Azure CLI and devops extension in order to download Azure Universal packages.\nYou can use this guide https://learn.microsoft.com/en-us/azure/devops/artifacts/quickstarts/universal-packages?view=azure-devops&tabs=Windows";
  public final String AZURE_CLI_INSTALLATION_CHECK_EXPLANATION = "Azure CLI check failed";

  public final String GCS_INVALID_ARTIFACT_PATH =
      "Failed while trying to download GCS artifact, missing artifact path. ";
  public final String GCS_INVALID_ARTIFACT_PATH_HINT = "Please review the GCS Artifact Details and check the "
      + "GCS artifact path and bucket.";
  public final String GCS_INVALID_ARTIFACT_PATH_EXPLANATION = "GCS Artifact file path not provided";

  public final String GCS_ARTIFACT_DOWNLOAD_FAILED = "Failed while trying to download GCS artifact with path: %s "
      + "from bucket: %s and project: %s";
  public final String GCS_ARTIFACT_DOWNLOAD_HINT = GCS_INVALID_ARTIFACT_PATH_HINT;
  public final String GCS_ARTIFACT_DOWNLOAD_EXPLANATION = "Failed to download GCS artifact with path: %s from"
      + " %s bucket and %s project";

  public final String GCS_ARTIFACT_CALCULATE_SIZE_FAILED =
      "Failed while trying to calculate GCS artifact file size. Artifact details: path: %s "
      + "from bucket: %s and project: %s";
  public final String GCS_ARTIFACT_CALCULATE_SIZE_FAILED_HINT = GCS_INVALID_ARTIFACT_PATH_HINT;
  public final String GCS_ARTIFACT_CALCULATE_SIZE_FAILED_EXPLANATION =
      "Failed to calculate GCS artifact file size. Artifacat details: path: %s from"
      + " %s bucket and %s project";

  public final String BAD_ARTIFACT_TYPE = "Please contact harness support team";
  public final String BAD_ARTIFACT_TYPE_HINT = "Unexpected artifact configuration of type %s";
  public final String BAD_ARTIFACT_TYPE_EXPLANATION = "Invalid artifact type, expected %s";
}
