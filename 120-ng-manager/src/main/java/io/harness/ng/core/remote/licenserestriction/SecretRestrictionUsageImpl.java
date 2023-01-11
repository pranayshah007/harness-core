package io.harness.ng.core.remote.licenserestriction;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.enforcement.beans.metadata.StaticLimitRestrictionMetadataDTO;
import io.harness.enforcement.client.usage.RestrictionUsageInterface;
import io.harness.ng.core.api.SecretCrudService;

import com.google.inject.Inject;

@OwnedBy(HarnessTeam.PL)
public class SecretRestrictionUsageImpl implements RestrictionUsageInterface<StaticLimitRestrictionMetadataDTO> {
  @Inject SecretCrudService secretCrudService;

  @Override
  public long getCurrentValue(String accountIdentifier, StaticLimitRestrictionMetadataDTO restrictionMetadataDTO) {
    return secretCrudService.countSecrets(accountIdentifier);
  }
}
