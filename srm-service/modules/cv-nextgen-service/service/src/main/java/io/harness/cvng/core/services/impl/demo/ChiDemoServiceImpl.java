/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl.demo;

import io.harness.cvng.beans.change.ChangeEventDTO;
import io.harness.cvng.beans.change.ChangeSourceType;
import io.harness.cvng.core.beans.change.DemoChangeEventDTO;
import io.harness.cvng.core.beans.params.MonitoredServiceParams;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.entities.changeSource.ChangeSource;
import io.harness.cvng.core.services.api.ChangeEventService;
import io.harness.cvng.core.services.api.FeatureFlagService;
import io.harness.cvng.core.services.api.demo.ChangeSourceDemoDataGenerator;
import io.harness.cvng.core.services.api.demo.ChiDemoService;
import io.harness.cvng.core.services.api.monitoredService.ChangeSourceService;
import io.harness.cvng.core.utils.FeatureFlagNames;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import java.util.List;
import java.util.Map;

public class ChiDemoServiceImpl implements ChiDemoService {
  @Inject private ChangeSourceService changeSourceService;
  @Inject private ChangeEventService changeEventService;
  @Inject private Map<ChangeSourceType, ChangeSourceDemoDataGenerator> changeSourceTypeToDemoDataGeneratorMap;
  @Inject private FeatureFlagService featureFlagService;
  @Override
  public void registerDemoChangeEvent(ProjectParams projectParams, DemoChangeEventDTO demoChangeEventDTO) {
    ChangeSource changeSource =
        changeSourceService.get(MonitoredServiceParams.builderWithProjectParams(projectParams)
                                    .monitoredServiceIdentifier(demoChangeEventDTO.getMonitoredServiceIdentifier())
                                    .build(),
            demoChangeEventDTO.getChangeSourceIdentifier());
    Preconditions.checkState(featureFlagService.isFeatureFlagEnabled(
                                 projectParams.getAccountIdentifier(), FeatureFlagNames.CVNG_MONITORED_SERVICE_DEMO),
        "Feature flag %s is not enabled", FeatureFlagNames.CVNG_MONITORED_SERVICE_DEMO);
    Preconditions.checkState(changeSource.isConfiguredForDemo(),
        "Change source with identifier is not a demo change source. Please check the identifier pattern");
    List<ChangeEventDTO> changeEvents =
        changeSourceTypeToDemoDataGeneratorMap.get(demoChangeEventDTO.getChangeSourceType()).generate(changeSource);
    changeEvents.forEach(changeEvent -> changeEventService.register(changeEvent));
  }
}
