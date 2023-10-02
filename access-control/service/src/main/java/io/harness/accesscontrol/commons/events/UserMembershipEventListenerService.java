/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.commons.events;

import static io.harness.eventsframework.EventsFrameworkConstants.USERMEMBERSHIP;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.google.inject.Inject;
import com.google.inject.name.Named;

@OwnedBy(HarnessTeam.PL)
public class UserMembershipEventListenerService extends EventListenerService {
  @Inject
  public UserMembershipEventListenerService(
      UserMembershipEventListener userMembershipEventListener, @Named("eventsConfig") EventsConfig eventsConfig) {
    super(userMembershipEventListener, eventsConfig);
  }

  @Override
  public String getServiceName() {
    return USERMEMBERSHIP;
  }
}
