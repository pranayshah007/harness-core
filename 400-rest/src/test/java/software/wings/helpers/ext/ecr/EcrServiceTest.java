/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.ecr;

import static io.harness.rule.OwnerRule.ABHISHEK;
import static io.harness.rule.OwnerRule.DEEPAK_PUTHRAYA;
import static io.harness.rule.OwnerRule.RAFAEL;

import static software.wings.helpers.ext.jenkins.BuildDetails.Builder.aBuildDetails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.aws.beans.AwsInternalConfig;
import io.harness.beans.ArtifactMetaInfo;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.AwsConfig;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.helpers.ext.jenkins.BuildDetails.BuildDetailsMetadataKeys;
import software.wings.service.impl.AwsApiHelperService;
import software.wings.service.intfc.aws.delegate.AwsEcrHelperServiceDelegate;
import software.wings.service.mappers.artifact.ArtifactConfigMapper;
import software.wings.service.mappers.artifact.AwsConfigToInternalMapper;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.ecr.model.DescribeImagesRequest;
import com.amazonaws.services.ecr.model.DescribeImagesResult;
import com.amazonaws.services.ecr.model.DescribeRepositoriesRequest;
import com.amazonaws.services.ecr.model.DescribeRepositoriesResult;
import com.amazonaws.services.ecr.model.ImageDetail;
import com.amazonaws.services.ecr.model.ImageIdentifier;
import com.amazonaws.services.ecr.model.ListImagesRequest;
import com.amazonaws.services.ecr.model.ListImagesResult;
import com.amazonaws.services.ecr.model.Repository;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class EcrServiceTest extends WingsBaseTest {
  @Mock private AwsEcrHelperServiceDelegate ecrServiceDelegate;
  @Inject @InjectMocks private EcrService ecrService;
  @Inject @InjectMocks private EcrServiceImpl ecrServiceImpl;
  @Mock private AwsApiHelperService awsApiHelperService;

  private AwsConfig awsConfig = AwsConfig.builder().build();
  private static final String SHA = "sha256:12132342";
  private static final Map<String, String> LABEL = ImmutableMap.<String, String>builder().put("key1", "val1").build();
  private static final String REGISTRY_ID = "registryId";
  private static final String IMAGE_NAME = "imageName";
  private static final String LATEST = "latest";
  private static final String IMAGE = "image";
  private static final String TAG = "tag";

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void shouldGetLabels() {
    when(awsApiHelperService.fetchLabels(eq(AwsConfigToInternalMapper.toAwsInternalConfig(awsConfig)), eq(REGISTRY_ID),
             eq(IMAGE_NAME), eq(Regions.US_EAST_1.getName()), anyList()))
        .thenReturn(ImmutableMap.<String, String>builder().put("key1", "val1").build());
    assertThat(ecrService.getLabels(AwsConfigToInternalMapper.toAwsInternalConfig(awsConfig), REGISTRY_ID, IMAGE_NAME,
                   Regions.US_EAST_1.getName(), Lists.newArrayList("tag1")))
        .hasSize(1)
        .isEqualTo(Collections.singletonList(ImmutableMap.<String, String>builder().put("key1", "val1").build()));
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void shouldGetBuilds() throws ParseException {
    String region = Regions.US_EAST_1.getName();
    when(ecrServiceDelegate.getEcrImageUrl(awsConfig, null, region, IMAGE_NAME)).thenReturn("imageUrl");
    DescribeImagesResult imagesResult = new DescribeImagesResult();
    imagesResult.setNextToken(null);
    imagesResult.setImageDetails(
        Lists.newArrayList(null, buildImageDetails(Arrays.asList(LATEST), "2023-02-02T16:48:55-08:00"),
            buildImageDetails(Arrays.asList("stable-perl"), "022-11-22T23:03:17-08:00"),
            buildImageDetails(Arrays.asList("stable"), "2022-11-22T04:18:35-08:00"),
            buildImageDetails(Arrays.asList("v1", "v2", "v3"), "2023-02-02T16:45:50-08:00")));
    when(awsApiHelperService.describeEcrImages(AwsConfigToInternalMapper.toAwsInternalConfig(awsConfig), region,
             new DescribeImagesRequest().withRepositoryName(IMAGE_NAME).withRegistryId(REGISTRY_ID)))
        .thenReturn(imagesResult);
    assertThat(ecrService
                   .getBuilds(AwsConfigToInternalMapper.toAwsInternalConfig(awsConfig), REGISTRY_ID, null, region,
                       IMAGE_NAME, 10)
                   .stream()
                   .map(ArtifactConfigMapper::toBuildDetails)
                   .collect(Collectors.toList()))
        .hasSize(6)
        .isEqualTo(Lists.newArrayList(buildBuildDetails(LATEST), buildBuildDetails("stable-perl"),
            buildBuildDetails("stable"), buildBuildDetails("v1"), buildBuildDetails("v2"), buildBuildDetails("v3")));
  }

  @Test
  @Owner(developers = RAFAEL)
  @Category(UnitTests.class)
  public void shouldGetBuildsFallback() throws ParseException {
    String region = Regions.US_EAST_1.getName();
    when(ecrServiceDelegate.getEcrImageUrl(awsConfig, null, region, IMAGE_NAME)).thenReturn("imageUrl");
    ListImagesResult imagesResult = new ListImagesResult();
    imagesResult.setNextToken(null);
    imagesResult.setImageIds(Lists.newArrayList(null, buildImageIdentifier(null), buildImageIdentifier(LATEST),
        buildImageIdentifier("v2"), buildImageIdentifier("v1")));
    when(awsApiHelperService.listEcrImages(AwsConfigToInternalMapper.toAwsInternalConfig(awsConfig), region,
             new ListImagesRequest().withRepositoryName(IMAGE_NAME).withRegistryId(REGISTRY_ID)))
        .thenReturn(imagesResult);
    assertThat(ecrServiceImpl
                   .getBuildsFallback(
                       AwsConfigToInternalMapper.toAwsInternalConfig(awsConfig), REGISTRY_ID, null, region, IMAGE_NAME)
                   .stream()
                   .map(ArtifactConfigMapper::toBuildDetails)
                   .collect(Collectors.toList()))
        .hasSize(3)
        .isEqualTo(Lists.newArrayList(buildBuildDetails(LATEST), buildBuildDetails("v1"), buildBuildDetails("v2")));
  }

  @Test
  @Owner(developers = RAFAEL)
  @Category(UnitTests.class)
  public void shouldGetBuildsWithFallback() throws ParseException {
    String region = Regions.US_EAST_1.getName();
    when(ecrServiceDelegate.getEcrImageUrl(awsConfig, null, region, IMAGE_NAME)).thenReturn("imageUrl");
    ListImagesResult imagesResult = new ListImagesResult();
    imagesResult.setNextToken(null);
    imagesResult.setImageIds(Lists.newArrayList(null, buildImageIdentifier(null), buildImageIdentifier(LATEST),
        buildImageIdentifier("stable"), buildImageIdentifier("stable-perl"), buildImageIdentifier("v1"),
        buildImageIdentifier("v2"), buildImageIdentifier("v3")));
    when(awsApiHelperService.describeEcrImages(AwsConfigToInternalMapper.toAwsInternalConfig(awsConfig), region,
             new DescribeImagesRequest().withRepositoryName(IMAGE_NAME)))
        .thenReturn(null);
    when(awsApiHelperService.listEcrImages(AwsConfigToInternalMapper.toAwsInternalConfig(awsConfig), region,
             new ListImagesRequest().withRepositoryName(IMAGE_NAME)))
        .thenReturn(imagesResult);
    assertThat(
        ecrService
            .getBuilds(AwsConfigToInternalMapper.toAwsInternalConfig(awsConfig), null, null, region, IMAGE_NAME, 10)
            .stream()
            .map(ArtifactConfigMapper::toBuildDetails)
            .collect(Collectors.toList()))
        .hasSize(6)
        .isEqualTo(
            Lists.newArrayList(buildBuildDetails(LATEST), buildBuildDetails("stable"), buildBuildDetails("stable-perl"),
                buildBuildDetails("v1"), buildBuildDetails("v2"), buildBuildDetails("v3")));
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testListEcrRegistry() {
    DescribeRepositoriesResult describeRepositoriesResult = new DescribeRepositoriesResult();
    describeRepositoriesResult.setRepositories(Lists.newArrayList("repo1", "repo2")
                                                   .stream()
                                                   .map(repoName -> {
                                                     Repository repo = new Repository();
                                                     repo.setRepositoryName(repoName);
                                                     return repo;
                                                   })
                                                   .collect(Collectors.toList()));
    describeRepositoriesResult.setNextToken(null);
    when(awsApiHelperService.listRepositories(AwsConfigToInternalMapper.toAwsInternalConfig(awsConfig),
             new DescribeRepositoriesRequest().withRegistryId(REGISTRY_ID), Regions.US_EAST_1.getName()))
        .thenReturn(describeRepositoriesResult);
    assertThat(ecrService.listEcrRegistry(
                   AwsConfigToInternalMapper.toAwsInternalConfig(awsConfig), Regions.US_EAST_1.getName(), REGISTRY_ID))
        .containsExactly("repo1", "repo2");
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testVerifyRepository() {
    DescribeRepositoriesResult describeRepositoriesResult = new DescribeRepositoriesResult();
    describeRepositoriesResult.setRepositories(Lists.newArrayList("repo1", "repo2")
                                                   .stream()
                                                   .map(repoName -> {
                                                     Repository repo = new Repository();
                                                     repo.setRepositoryName(repoName);
                                                     return repo;
                                                   })
                                                   .collect(Collectors.toList()));
    describeRepositoriesResult.setNextToken(null);
    when(awsApiHelperService.listRepositories(AwsConfigToInternalMapper.toAwsInternalConfig(awsConfig),
             new DescribeRepositoriesRequest().withRegistryId(REGISTRY_ID), Regions.US_EAST_1.getName()))
        .thenReturn(describeRepositoriesResult);
    assertThat(ecrService.verifyRepository(AwsConfigToInternalMapper.toAwsInternalConfig(awsConfig),
                   Regions.US_EAST_1.getName(), REGISTRY_ID, "repo1"))
        .isTrue();
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testVerifyBuildNumber() {
    ImageDetail imageDetail = new ImageDetail();
    imageDetail.setImageDigest(SHA);
    Date date = new Date(3l);
    imageDetail.setImagePushedAt(date);
    DescribeImagesResult describeImagesResult = new DescribeImagesResult();
    describeImagesResult.setImageDetails(Collections.singletonList(imageDetail));
    AwsInternalConfig awsInternalConfig = AwsConfigToInternalMapper.toAwsInternalConfig(awsConfig);
    DescribeImagesRequest describeImagesRequest = new DescribeImagesRequest()
                                                      .withRegistryId(REGISTRY_ID)
                                                      .withRepositoryName(IMAGE_NAME)
                                                      .withImageIds(new ImageIdentifier().withImageTag(LATEST));
    when(awsApiHelperService.describeEcrImages(
             eq(awsInternalConfig), eq(Regions.US_EAST_1.getName()), eq(describeImagesRequest)))
        .thenReturn(describeImagesResult);
    when(awsApiHelperService.fetchLabels(
             eq(awsInternalConfig), eq(REGISTRY_ID), eq(IMAGE_NAME), eq(Regions.US_EAST_1.getName()), anyList()))
        .thenReturn(LABEL);
    BuildDetailsInternal buildDetailsInternal = ecrService.verifyBuildNumber(
        awsInternalConfig, REGISTRY_ID, IMAGE_NAME, Regions.US_EAST_1.getName(), IMAGE_NAME, LATEST);
    assertThat(buildDetailsInternal.getNumber()).isEqualTo(LATEST);
    ArtifactMetaInfo artifactMetaInfo = ArtifactMetaInfo.builder().sha(SHA).shaV2(SHA).labels(LABEL).build();
    assertThat(buildDetailsInternal.getArtifactMetaInfo()).isEqualTo(artifactMetaInfo);
    assertThat(buildDetailsInternal.getMetadata().get(IMAGE)).isEqualTo("imageName:latest");
    assertThat(buildDetailsInternal.getMetadata().get(TAG)).isEqualTo(LATEST);
    assertThat(buildDetailsInternal.getUiDisplayName()).isEqualTo("Tag# latest");
    assertThat(buildDetailsInternal.getImagePushedAt()).isEqualTo(date);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testVerifyBuildNumber_SHA() {
    ImageDetail imageDetail = new ImageDetail();
    imageDetail.setImageDigest(SHA);
    Date date = new Date(3l);
    imageDetail.setImagePushedAt(date);
    DescribeImagesResult describeImagesResult = new DescribeImagesResult();
    describeImagesResult.setImageDetails(Collections.singletonList(imageDetail));
    AwsInternalConfig awsInternalConfig = AwsConfigToInternalMapper.toAwsInternalConfig(awsConfig);
    DescribeImagesRequest describeImagesRequest = new DescribeImagesRequest()
                                                      .withRegistryId(REGISTRY_ID)
                                                      .withRepositoryName(IMAGE_NAME)
                                                      .withImageIds(new ImageIdentifier().withImageDigest(SHA));
    when(awsApiHelperService.describeEcrImages(
             eq(awsInternalConfig), eq(Regions.US_EAST_1.getName()), eq(describeImagesRequest)))
        .thenReturn(describeImagesResult);
    when(awsApiHelperService.fetchLabels(
             eq(awsInternalConfig), eq(REGISTRY_ID), eq(IMAGE_NAME), eq(Regions.US_EAST_1.getName()), anyList()))
        .thenReturn(LABEL);
    BuildDetailsInternal buildDetailsInternal = ecrService.verifyBuildNumber(
        awsInternalConfig, REGISTRY_ID, IMAGE_NAME, Regions.US_EAST_1.getName(), IMAGE_NAME, SHA);
    assertThat(buildDetailsInternal.getNumber()).isEqualTo(SHA);
    ArtifactMetaInfo artifactMetaInfo = ArtifactMetaInfo.builder().sha(SHA).shaV2(SHA).labels(LABEL).build();
    assertThat(buildDetailsInternal.getArtifactMetaInfo()).isEqualTo(artifactMetaInfo);
    assertThat(buildDetailsInternal.getMetadata().get(IMAGE)).isEqualTo("imageName@sha256:12132342");
    assertThat(buildDetailsInternal.getMetadata().get(TAG)).isEqualTo(SHA);
    assertThat(buildDetailsInternal.getUiDisplayName()).isEqualTo("Tag# sha256:12132342");
    assertThat(buildDetailsInternal.getImagePushedAt()).isEqualTo(date);
  }

  private ImageIdentifier buildImageIdentifier(String tag) {
    ImageIdentifier imageIdentifier = new ImageIdentifier();
    imageIdentifier.setImageTag(tag);
    return imageIdentifier;
  }

  private ImageDetail buildImageDetails(List<String> tags, String date) throws ParseException {
    ImageDetail imageDetail = new ImageDetail();
    imageDetail.setImageTags(tags);
    imageDetail.setImagePushedAt(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(date));
    return imageDetail;
  }

  private BuildDetails buildBuildDetails(String tag) {
    Map<String, String> metadata = new HashMap();
    metadata.put(BuildDetailsMetadataKeys.image, "imageUrl:" + tag);
    metadata.put(BuildDetailsMetadataKeys.tag, tag);
    return aBuildDetails().withNumber(tag).withMetadata(metadata).withUiDisplayName("Tag# " + tag).build();
  }
}
