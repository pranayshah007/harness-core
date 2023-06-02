/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.aws.lambda.exception;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.CDP)
@UtilityClass
public class AwsLambdaExceptionConstants {
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
}
