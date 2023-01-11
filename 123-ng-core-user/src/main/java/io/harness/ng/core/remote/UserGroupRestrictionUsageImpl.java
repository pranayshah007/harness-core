package io.harness.ng.core.remote;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.enforcement.beans.metadata.StaticLimitRestrictionMetadataDTO;
import io.harness.enforcement.client.usage.RestrictionUsageInterface;
import io.harness.ng.core.api.UserGroupService;

import com.google.inject.Inject;

@OwnedBy(HarnessTeam.PL)
public class UserGroupRestrictionUsageImpl implements RestrictionUsageInterface<StaticLimitRestrictionMetadataDTO> {
  @Inject UserGroupService userGroupService;

  @Override
  public long getCurrentValue(String accountIdentifier, StaticLimitRestrictionMetadataDTO restrictionMetadataDTO) {
    return userGroupService.countUserGroups(accountIdentifier);
  }
}
