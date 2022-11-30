/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.cdng.services.impl;

import io.harness.cvng.cdng.beans.CVNGStepInfo;
import io.harness.cvng.cdng.beans.MonitoredServiceSpec.MonitoredServiceSpecType;
import io.harness.cvng.cdng.services.api.VerifyStepMonitoredServiceResolutionService;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.filters.GenericStepPMSFilterJsonCreator;
import io.harness.plancreator.steps.StepElementConfig;
import io.harness.pms.filter.creation.FilterCreationResponse;
import io.harness.pms.sdk.core.filter.creation.beans.FilterCreationContext;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
@Slf4j
public class CVNGStepFilterJsonCreator extends GenericStepPMSFilterJsonCreator {
  @Override
  public Set<String> getSupportedStepTypes() {
    return CVNGPlanCreatorV2.CVNG_SUPPORTED_TYPES;
  }
  @Inject
  private Map<MonitoredServiceSpecType, VerifyStepMonitoredServiceResolutionService> verifyStepCvConfigServiceMap;

  @Override
  public FilterCreationResponse handleNode(FilterCreationContext filterCreationContext, StepElementConfig yamlField) {
    Preconditions.checkState(yamlField.getStepSpecType() instanceof CVNGStepInfo);
    String accountIdentifier = filterCreationContext.getSetupMetadata().getAccountId();
    String orgIdentifier = filterCreationContext.getSetupMetadata().getOrgId();
    String projectIdentifier = filterCreationContext.getSetupMetadata().getProjectId();
    ProjectParams projectParams = ProjectParams.builder()
                                      .accountIdentifier(accountIdentifier)
                                      .orgIdentifier(orgIdentifier)
                                      .projectIdentifier(projectIdentifier)
                                      .build();

    CVNGStepInfo cvngStepInfo = (CVNGStepInfo) yamlField.getStepSpecType();
    cvngStepInfo.validate();

    MonitoredServiceSpecType monitoredServiceType =
        CVNGStepUtils.getMonitoredServiceSpecType(cvngStepInfo.getMonitoredService());
    List<EntityDetailProtoDTO> result = verifyStepCvConfigServiceMap.get(monitoredServiceType)
                                            .getReferredEntities(filterCreationContext, cvngStepInfo, projectParams);
    return FilterCreationResponse.builder().referredEntities(result).build();
  }
}
