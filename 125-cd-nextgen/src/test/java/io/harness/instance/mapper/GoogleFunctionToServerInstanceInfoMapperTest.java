/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.instance.mapper;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ALLU_VAMSI;
import static io.harness.rule.OwnerRule.PRAGYESH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.instancesync.info.GoogleFunctionServerInstanceInfo;
import io.harness.delegate.beans.instancesync.mapper.GoogleFunctionToServerInstanceInfoMapper;
import io.harness.delegate.task.googlefunctionbeans.GoogleFunction;
import io.harness.rule.Owner;

import com.google.api.client.util.Lists;
import java.util.Arrays;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDP)
public class GoogleFunctionToServerInstanceInfoMapperTest extends CategoryTest {
  private final String FUNCTION = "fun";
  private final String PROJECT = "cd-play";
  private final String REGION = "us-east1";
  private final String RUN_TIME = "java8";
  private final String INFRA_KEY = "198398123";
  private final String SOURCE = "source";
  private final String REVISION = "function-867";
  private final long TIME = 74987321;

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void toServerInstanceInfoListTest() {
    GoogleFunction.GoogleCloudRunService cloudRunService =
        GoogleFunction.GoogleCloudRunService.builder().revision(REVISION).build();
    GoogleFunction.GoogleCloudRunRevision googleCloudRunRevision =
        GoogleFunction.GoogleCloudRunRevision.builder().revision(REVISION).trafficPercent(100).build();
    GoogleFunction googleFunction = GoogleFunction.builder()
                                        .functionName(FUNCTION)
                                        .runtime(RUN_TIME)
                                        .source(SOURCE)
                                        .updatedTime(TIME)
                                        .cloudRunService(cloudRunService)
                                        .activeCloudRunRevisions(Arrays.asList(googleCloudRunRevision))
                                        .build();
    GoogleFunctionServerInstanceInfo serverInstanceInfo =
        (GoogleFunctionServerInstanceInfo) GoogleFunctionToServerInstanceInfoMapper.toServerInstanceInfo(
            googleFunction, cloudRunService.getRevision(), PROJECT, REGION, INFRA_KEY);
    assertThat(serverInstanceInfo.getRevision()).isEqualTo(REVISION);
    assertThat(serverInstanceInfo.getFunctionName()).isEqualTo(googleFunction.getFunctionName());
    assertThat(serverInstanceInfo.getRunTime()).isEqualTo(googleFunction.getRuntime());
    assertThat(serverInstanceInfo.getSource()).isEqualTo(SOURCE);
    assertThat(serverInstanceInfo.getUpdatedTime()).isEqualTo(TIME);
    assertThat(serverInstanceInfo.getProject()).isEqualTo(PROJECT);
    assertThat(serverInstanceInfo.getRegion()).isEqualTo(REGION);
    assertThat(serverInstanceInfo.getInfraStructureKey()).isEqualTo(INFRA_KEY);
  }

  @Test
  @Owner(developers = PRAGYESH)
  @Category(UnitTests.class)
  public void toGenOneServerInstanceInfoListTest() {
    GoogleFunction googleFunction = GoogleFunction.builder()
                                        .functionName(FUNCTION)
                                        .runtime(RUN_TIME)
                                        .source(SOURCE)
                                        .updatedTime(TIME)
                                        .cloudRunService(GoogleFunction.GoogleCloudRunService.builder().build())
                                        .activeCloudRunRevisions(Lists.newArrayList())
                                        .build();
    GoogleFunctionServerInstanceInfo serverInstanceInfo =
        (GoogleFunctionServerInstanceInfo) GoogleFunctionToServerInstanceInfoMapper.toGenOneServerInstanceInfo(
            googleFunction, PROJECT, REGION, INFRA_KEY);
    assertThat(serverInstanceInfo.getRevision()).isEqualTo("LATEST");
    assertThat(serverInstanceInfo.getFunctionName()).isEqualTo(googleFunction.getFunctionName());
    assertThat(serverInstanceInfo.getRunTime()).isEqualTo(googleFunction.getRuntime());
    assertThat(serverInstanceInfo.getSource()).isEqualTo(SOURCE);
    assertThat(serverInstanceInfo.getUpdatedTime()).isEqualTo(TIME);
    assertThat(serverInstanceInfo.getProject()).isEqualTo(PROJECT);
    assertThat(serverInstanceInfo.getRegion()).isEqualTo(REGION);
    assertThat(serverInstanceInfo.getInfraStructureKey()).isEqualTo(INFRA_KEY);
  }
}
