/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.ecr;

import static io.harness.rule.OwnerRule.DEEPAK_PUTHRAYA;

import static software.wings.helpers.ext.jenkins.BuildDetails.Builder.aBuildDetails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.EcrConfig;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.helpers.ext.jenkins.BuildDetails.BuildDetailsMetadataKeys;
import software.wings.service.impl.AwsHelperService;
import software.wings.service.intfc.aws.delegate.AwsEcrHelperServiceDelegate;
import software.wings.service.intfc.security.EncryptionService;

import com.amazonaws.services.ecr.model.DescribeRepositoriesRequest;
import com.amazonaws.services.ecr.model.DescribeRepositoriesResult;
import com.amazonaws.services.ecr.model.ImageIdentifier;
import com.amazonaws.services.ecr.model.ListImagesRequest;
import com.amazonaws.services.ecr.model.ListImagesResult;
import com.amazonaws.services.ecr.model.Repository;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class EcrClassicServiceTest extends WingsBaseTest {
  @Mock private AwsHelperService awsHelperService;
  @Mock private AwsEcrHelperServiceDelegate ecrServiceDelegate;
  @Mock private EncryptionService encryptionService;
  @Inject @InjectMocks private EcrClassicService ecrService;

  private EcrConfig awsConfig = EcrConfig.builder().build();

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void shouldGetBuilds() {
    ListImagesResult imagesResult = new ListImagesResult();
    imagesResult.setNextToken(null);
    imagesResult.setImageIds(Lists.newArrayList(null, buildImageIdentifier(null), buildImageIdentifier("latest"),
        buildImageIdentifier("v2"), buildImageIdentifier("v1")));
    when(awsHelperService.listEcrImages(awsConfig, null, new ListImagesRequest().withRepositoryName("imageName")))
        .thenReturn(imagesResult);
    assertThat(ecrService.getBuilds(awsConfig, null, "imageName", 10))
        .hasSize(3)
        .isEqualTo(Lists.newArrayList(buildBuildDetails("latest"), buildBuildDetails("v1"), buildBuildDetails("v2")));
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
    when(awsHelperService.listRepositories(awsConfig, null, new DescribeRepositoriesRequest()))
        .thenReturn(describeRepositoriesResult);
    assertThat(ecrService.listEcrRegistry(awsConfig, null)).containsExactly("repo1", "repo2");
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void shouldGetEcrImageURl() {
    EcrConfig ecrConfig = EcrConfig.builder().ecrUrl("https://aws_account_id.dkr.ecr.region.amazonaws.com").build();
    assertThat(ecrService.getEcrImageUrl(ecrConfig, "imageName"))
        .isEqualTo("aws_account_id.dkr.ecr.region.amazonaws.com/imageName");
  }

  private ImageIdentifier buildImageIdentifier(String tag) {
    ImageIdentifier imageIdentifier = new ImageIdentifier();
    imageIdentifier.setImageTag(tag);
    return imageIdentifier;
  }

  private BuildDetails buildBuildDetails(String tag) {
    Map<String, String> metadata = new HashMap();
    metadata.put(BuildDetailsMetadataKeys.image, "imageUrl:" + tag);
    metadata.put(BuildDetailsMetadataKeys.tag, tag);
    return aBuildDetails().withNumber(tag).withMetadata(metadata).withUiDisplayName("Tag# " + tag).build();
  }
}
