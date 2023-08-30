/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.helpers.ext.ecr;
import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.aws.beans.AwsInternalConfig;

import java.util.List;
import java.util.Map;

/**
 * Created by brett on 7/15/17
 */

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ARTIFACTS})
@OwnedBy(CDC)
public interface EcrService {
  int MAX_NO_OF_IMAGES = 1000;
  /**
   * Gets builds.
   *
   * @param awsConfig         the aws cloud provider config
   * @param registryId
   * @param region            the region name
   * @param imageName         the image name
   * @param maxNumberOfBuilds the max number of builds
   * @return the builds
   */
  List<BuildDetailsInternal> getBuilds(AwsInternalConfig awsConfig, String registryId, String imageUrl, String region,
      String imageName, int maxNumberOfBuilds);

  /**
   * Gets last successful build.
   *
   * @param awsConfig the ecr config
   * @param imageName the image name
   * @return the last successful build
   */
  BuildDetailsInternal getLastSuccessfulBuild(AwsInternalConfig awsConfig, String imageName);

  /**
   * Validates the Image
   *
   * @param awsConfig  the ecr config
   * @param region     the aws region
   * @param registryId
   * @param imageName  the image name
   * @return the boolean
   */
  boolean verifyRepository(AwsInternalConfig awsConfig, String region, String registryId, String imageName);

  /**
   * Lists aws regions
   *
   * @param awsConfig aws config
   * @return
   */
  List<String> listRegions(AwsInternalConfig awsConfig);

  /**
   * List ecr registry list.
   *
   * @param awsConfig  the ecr config
   * @param registryId
   * @return the list
   */
  List<String> listEcrRegistry(AwsInternalConfig awsConfig, String region, String registryId);

  /**
   * Validates the Image
   *
   * @param awsConfig
   * @param registryId
   */
  boolean verifyImageName(
      AwsInternalConfig awsConfig, String registryId, String imageUrl, String region, String imageName);

  List<Map<String, String>> getLabels(
      AwsInternalConfig awsConfig, String registryId, String imageName, String region, List<String> tags);

  boolean validateCredentials(
      AwsInternalConfig awsConfig, String registryId, String imageUrl, String region, String imageName);

  BuildDetailsInternal verifyBuildNumber(AwsInternalConfig awsInternalConfig, String registryId, String imageUrl,
      String region, String imageName, String tag);

  BuildDetailsInternal getLastSuccessfulBuildFromRegex(AwsInternalConfig awsInternalConfig, String registryId,
      String imageUrl, String region, String imageName, String tagRegex);
}
