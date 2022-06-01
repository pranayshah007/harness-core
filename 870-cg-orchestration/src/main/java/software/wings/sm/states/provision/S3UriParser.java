/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.provision;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;

import com.amazonaws.services.s3.AmazonS3URI;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

@OwnedBy(CDP)
@Singleton
public class S3UriParser {
  @NotNull
  Map<String, List<String>> getBucketsFilesMap(List<String> filePaths) {
    Map<String, List<String>> buckets = new HashMap<>();
    filePaths.forEach(parametersFilePath -> {
      AmazonS3URI parametersFileUri = parseUrl(parametersFilePath);
      String bucketName = parametersFileUri.getBucket();
      String fileKey = parametersFileUri.getKey();
      if (buckets.get(bucketName) == null) {
        buckets.put(bucketName, new ArrayList<>(Collections.singletonList(fileKey)));
      } else {
        buckets.get(bucketName).add(fileKey);
      }
    });
    return buckets;
  }

  public AmazonS3URI parseUrl(String filePath) {
    try {
      return new AmazonS3URI(filePath);
    } catch (IllegalArgumentException e) {
      throw new InvalidRequestException(String.format("The %s format is not valid", filePath));
    }
  }
}
