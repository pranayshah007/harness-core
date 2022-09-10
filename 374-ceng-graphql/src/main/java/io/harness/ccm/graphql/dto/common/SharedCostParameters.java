package io.harness.ccm.graphql.dto.common;

import io.harness.ccm.views.businessMapping.entities.BusinessMapping;
import io.harness.ccm.views.businessMapping.entities.SharedCost;

import com.google.cloud.Timestamp;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SharedCostParameters {
  double totalCost;
  double numberOfEntities;
  Map<String, Double> costPerEntity;
  Map<String, Reference> entityReference;
  Map<String, Map<Timestamp, Double>> sharedCostFromGroupBy;
  BusinessMapping businessMappingFromGroupBy;
  Map<String, Map<Timestamp, Double>> sharedCostFromFilters;
  List<SharedCost> sharedCostBucketsFromFilters;
}
