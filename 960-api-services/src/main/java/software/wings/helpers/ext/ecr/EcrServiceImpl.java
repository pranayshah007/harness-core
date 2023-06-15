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
import io.harness.artifacts.gar.service.GARUtils;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.beans.ArtifactMetaInfo;
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
import com.amazonaws.services.ecr.model.ImageDetail;
import com.amazonaws.services.ecr.model.ImageIdentifier;
import com.amazonaws.services.ecr.model.ListImagesRequest;
import com.amazonaws.services.ecr.model.ListImagesResult;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * Created by brett on 7/15/17
 */
@OwnedBy(CDC)
@Singleton
@Slf4j
public class EcrServiceImpl implements EcrService {
  @Inject private AwsApiHelperService awsApiHelperService;

  @VisibleForTesting
  List<BuildDetailsInternal> getBuildsFallback(
      AwsInternalConfig awsConfig, String registryId, String imageUrl, String region, String imageName) {
    List<BuildDetailsInternal> buildDetailsInternals = new ArrayList<>();
    try {
      ListImagesResult listImageResult;
      ListImagesRequest listImagesRequest = new ListImagesRequest().withRepositoryName(imageName);
      if (StringUtils.isNotBlank(registryId)) {
        listImagesRequest.withRegistryId(registryId);
      }
      do {
        listImageResult = awsApiHelperService.listEcrImages(awsConfig, region, listImagesRequest);
        listImageResult.getImageIds()
            .stream()
            .filter(imageIdentifier -> imageIdentifier != null && isNotEmpty(imageIdentifier.getImageTag()))
            .forEach(imageIdentifier -> {
              Map<String, String> metadata = new HashMap<>();
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
  public List<BuildDetailsInternal> getBuilds(AwsInternalConfig awsConfig, String registryId, String imageUrl,
      String region, String imageName, int maxNumberOfImagesPerPage) {
    List<BuildDetailsInternal> buildDetailsInternals = new ArrayList<>();
    try {
      log.debug("GetBuilds for {} in region {} with maxPageSize {}", imageName, region, maxNumberOfImagesPerPage);
      DescribeImagesResult describeImagesResult;
      DescribeImagesRequest describeImagesRequest =
          new DescribeImagesRequest().withRepositoryName(imageName).withMaxResults(maxNumberOfImagesPerPage);
      if (StringUtils.isNotBlank(registryId)) {
        describeImagesRequest.setRegistryId(registryId);
      }
      int pageCounter = 0;
      do {
        log.debug("Making a describeImages API call page request no. {}", ++pageCounter);
        describeImagesResult = awsApiHelperService.describeEcrImages(awsConfig, region, describeImagesRequest);
        log.debug(
            "DescribeImages API page result got back with {} images", describeImagesResult.getImageDetails().size());

        describeImagesResult.getImageDetails()
            .stream()
            .filter(imageIdentifier -> imageIdentifier != null && isNotEmpty(imageIdentifier.getImageTags()))
            .forEach(imageIdentifier -> {
              imageIdentifier.getImageTags().stream().filter(EmptyPredicate::isNotEmpty).forEach(image -> {
                Map<String, String> metadata = new HashMap<>();
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
        log.debug("Finished processing page no. {} for describeImages API call", pageCounter);
      } while (describeImagesRequest.getNextToken() != null);
    } catch (Exception e) {
      return getBuildsFallback(awsConfig, registryId, imageUrl, region, imageName);
    }
    log.debug("GetBuilds describeImages API has done with the fetch of {} tags", buildDetailsInternals.size());
    return buildDetailsInternals;
  }

  @Override
  public BuildDetailsInternal getLastSuccessfulBuild(AwsInternalConfig awsConfig, String imageName) {
    return null;
  }

  @Override
  public boolean verifyRepository(
      AwsInternalConfig awsConfig, String region, String registryId, String repositoryName) {
    return listEcrRegistry(awsConfig, region, registryId).contains(repositoryName);
  }

  @Override
  public List<String> listRegions(AwsInternalConfig awsConfig) {
    return awsApiHelperService.listRegions(awsConfig);
  }

  @Override
  public List<String> listEcrRegistry(AwsInternalConfig awsConfig, String region, String registryId) {
    List<String> repoNames = new ArrayList<>();
    DescribeRepositoriesRequest describeRepositoriesRequest = new DescribeRepositoriesRequest();
    DescribeRepositoriesResult describeRepositoriesResult;
    if (StringUtils.isNotBlank(registryId)) {
      describeRepositoriesRequest.setRegistryId(registryId);
    }
    do {
      describeRepositoriesResult = awsApiHelperService.listRepositories(awsConfig, describeRepositoriesRequest, region);
      describeRepositoriesResult.getRepositories().forEach(repository -> repoNames.add(repository.getRepositoryName()));
      describeRepositoriesRequest.setNextToken(describeRepositoriesResult.getNextToken());
    } while (describeRepositoriesRequest.getNextToken() != null);

    return repoNames;
  }

  @Override
  public List<Map<String, String>> getLabels(
      AwsInternalConfig awsConfig, String registryId, String imageName, String region, List<String> tags) {
    return Collections.singletonList(awsApiHelperService.fetchLabels(awsConfig, registryId, imageName, region, tags));
  }

  private String getSHA(DescribeImagesResult describeImagesResult) {
    if (describeImagesResult != null && EmptyPredicate.isNotEmpty(describeImagesResult.getImageDetails())) {
      ImageDetail imageDetail = describeImagesResult.getImageDetails().get(0);
      if (imageDetail != null) {
        return imageDetail.getImageDigest();
      }
    }
    return null;
  }

  @Override
  public BuildDetailsInternal getLastSuccessfulBuildFromRegex(AwsInternalConfig awsInternalConfig, String registryId,
      String imageUrl, String region, String imageName, String tagRegex) {
    List<BuildDetailsInternal> builds =
        getBuilds(awsInternalConfig, registryId, imageUrl, region, imageName, MAX_NO_OF_IMAGES);

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

    return verifyBuildNumber(
        awsInternalConfig, registryId, imageUrl, region, imageName, buildsResponse.get(0).getNumber());
  }

  @Override
  public boolean verifyImageName(
      AwsInternalConfig awsConfig, String registryId, String imageUrl, String region, String imageName) {
    try {
      getBuilds(awsConfig, registryId, imageUrl, region, imageName, 1);
    } catch (Exception e) {
      return false;
    }
    return true;
  }

  @Override
  public boolean validateCredentials(
      AwsInternalConfig awsConfig, String registryId, String imageUrl, String region, String imageName) {
    try {
      getBuilds(awsConfig, registryId, imageUrl, region, imageName, 1);
    } catch (Exception e) {
      return false;
    }
    return true;
  }

  @Override
  public BuildDetailsInternal verifyBuildNumber(AwsInternalConfig awsInternalConfig, String registryId, String imageUrl,
      String region, String imageName, String tag) {
    boolean isSHA = GARUtils.isSHA(tag);
    ImageIdentifier imageIdentifier;
    if (isSHA) {
      imageIdentifier = new ImageIdentifier().withImageDigest(tag);
    } else {
      imageIdentifier = new ImageIdentifier().withImageTag(tag);
    }
    DescribeImagesRequest imagesRequest =
        new DescribeImagesRequest().withRepositoryName(imageName).withImageIds(imageIdentifier);
    if (StringUtils.isNotBlank(registryId)) {
      imagesRequest.setRegistryId(registryId);
    }
    DescribeImagesResult describeImagesResult =
        awsApiHelperService.describeEcrImages(awsInternalConfig, region, imagesRequest);
    String sha = getSHA(describeImagesResult);
    if (EmptyPredicate.isEmpty(sha)) {
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
    Map<String, String> label = null;
    List<Map<String, String>> labels =
        getLabels(awsInternalConfig, registryId, imageName, region, Collections.singletonList(tag));
    if (EmptyPredicate.isNotEmpty(labels)) {
      label = labels.get(0);
    }
    ArtifactMetaInfo artifactMetaInfo = ArtifactMetaInfo.builder().sha(sha).shaV2(sha).labels(label).build();
    Map<String, String> metadata = new HashMap<>();
    metadata.put(BuildDetailsInternalMetadataKeys.image, imageUrl + (isSHA ? "@" : ":") + tag);
    metadata.put(BuildDetailsInternalMetadataKeys.tag, tag);
    Date date = describeImagesResult.getImageDetails().get(0).getImagePushedAt();
    return BuildDetailsInternal.builder()
        .number(tag)
        .metadata(metadata)
        .uiDisplayName("Tag# " + tag)
        .artifactMetaInfo(artifactMetaInfo)
        .imagePushedAt(date)
        .build();
  }
}
