package io.harness.delegate.beans.ldap;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateMetaInfo;
import io.harness.delegate.beans.DelegateTaskNotifyResponseData;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import software.wings.beans.sso.LdapGroupResponse;

import java.util.Collection;

import static io.harness.annotations.dev.HarnessTeam.PL;

@OwnedBy(PL)
@Getter
@Builder
public class LdapGroupSearchTaskResponse implements DelegateTaskNotifyResponseData {
  @Setter private DelegateMetaInfo delegateMetaInfo;
  private final Collection<LdapGroupResponse> ldapListGroupsResponses;
}
