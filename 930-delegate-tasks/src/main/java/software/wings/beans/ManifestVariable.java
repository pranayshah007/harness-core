/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import software.wings.beans.appmanifest.ApplicationManifestSummary;
import software.wings.beans.appmanifest.LastDeployedHelmChartInformation;
import software.wings.beans.appmanifest.ManifestInput;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@OwnedBy(CDC)
@TargetModule(HarnessModule._957_CG_BEANS)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ManifestVariable extends Variable {
  private List<String> workflowIds;
  private String serviceId;
  private String serviceName;
  private String appManifestId;
  private HelmChartInputType inputType;
  private List<ApplicationManifestSummary> applicationManifestSummary;
  private LastDeployedHelmChartInformation lastDeployedHelmChartInfo;

  @Builder
  public ManifestVariable(String name, String description, boolean mandatory, String value, boolean fixed,
      String allowedValues, List<String> allowedList, Map<String, Object> metadata, VariableType type, String serviceId,
      List<ApplicationManifestSummary> applicationManifestSummary, String serviceName, List<String> workflowIds,
      LastDeployedHelmChartInformation lastDeployedHelmChartInformation, HelmChartInputType inputType,
      String appManifestId) {
    super(name, description, mandatory, value, fixed, allowedValues, allowedList, metadata, type);
    this.serviceId = serviceId;
    this.applicationManifestSummary = applicationManifestSummary;
    this.serviceName = serviceName;
    this.workflowIds = workflowIds;
    this.lastDeployedHelmChartInfo = lastDeployedHelmChartInformation;
    this.inputType = inputType;
    this.appManifestId = appManifestId;
  }

  public ManifestInput mapManifestVariableToManifestInput() {
    if (HelmChartInputType.VERSION.equals(inputType)) {
      return ManifestInput.builder().appManifestId(getAppManifestId()).buildNo(getValue()).build();
    }
    return null;
  }
}
