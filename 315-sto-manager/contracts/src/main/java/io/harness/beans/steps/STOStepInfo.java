/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.steps;

import static io.harness.annotations.dev.HarnessTeam.STO;

import io.harness.advisers.rollback.OnFailRollbackParameters;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.nodes.security.DockerContentTrustScanNode;
import io.harness.beans.steps.nodes.security.ExternalScanNode;
import io.harness.beans.steps.stepinfo.RunStepInfo;
import io.harness.beans.steps.stepinfo.SecurityStepInfo;
import io.harness.beans.steps.stepinfo.security.AquaTrivyStepInfo;
import io.harness.beans.steps.stepinfo.security.AwsEcrStepInfo;
import io.harness.beans.steps.stepinfo.security.BanditStepInfo;
import io.harness.beans.steps.stepinfo.security.BlackDuckStepInfo;
import io.harness.beans.steps.stepinfo.security.BrakemanStepInfo;
import io.harness.beans.steps.stepinfo.security.BurpStepInfo;
import io.harness.beans.steps.stepinfo.security.CheckmarxStepInfo;
import io.harness.beans.steps.stepinfo.security.DataTheoremStepInfo;
import io.harness.beans.steps.stepinfo.security.FortifyOnDemandStepInfo;
import io.harness.beans.steps.stepinfo.security.GrypeStepInfo;
import io.harness.beans.steps.stepinfo.security.JfrogXrayStepInfo;
import io.harness.beans.steps.stepinfo.security.MendStepInfo;
import io.harness.beans.steps.stepinfo.security.MetasploitStepInfo;
import io.harness.beans.steps.stepinfo.security.NessusStepInfo;
import io.harness.beans.steps.stepinfo.security.NexusIQStepInfo;
import io.harness.beans.steps.stepinfo.security.NiktoStepInfo;
import io.harness.beans.steps.stepinfo.security.NmapStepInfo;
import io.harness.beans.steps.stepinfo.security.OpenvasStepInfo;
import io.harness.beans.steps.stepinfo.security.OwaspStepInfo;
import io.harness.beans.steps.stepinfo.security.PrismaCloudStepInfo;
import io.harness.beans.steps.stepinfo.security.ProwlerStepInfo;
import io.harness.beans.steps.stepinfo.security.QualysStepInfo;
import io.harness.beans.steps.stepinfo.security.ReapsawStepInfo;
import io.harness.beans.steps.stepinfo.security.ShiftLeftStepInfo;
import io.harness.beans.steps.stepinfo.security.SniperStepInfo;
import io.harness.beans.steps.stepinfo.security.SnykStepInfo;
import io.harness.beans.steps.stepinfo.security.SonarqubeStepInfo;
import io.harness.beans.steps.stepinfo.security.SysdigStepInfo;
import io.harness.beans.steps.stepinfo.security.TenableStepInfo;
import io.harness.beans.steps.stepinfo.security.VeracodeStepInfo;
import io.harness.beans.steps.stepinfo.security.ZapStepInfo;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.plancreator.steps.common.StepElementParameters.StepElementParametersBuilder;
import io.harness.plancreator.steps.common.WithStepElementParameters;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.yaml.core.StepSpecType;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModel;
import java.time.Duration;
import java.util.List;

@ApiModel(subTypes = {RunStepInfo.class, AquaTrivyStepInfo.class, AwsEcrStepInfo.class, BanditStepInfo.class,
              BlackDuckStepInfo.class, BrakemanStepInfo.class, BurpStepInfo.class, CheckmarxStepInfo.class,
              DataTheoremStepInfo.class, DockerContentTrustScanNode.class, ExternalScanNode.class,
              FortifyOnDemandStepInfo.class, GrypeStepInfo.class, MendStepInfo.class, MetasploitStepInfo.class,
              NmapStepInfo.class, NiktoStepInfo.class, NessusStepInfo.class, NexusIQStepInfo.class,
              OpenvasStepInfo.class, OwaspStepInfo.class, ProwlerStepInfo.class, QualysStepInfo.class,
              ReapsawStepInfo.class, ShiftLeftStepInfo.class, SnykStepInfo.class, SniperStepInfo.class,
              SysdigStepInfo.class, SonarqubeStepInfo.class, TenableStepInfo.class, PrismaCloudStepInfo.class,
              VeracodeStepInfo.class, JfrogXrayStepInfo.class, ZapStepInfo.class, SecurityStepInfo.class})
@OwnedBy(STO)
public interface STOStepInfo extends StepSpecType, WithStepElementParameters, SpecParameters {
  int MIN_RETRY = 0;
  int MAX_RETRY = 5;
  long DEFAULT_TIMEOUT = Duration.ofHours(2).toMillis();

  @JsonIgnore TypeInfo getNonYamlInfo();
  @JsonIgnore int getRetry();
  @JsonIgnore String getName();
  @JsonIgnore String getIdentifier();
  @JsonIgnore
  default long getDefaultTimeout() {
    return DEFAULT_TIMEOUT;
  }

  // TODO: implement this when we support graph section in yaml
  @JsonIgnore
  default List<String> getDependencies() {
    return null;
  }

  @Override
  default SpecParameters getSpecParameters() {
    return this;
  }

  default StepParameters getStepParameters(
      CIAbstractStepNode stepElementConfig, OnFailRollbackParameters failRollbackParameters) {
    StepElementParametersBuilder stepParametersBuilder =
        CiStepParametersUtils.getStepParameters(stepElementConfig, failRollbackParameters);
    stepParametersBuilder.spec(getSpecParameters());
    return stepParametersBuilder.build();
  }
}
