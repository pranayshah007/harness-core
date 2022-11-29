/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans.monitoredService.healthSouceSpec;

import static io.harness.cvng.beans.MonitoredServiceDataSourceType.dataSourceTypeMonitoredServiceDataSourceTypeMap;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.core.beans.monitoredService.HealthSource;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.services.api.monitoredService.HealthSourceService;
import io.harness.cvng.core.utils.monitoredService.CVConfigToHealthSourceTransformer;
import io.harness.cvng.models.VerificationType;

import com.google.common.base.Preconditions;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.apache.commons.lang3.tuple.Pair;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class HealthSourceDTO {
  String identifier;
  String name;
  DataSourceType type;
  VerificationType verificationType;

  public static HealthSourceDTO toHealthSourceDTO(HealthSource healthSource) {
    return HealthSourceDTO.builder()
        .name(healthSource.getName())
        .identifier(healthSource.getIdentifier())
        .type(healthSource.getSpec().getType())
        .verificationType(healthSource.getSpec().getType().getVerificationType())
        .build();
  }

  public static HealthSource toHealthSource(List<CVConfig> cvConfigs,
      Map<DataSourceType, CVConfigToHealthSourceTransformer> dataSourceTypeToHealthSourceTransformerMap) {
    Preconditions.checkState(isNotEmpty(cvConfigs), "Cannot convert to HealthSource if cvConfig list is empty");
    CVConfig baseCVConfig = cvConfigs.get(0);
    CVConfigToHealthSourceTransformer<CVConfig, HealthSourceSpec> cvConfigToHealthSourceTransformer =
        dataSourceTypeToHealthSourceTransformerMap.get(baseCVConfig.getType());
    Pair<String, String> nameSpaceAndIdentifier =
        HealthSourceService.getNameSpaceAndIdentifier(baseCVConfig.getFullyQualifiedIdentifier());
    return HealthSource.builder()
        .name(baseCVConfig.getMonitoringSourceName())
        .type(dataSourceTypeMonitoredServiceDataSourceTypeMap.get(baseCVConfig.getType()))
        .identifier(nameSpaceAndIdentifier.getValue())
        .spec(cvConfigToHealthSourceTransformer.transform(cvConfigs))
        .build();
  }
}
