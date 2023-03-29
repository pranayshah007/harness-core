/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.helpers.ext.ecr;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;

import io.harness.annotations.dev.OwnedBy;
import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.artifacts.beans.BuildDetailsInternal.BuildDetailsInternalMetadataKeys;
import io.harness.artifacts.comparator.BuildDetailsInternalComparatorAscending;
import io.harness.artifacts.comparator.BuildDetailsInternalComparatorDescending;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.context.MdcGlobalContextData;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidArtifactServerException;
import io.harness.exception.exceptionmanager.exceptionhandler.ExceptionMetadataKeys;
import io.harness.manage.GlobalContextManager;

import software.wings.service.impl.AwsApiHelperService;

import com.amazonaws.services.codedeploy.model.InvalidTagException;
import com.amazonaws.services.ecr.model.DescribeImagesRequest;
import com.amazonaws.services.ecr.model.DescribeImagesResult;
import com.amazonaws.services.ecr.model.DescribeRepositoriesRequest;
import com.amazonaws.services.ecr.model.DescribeRepositoriesResult;
import com.amazonaws.services.ecr.model.ListImagesRequest;
import com.amazonaws.services.ecr.model.ListImagesResult;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Created by brett on 7/15/17
 */
@OwnedBy(CDC)
@Singleton
public class EcrServiceImpl implements EcrService {
  @Inject private AwsApiHelperService awsApiHelperService;

  @VisibleForTesting
  List<BuildDetailsInternal> getBuildsFallback(
      AwsInternalConfig awsConfig, String imageUrl, String region, String imageName, int maxNumberOfBuilds) {
    List<BuildDetailsInternal> buildDetailsInternals = new ArrayList<>();
    try {
      ListImagesResult listImageResult;
      ListImagesRequest listImagesRequest = new ListImagesRequest().withRepositoryName(imageName);
      do {
        listImageResult = awsApiHelperService.listEcrImages(awsConfig, region, listImagesRequest);
        listImageResult.getImageIds()
            .stream()
            .filter(imageIdentifier -> imageIdentifier != null && isNotEmpty(imageIdentifier.getImageTag()))
            .forEach(imageIdentifier -> {
              Map<String, String> metadata = new HashMap();
              metadata.put(BuildDetailsInternalMetadataKeys.image, imageUrl + ":" + imageIdentifier.getImageTag());
              metadata.put(BuildDetailsInternalMetadataKeys.tag, imageIdentifier.getImageTag());
              buildDetailsInternals.add(BuildDetailsInternal.builder()
                                            .number(imageIdentifier.getImageTag())
                                            .metadata(metadata)
                                            .uiDisplayName("Tag# " + imageIdentifier.getImageTag())
                                            .build());
            });
        listImagesRequest.setNextToken(listImageResult.getNextToken());
      } while (listImagesRequest.getNextToken() != null);
    } catch (Exception e) {
      throw new GeneralException(ExceptionUtils.getMessage(e), USER);
    }
    // Sorting at build tag for docker artifacts.
    return buildDetailsInternals.stream()
        .sorted(new BuildDetailsInternalComparatorAscending())
        .collect(Collectors.toList());
  }
  @Override
  public List<BuildDetailsInternal> getBuilds(
      AwsInternalConfig awsConfig, String imageUrl, String region, String imageName, int maxNumberOfBuilds) {
    List<BuildDetailsInternal> buildDetailsInternals = new ArrayList<>();
    try {
      DescribeImagesResult describeImagesResult;
      DescribeImagesRequest describeImagesRequest = new DescribeImagesRequest().withRepositoryName(imageName);
      do {
        describeImagesResult = awsApiHelperService.describeEcrImages(awsConfig, region, describeImagesRequest);

        describeImagesResult.getImageDetails()
            .stream()
            .filter(imageIdentifier -> imageIdentifier != null && isNotEmpty(imageIdentifier.getImageTags()))
            .forEach(imageIdentifier -> {
              imageIdentifier.getImageTags()
                  .stream()
                  .filter(image -> image != null && isNotEmpty(image))
                  .forEach(image -> {
                    Map<String, String> metadata = new HashMap();
                    metadata.put(BuildDetailsInternalMetadataKeys.image, imageUrl + ":" + image);
                    metadata.put(BuildDetailsInternalMetadataKeys.tag, image);
                    buildDetailsInternals.add(BuildDetailsInternal.builder()
                                                  .number(image)
                                                  .metadata(metadata)
                                                  .uiDisplayName("Tag# " + image)
                                                  .imagePushedAt(imageIdentifier.getImagePushedAt())
                                                  .build());
                  });
            });
        describeImagesRequest.setNextToken(describeImagesResult.getNextToken());
      } while (describeImagesRequest.getNextToken() != null);
    } catch (Exception e) {
      return getBuildsFallback(awsConfig, imageUrl, region, imageName, maxNumberOfBuilds);
    }
    // Sorting at build tag for docker artifacts.
    return buildDetailsInternals;
  }

