/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.utils.monitoredService;

import io.harness.cvng.core.beans.CustomHealthLogDefinition;
import io.harness.cvng.core.beans.CustomHealthRequestDefinition;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.CustomHealthSourceLogSpec;
import io.harness.cvng.core.entities.CustomHealthLogCVConfig;

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.List;

public class CustomHealthSourceSpecLogTransformer
    implements CVConfigToHealthSourceTransformer<CustomHealthLogCVConfig, CustomHealthSourceLogSpec> {
  @Override
  public CustomHealthSourceLogSpec transformToHealthSourceConfig(List<CustomHealthLogCVConfig> cvConfigGroup) {
    Preconditions.checkNotNull(cvConfigGroup);

    CustomHealthSourceLogSpec logSpec = CustomHealthSourceLogSpec.builder()
                                            .connectorRef(cvConfigGroup.get(0).getConnectorIdentifier())
                                            .logDefinitions(new ArrayList<>())
                                            .build();

    cvConfigGroup.forEach(cvConfig -> {
      CustomHealthRequestDefinition requestDefinition = cvConfig.getRequestDefinition();
      CustomHealthLogDefinition specLogDefinition =
          CustomHealthLogDefinition.builder()
              .requestDefinition(CustomHealthRequestDefinition.builder()
                                     .endTimeInfo(requestDefinition.getEndTimeInfo())
                                     .startTimeInfo(requestDefinition.getStartTimeInfo())
                                     .method(requestDefinition.getMethod())
                                     .requestBody(requestDefinition.getRequestBody())
                                     .urlPath(requestDefinition.getUrlPath())
                                     .build())
              .logMessageJsonPath(cvConfig.getLogMessageJsonPath())
              .timestampJsonPath(cvConfig.getTimestampJsonPath())
              .serviceInstanceJsonPath(cvConfig.getServiceInstanceJsonPath())
              .queryName(cvConfig.getQueryName())
              .build();
      logSpec.getLogDefinitions().add(specLogDefinition);
    });

    return logSpec;
  }
}
