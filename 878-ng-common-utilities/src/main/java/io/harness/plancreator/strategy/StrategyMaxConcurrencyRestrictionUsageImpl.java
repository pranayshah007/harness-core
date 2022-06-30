package io.harness.plancreator.strategy;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.enforcement.beans.metadata.StaticLimitRestrictionMetadataDTO;
import io.harness.enforcement.client.usage.RestrictionUsageInterface;

@OwnedBy(HarnessTeam.PIPELINE)
public class StrategyMaxConcurrencyRestrictionUsageImpl
    implements RestrictionUsageInterface<StaticLimitRestrictionMetadataDTO> {
  @Override
  public long getCurrentValue(String accountIdentifier, StaticLimitRestrictionMetadataDTO restrictionMetadataDTO) {
    return 0;
  }
}