  @Override
  public BuildDetailsInternal getLastSuccessfulBuild(AwsInternalConfig awsConfig, String imageName) {
    return null;
  }

  @Override
  public boolean verifyRepository(AwsInternalConfig awsConfig, String region, String repositoryName) {
    return listEcrRegistry(awsConfig, region).contains(repositoryName);
  }

  @Override
  public List<String> listRegions(AwsInternalConfig awsConfig) {
    return awsApiHelperService.listRegions(awsConfig);
  }

  @Override
  public List<String> listEcrRegistry(AwsInternalConfig awsConfig, String region) {
    List<String> repoNames = new ArrayList<>();
    DescribeRepositoriesRequest describeRepositoriesRequest = new DescribeRepositoriesRequest();
    DescribeRepositoriesResult describeRepositoriesResult;
    do {
      describeRepositoriesResult = awsApiHelperService.listRepositories(awsConfig, describeRepositoriesRequest, region);
      describeRepositoriesResult.getRepositories().forEach(repository -> repoNames.add(repository.getRepositoryName()));
      describeRepositoriesRequest.setNextToken(describeRepositoriesResult.getNextToken());
    } while (describeRepositoriesRequest.getNextToken() != null);

    return repoNames;
  }

  @Override
  public List<Map<String, String>> getLabels(
      AwsInternalConfig awsConfig, String imageName, String region, List<String> tags) {
    return Collections.singletonList(awsApiHelperService.fetchLabels(awsConfig, imageName, region, tags));
  }

  @Override
  public BuildDetailsInternal getLastSuccessfulBuildFromRegex(
      AwsInternalConfig awsInternalConfig, String imageUrl, String region, String imageName, String tagRegex) {
    List<BuildDetailsInternal> builds =
        getBuilds(awsInternalConfig, imageUrl, region, imageName, MAX_NO_OF_TAGS_PER_IMAGE);

    Pattern pattern = Pattern.compile(tagRegex.replace(".", "\\.").replace("?", ".?").replace("*", ".*?"));

    if (EmptyPredicate.isEmpty(builds)) {
      throw new InvalidArtifactServerException(
          "There are no builds for this image: " + imageName + " and tagRegex: " + tagRegex, USER);
    }

    List<BuildDetailsInternal> buildsResponse =
        builds.stream()
            .filter(build -> !build.getNumber().endsWith("/") && pattern.matcher(build.getNumber()).find())
            .sorted(new BuildDetailsInternalComparatorDescending())
            .collect(Collectors.toList());

    if (buildsResponse.isEmpty()) {
      throw new InvalidArtifactServerException(
          "There are no builds for this image: " + imageName + " and tagRegex: " + tagRegex, USER);
    }

    return buildsResponse.get(0);
  }

  @Override
  public boolean verifyImageName(AwsInternalConfig awsConfig, String imageUrl, String region, String imageName) {
    try {
      getBuilds(awsConfig, imageUrl, region, imageName, 1);
    } catch (Exception e) {
      return false;
    }
    return true;
  }

  @Override
  public boolean validateCredentials(AwsInternalConfig awsConfig, String imageUrl, String region, String imageName) {
    try {
      getBuilds(awsConfig, imageUrl, region, imageName, 1);
    } catch (Exception e) {
      return false;
    }
    return true;
  }

  @Override
  public BuildDetailsInternal verifyBuildNumber(
      AwsInternalConfig awsInternalConfig, String imageUrl, String region, String imageName, String tag) {
    List<BuildDetailsInternal> builds =
        getBuilds(awsInternalConfig, imageUrl, region, imageName, MAX_NO_OF_TAGS_PER_IMAGE);
    builds = builds.stream().filter(build -> build.getNumber().equals(tag)).collect(Collectors.toList());
    if (builds.size() != 1) {
      Map<String, String> imageDataMap = new HashMap<>();
      imageDataMap.put(ExceptionMetadataKeys.IMAGE_NAME.name(), imageName);
      imageDataMap.put(ExceptionMetadataKeys.IMAGE_TAG.name(), tag);
      imageDataMap.put(ExceptionMetadataKeys.URL.name(), imageUrl + ":" + tag);
      MdcGlobalContextData mdcGlobalContextData = MdcGlobalContextData.builder().map(imageDataMap).build();
      GlobalContextManager.upsertGlobalContextRecord(mdcGlobalContextData);
      InvalidTagException exception = new InvalidTagException(
          "Could not find tag [" + tag + "] for Repository [" + imageName + "] in the region: [" + region + "]");
      exception.setErrorCode(exception.getClass().getSimpleName());
      exception.setStatusCode(400);
      exception.setServiceName("AmazonECR");
      throw exception;
    }
    return builds.get(0);
  }
}
