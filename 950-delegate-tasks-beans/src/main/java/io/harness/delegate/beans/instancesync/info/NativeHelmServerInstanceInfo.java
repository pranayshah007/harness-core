/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.instancesync.info;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.task.helm.HelmChartInfo;
import io.harness.k8s.model.HelmVersion;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_FIRST_GEN})
@JsonTypeName("NativeHelmServerInstanceInfo")
@Data
@Builder
@EqualsAndHashCode(callSuper = true)
@OwnedBy(HarnessTeam.CDP)
@RecasterAlias("io.harness.delegate.beans.instancesync.info.NativeHelmServerInstanceInfo")
public class NativeHelmServerInstanceInfo extends ServerInstanceInfo {
  private String podName;
  private String ip;
  private String namespace;
  private String releaseName;
  private HelmChartInfo helmChartInfo;
  private HelmVersion helmVersion;

  @Override
  public String getReleaseKey() {
    if (isEmpty(releaseName) || isEmpty(namespace)) {
      return null;
    }
    return String.format("%s_%s", releaseName, namespace);
  }
}
