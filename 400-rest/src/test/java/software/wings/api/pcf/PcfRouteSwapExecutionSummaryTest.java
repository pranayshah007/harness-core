/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.api.pcf;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ALLU_VAMSI;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.pcf.CfAppSetupTimeDetails;
import io.harness.delegate.beans.pcf.CfRouteUpdateRequestConfigData;
import io.harness.rule.Owner;

import software.wings.api.DeploymentInfo;
import software.wings.api.PcfDeploymentInfo;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(CDP)
public class PcfRouteSwapExecutionSummaryTest extends CategoryTest {
  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void extractDeploymentInfoTest() {
    List<CfAppSetupTimeDetails> existingApplicationDetails = Arrays.asList(CfAppSetupTimeDetails.builder()
                                                                               .applicationName("AppName")
                                                                               .applicationGuid("uuid")
                                                                               .initialInstanceCount(2)
                                                                               .build());
    CfRouteUpdateRequestConfigData cfRouteUpdateRequestConfigData =
        CfRouteUpdateRequestConfigData.builder()
            .nonVersioning(true)
            .existingApplicationDetails(existingApplicationDetails)
            .build();
    PcfRouteSwapExecutionSummary pcfRouteSwapExecutionSummary =
        PcfRouteSwapExecutionSummary.builder().pcfRouteUpdateRequestConfigData(cfRouteUpdateRequestConfigData).build();
    Optional<List<DeploymentInfo>> deploymentInfoList = pcfRouteSwapExecutionSummary.extractDeploymentInfo();
    assertThat(deploymentInfoList.get().size()).isEqualTo(1);
    PcfDeploymentInfo pcfDeploymentInfo = (PcfDeploymentInfo) deploymentInfoList.get().get(0);
    assertThat(pcfDeploymentInfo.getApplicationName())
        .isEqualTo(existingApplicationDetails.get(0).getApplicationName());
    assertThat(pcfDeploymentInfo.getApplicationGuild())
        .isEqualTo(existingApplicationDetails.get(0).getApplicationGuid());
  }
}
