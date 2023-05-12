/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.aws.sam;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.infra.beans.AwsSamInfrastructureOutcome;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.AwsSamServerInstanceInfo;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.yaml.ParameterField;

import com.google.inject.Inject;
import java.util.List;

@OwnedBy(HarnessTeam.CDP)
public class AwsSamStepHelper {
  @Inject protected OutcomeService outcomeService;

  public void verifyPluginImageIsProvider(ParameterField<String> image) {
    if (ParameterField.isNull(image) || image.getValue() == null) {
      throw new InvalidRequestException("Plugin Image must be provided");
    }
  }

  public InfrastructureOutcome getInfrastructureOutcome(Ambiance ambiance) {
    return (InfrastructureOutcome) outcomeService.resolve(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));
  }

  public void updateServerInstanceInfoList(
      List<ServerInstanceInfo> serverInstanceInfoList, InfrastructureOutcome infrastructureOutcome) {
    if (serverInstanceInfoList != null) {
      for (ServerInstanceInfo serverInstanceInfo : serverInstanceInfoList) {
        AwsSamServerInstanceInfo awsSamServerInstanceInfo = (AwsSamServerInstanceInfo) serverInstanceInfo;
        awsSamServerInstanceInfo.setInfraStructureKey(infrastructureOutcome.getInfrastructureKey());
        AwsSamInfrastructureOutcome awsSamInfrastructureOutcome = (AwsSamInfrastructureOutcome) infrastructureOutcome;
        awsSamServerInstanceInfo.setRegion(awsSamInfrastructureOutcome.getRegion());
      }
    }
  }
}
