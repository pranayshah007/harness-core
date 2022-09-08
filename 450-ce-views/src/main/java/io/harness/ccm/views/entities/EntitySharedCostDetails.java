package io.harness.ccm.views.entities;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@Value
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class EntitySharedCostDetails {
  String sharedCostBucketName;
  double cost;
}
