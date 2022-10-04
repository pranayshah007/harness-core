/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.security.authentication;

import static io.harness.eventsframework.EventsFrameworkConstants.LDAP_GROUP_SYNC;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.api.Producer;
import io.harness.eventsframework.ldapgroupsync.ldapgroupsyncdata.LdapGroupSyncDTO;
import io.harness.eventsframework.producer.Message;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.PL)
public class NgLdapGroupSyncEventPublisher {
  @Inject @Named(LDAP_GROUP_SYNC) private Producer eventProducer;

  public void publishLdapGroupSyncEvent(String accountIdentifier, String ssoId) {
    log.info("EVENT_LDAP_GROUP_SYNC: Publishing Ldap group sync event for ssoId {}, for accountId {}", ssoId,
        accountIdentifier);
    LdapGroupSyncDTO ldapGroupSyncDTO =
        LdapGroupSyncDTO.newBuilder().setAccountIdentifier(accountIdentifier).setSsoId(ssoId).build();

    eventProducer.send(Message.newBuilder()
                           .putAllMetadata(ImmutableMap.of("accountId", accountIdentifier))
                           .setData(ldapGroupSyncDTO.toByteString())
                           .build());
  }
}
