/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.api.pcf;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.pcf.CfAppSetupTimeDetails;
import io.harness.delegate.beans.pcf.CfInBuiltVariablesUpdateValues;
import io.harness.delegate.beans.pcf.CfRouteUpdateRequestConfigData;

import software.wings.api.DeploymentInfo;
import software.wings.api.PcfDeploymentInfo;
import software.wings.service.impl.instance.DeploymentInfoExtractor;
import software.wings.sm.StepExecutionSummary;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
@EqualsAndHashCode(callSuper = true)
@OwnedBy(CDP)
public class PcfRouteSwapExecutionSummary extends StepExecutionSummary implements DeploymentInfoExtractor {
  private String organization;
  private String space;
  private CfInBuiltVariablesUpdateValues finalAppDetails;
  private CfRouteUpdateRequestConfigData pcfRouteUpdateRequestConfigData;

  @Override
  public Optional<List<DeploymentInfo>> extractDeploymentInfo() {
    if (pcfRouteUpdateRequestConfigData.isVersioningChanged()) {
      return getFinalAppDetails(finalAppDetails);
    } else if (pcfRouteUpdateRequestConfigData.isDownsizeOldApplication()
        || pcfRouteUpdateRequestConfigData.isNonVersioning()) {
      return applicationDeploymentInfo(pcfRouteUpdateRequestConfigData.getExistingApplicationDetails());
    }
    return Optional.empty();
  }

  private Optional<List<DeploymentInfo>> getFinalAppDetails(CfInBuiltVariablesUpdateValues finalAppDetails) {
    List<DeploymentInfo> pcfDeploymentInfo = new ArrayList<>();
    pcfDeploymentInfo.add(PcfDeploymentInfo.builder()
                              .applicationName(finalAppDetails.getNewAppName())
                              .applicationGuild(finalAppDetails.getNewAppGuid())
                              .build());
    pcfDeploymentInfo.add(PcfDeploymentInfo.builder()
                              .applicationName(finalAppDetails.getOldAppName())
                              .applicationGuild(finalAppDetails.getOldAppGuid())
                              .build());
    return Optional.of(pcfDeploymentInfo);
  }

  private Optional<List<DeploymentInfo>> applicationDeploymentInfo(List<CfAppSetupTimeDetails> details) {
    List<DeploymentInfo> pcfDeploymentInfo = new ArrayList<>();
    details.forEach(existingApp
        -> pcfDeploymentInfo.add(PcfDeploymentInfo.builder()
                                     .applicationName(existingApp.getApplicationName())
                                     .applicationGuild(existingApp.getApplicationGuid())
                                     .build()));
    return Optional.of(pcfDeploymentInfo);
  }
}
