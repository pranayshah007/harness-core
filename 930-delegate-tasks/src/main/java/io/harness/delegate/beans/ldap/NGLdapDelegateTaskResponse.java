package io.harness.delegate.beans.ldap;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateMetaInfo;
import io.harness.delegate.beans.DelegateTaskNotifyResponseData;

import software.wings.beans.sso.LdapGroupResponse;
import software.wings.beans.sso.LdapTestResponse;

import java.util.Collection;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@OwnedBy(PL)
@Getter
@Builder
public class NGLdapDelegateTaskResponse implements DelegateTaskNotifyResponseData {
  @Setter private DelegateMetaInfo delegateMetaInfo;
  private final Collection<LdapGroupResponse> ldapListGroupsResponses;
  private final LdapTestResponse ldapTestResponse;
}