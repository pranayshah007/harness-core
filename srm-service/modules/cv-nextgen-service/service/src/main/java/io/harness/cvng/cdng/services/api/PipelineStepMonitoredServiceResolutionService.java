/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.cdng.services.api;

import io.harness.cvng.cdng.beans.CVNGDeploymentStepInfo;
import io.harness.cvng.cdng.beans.CVNGStepInfo;
import io.harness.cvng.cdng.beans.MonitoredServiceNode;
import io.harness.cvng.cdng.beans.ResolvedCVConfigInfo;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.beans.params.ServiceEnvironmentParams;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.sdk.core.filter.creation.beans.FilterCreationContext;

import java.util.List;

public interface PipelineStepMonitoredServiceResolutionService {
  ResolvedCVConfigInfo fetchAndPersistResolvedCVConfigInfo(
      Ambiance ambiance, ServiceEnvironmentParams serviceEnvironmentParams, MonitoredServiceNode monitoredServiceNode);
  default void managePerpetualTasks(ServiceEnvironmentParams serviceEnvironmentParams,
      ResolvedCVConfigInfo resolvedCVConfigInfo, String verificationJobInstanceId) {}
  List<EntityDetailProtoDTO> getReferredEntities(
      FilterCreationContext filterCreationContext, CVNGStepInfo cvngStepInfo, ProjectParams projectParams);

  List<EntityDetailProtoDTO> getReferredEntities(FilterCreationContext filterCreationContext,
      CVNGDeploymentStepInfo cvngDeploymentStepInfo, ProjectParams projectParams);
}
