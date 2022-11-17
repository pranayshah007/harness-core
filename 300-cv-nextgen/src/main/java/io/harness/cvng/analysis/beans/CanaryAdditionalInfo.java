/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.analysis.beans;

import static io.harness.cvng.analysis.CVAnalysisConstants.AFTER;
import static io.harness.cvng.analysis.CVAnalysisConstants.BEFORE;
import static io.harness.cvng.analysis.CVAnalysisConstants.CANARY;
import static io.harness.cvng.analysis.CVAnalysisConstants.PRIMARY;

import io.harness.cvng.beans.job.VerificationJobType;

import com.google.common.base.Preconditions;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CanaryAdditionalInfo extends CanaryBlueGreenAdditionalInfo {
  @Override
  public VerificationJobType getType() {
    return VerificationJobType.CANARY;
  }

  public enum CanaryAnalysisType {
    CLASSIC(PRIMARY, CANARY),
    IMPROVISED(BEFORE, AFTER),
    BLUE_GREEN(BEFORE, AFTER);

    private final String primaryInstancesLabel;
    private final String canaryInstancesLabel;

    CanaryAnalysisType(String primaryInstancesLabel, String canaryInstancesLabel) {
      this.primaryInstancesLabel = primaryInstancesLabel;
      this.canaryInstancesLabel = canaryInstancesLabel;
    }

    public String getCanaryInstancesLabel() {
      return this.canaryInstancesLabel;
    }

    public String getPrimaryInstancesLabel() {
      return this.primaryInstancesLabel;
    }
  }

  public void setFieldNames() {
    Preconditions.checkNotNull(this.primary, "Populate control hosts before setting field names");
    Preconditions.checkNotNull(this.canary, "Populate test hosts before setting field names");

    Set<String> controlHosts = this.getPrimary().stream().map(HostSummaryInfo::getHostName).collect(Collectors.toSet());
    Set<String> testHosts = this.getCanary().stream().map(HostSummaryInfo::getHostName).collect(Collectors.toSet());
    testHosts.removeAll(controlHosts);

    CanaryAnalysisType canaryAnalysisType =
        testHosts.size() > 0 ? CanaryAnalysisType.CLASSIC : CanaryAnalysisType.IMPROVISED;
    this.setPrimaryInstancesLabel(canaryAnalysisType.getPrimaryInstancesLabel());
    this.setCanaryInstancesLabel(canaryAnalysisType.getCanaryInstancesLabel());
  }
}
