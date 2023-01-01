/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.utils;

import static io.harness.provision.TerraformConstants.TF_S3_FILE_DIR_FOR_VARIABLES_AND_TARGETS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.amazonaws.services.s3.AmazonS3URI;
import java.nio.file.Paths;

@OwnedBy(HarnessTeam.CDP)
public class S3Utils {
  private static final String USER_DIR_KEY = "user.dir";

  public String resolveS3BucketAbsoluteFilePath(String downloadDir, AmazonS3URI s3URI) {
    return Paths.get(downloadDir, s3URI.getKey()).toString();
  }

  public String buildS3FilePath(String accountId, AmazonS3URI s3URI) {
    return Paths
        .get(Paths.get(System.getProperty(USER_DIR_KEY)).toString(),
            TF_S3_FILE_DIR_FOR_VARIABLES_AND_TARGETS.replace("${ACCOUNT_ID}", accountId)
                .replace("${REPO_TYPE}", "terraform")
                .replace("${BUCKET_NAME}", s3URI.getBucket()))
        .toString();
  }
}
