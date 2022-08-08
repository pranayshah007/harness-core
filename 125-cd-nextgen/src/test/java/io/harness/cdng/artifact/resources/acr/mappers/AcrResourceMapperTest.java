/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.artifact.resources.acr.mappers;

import static io.harness.rule.OwnerRule.MLUKIC;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifact.ArtifactMetadataKeys;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.azure.AcrBuildDetailsDTO;
import io.harness.delegate.beans.azure.AcrResponseDTO;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.artifacts.azure.AcrArtifactDelegateResponse;
import io.harness.delegate.task.artifacts.response.ArtifactBuildDetailsNG;
import io.harness.rule.Owner;

import com.google.common.collect.Lists;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDP)
public class AcrResourceMapperTest extends CategoryTest {
  private final String SUBSCRIPTION_ID = "123456-6543-3456-654321";
  private final String REGISTRY = "testreg";
  private final String REGISTRY_URL = format("%s.azurecr.io", REGISTRY);
  private final String REPOSITORY = "library/testapp";
  private final String TAG = "1.0";
  private final String TAG_LABEL = "Tag#";
  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testToAcrResponse() {
    String imagePath = format("%s/%s:%s", REGISTRY_URL, REPOSITORY, TAG);

    Map<String, String> metadata = new HashMap<>();
    metadata.put(ArtifactMetadataKeys.IMAGE, imagePath);
    metadata.put(ArtifactMetadataKeys.TAG, TAG);
    metadata.put(ArtifactMetadataKeys.REGISTRY_HOSTNAME, REGISTRY_URL);

    List<AcrArtifactDelegateResponse> acrArtifactDelegateResponses =
        Lists.newArrayList(AcrArtifactDelegateResponse.builder()
                               .subscription(SUBSCRIPTION_ID)
                               .registry(REGISTRY)
                               .repository(REPOSITORY)
                               .tag(TAG)
                               .buildDetails(ArtifactBuildDetailsNG.builder()
                                                 .number(TAG)
                                                 .uiDisplayName(format("%s %s", TAG_LABEL, TAG))
                                                 .buildUrl(imagePath)
                                                 .metadata(metadata)
                                                 .build())
                               .sourceType(ArtifactSourceType.ACR)
                               .build());

    AcrResponseDTO acrResponseDTO = AcrResourceMapper.toAcrResponse(acrArtifactDelegateResponses);
    assertThat(acrResponseDTO).isNotNull();
    assertThat(acrResponseDTO.getBuildDetailsList()).isNotEmpty();
    assertThat(acrResponseDTO.getBuildDetailsList().get(0))
        .isEqualTo(AcrBuildDetailsDTO.builder()
                       .tag(TAG)
                       .buildUrl(imagePath)
                       .metadata(metadata)
                       .repository(REPOSITORY)
                       .build());
  }
}
