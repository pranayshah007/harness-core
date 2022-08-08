/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.ami;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.helpers.ext.jenkins.BuildDetails.Builder.aBuildDetails;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.AwsConfig;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.service.impl.AwsHelperService;

import com.amazonaws.services.ec2.model.DescribeImagesRequest;
import com.amazonaws.services.ec2.model.DescribeImagesResult;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.Image;
import com.amazonaws.services.ec2.model.Tag;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by sgurubelli on 12/14/17.
 */
@OwnedBy(CDC)
@Singleton
@Slf4j
public class AmiServiceImpl implements AmiService {
  private static final String AMI_RESOURCE_FILTER_PREFIX = "ami-";
  private static final long AMI_MAX_RESULTS = 1000;
  @Inject private AwsHelperService awsHelperService;

  @Override
  public List<BuildDetails> getBuilds(AwsConfig awsConfig, List<EncryptedDataDetail> encryptionDetails, String region,
      Map<String, List<String>> tags, Map<String, String> filterMap, int maxNumberOfBuilds) {
    log.info("Retrieving images from Aws");
    List<BuildDetails> buildDetails = new ArrayList<>();
    List<Filter> filters = getFilters(tags, filterMap);
    DescribeImagesRequest describeImagesRequest = new DescribeImagesRequest().withFilters(filters);
    DescribeImagesResult describeImagesResult;
    describeImagesResult =
        awsHelperService.desribeEc2Images(awsConfig, encryptionDetails, region, describeImagesRequest);
    log.info("Sorting on creation time");
    Collections.sort(
        describeImagesResult.getImages(), Collections.reverseOrder(Comparator.comparing(Image::getCreationDate)));

    int numberOfNewImages = describeImagesResult.getImages().size();
    List<Image> limitedImages = describeImagesResult.getImages()
                                    .stream()
                                    .filter(image -> image != null && isNotBlank(image.getName()))
                                    .limit(AMI_MAX_RESULTS)
                                    .collect(Collectors.toList());

    int fetchedImages = limitedImages.size();

    if (numberOfNewImages != fetchedImages) {
      log.warn("Fetching top {} images only due to limit constrained ", AMI_MAX_RESULTS);
    }
    Collections.reverse(limitedImages);

    limitedImages.forEach(image -> constructBuildDetails(buildDetails, image));
    if (buildDetails.isEmpty()) {
      log.info("No images found matching with the given Region {}, and filters {}", region, filters);
    } else {
      log.info("Images found of size {}", buildDetails.size());
    }
    return buildDetails;
  }

  private void constructBuildDetails(List<BuildDetails> buildDetails, Image image) {
    // filtering out tags with "." in the keys as these lead to an exception when saving to mongo
    Map<String, String> metadata = image.getTags()
                                       .stream()
                                       .filter(tag -> !tag.getKey().contains("."))
                                       .collect(Collectors.toMap(Tag::getKey, Tag::getValue));
    metadata.put("ownerId", image.getOwnerId());
    metadata.put("imageType", image.getImageType());
    buildDetails.add(aBuildDetails()
                         .withNumber(image.getName())
                         .withRevision(image.getImageId())
                         .withUiDisplayName("Image: " + image.getName())
                         .withMetadata(metadata)
                         .build());
  }

  protected List<Filter> getFilters(Map<String, List<String>> tags, Map<String, String> filterMap) {
    List<Filter> filters = new ArrayList<>();
    filters.add(new Filter("is-public").withValues("false"));
    filters.add(new Filter("state").withValues("available"));

    if (isNotEmpty(tags)) {
      tags.keySet().forEach(key -> filters.add(new Filter("tag:" + key, new ArrayList<>(tags.get(key)))));
    }

    if (isNotEmpty(filterMap)) {
      filterMap.entrySet()
          .stream()
          .filter(entry -> isNotBlank(entry.getKey()))
          .filter(entry -> isNotBlank(entry.getValue()))
          .filter(entry -> entry.getKey().startsWith(AMI_RESOURCE_FILTER_PREFIX))
          .forEach(entry -> filters.add(new Filter(entry.getKey().substring(4)).withValues(entry.getValue())));
    }
    return filters;
  }
}
