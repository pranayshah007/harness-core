/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.mappers;

import static io.harness.rule.OwnerRule.ABHISHEK;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.artifact.ArtifactMetadataKeys;
import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.beans.ArtifactMetaInfo;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.artifacts.gar.GarDelegateResponse;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

public class GarRequestResponseMapperTest {
  private static final String SHA_V2 = "sHAV2";
  private static final String SHA = "sHA";
  private static final String NUMBER = "number";

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void toGarResponseTest() {
    BuildDetailsInternal buildDetailsInternal = BuildDetailsInternal.builder().build();
    ArtifactMetaInfo artifactMetaInfo = ArtifactMetaInfo.builder().shaV2(SHA_V2).sha(SHA).build();
    GarDelegateResponse garDelegateResponse =
        GarRequestResponseMapper.toGarResponse(buildDetailsInternal, null, artifactMetaInfo);
    assertThat(garDelegateResponse.getBuildDetails().getMetadata().get(ArtifactMetadataKeys.SHA)).isEqualTo(SHA);
    assertThat(garDelegateResponse.getBuildDetails().getMetadata().get(ArtifactMetadataKeys.SHAV2)).isEqualTo(SHA_V2);
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void toGarResponseTest_withoutMetaInfo() {
    BuildDetailsInternal buildDetailsInternal = BuildDetailsInternal.builder().number(NUMBER).build();
    GarDelegateResponse garDelegateResponse = GarRequestResponseMapper.toGarResponse(buildDetailsInternal, null, null);
    assertThat(garDelegateResponse.getVersion()).isEqualTo(NUMBER);
  }
}