/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event.handler.impl.segment;

import static io.harness.event.handler.impl.Constants.ACCOUNT_ID;
import static io.harness.event.handler.impl.Constants.EMAIL_ID;
import static io.harness.event.model.EventType.USER_INVITE_ACCEPTED_FOR_TRIAL_ACCOUNT;
import static io.harness.rule.OwnerRule.VIKAS;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.event.handler.impl.Utils;
import io.harness.event.handler.segment.SegmentConfig;
import io.harness.event.listener.EventListener;
import io.harness.event.model.Event;
import io.harness.event.model.EventData;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.Account;
import software.wings.beans.User;
import software.wings.beans.UserInvite;
import software.wings.events.TestUtils;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.UserService;

import com.google.inject.Inject;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runners.MethodSorters;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class SegmentHandlerTest extends WingsBaseTest {
  @Mock private SegmentConfig segmentConfig;

  @Mock private EventListener eventListener;

  @Mock private AccountService accountService;

  @Inject @InjectMocks private SegmentHandler segmentHandler;

  @Mock private Utils utils;

  @Inject private TestUtils testUtils;

  @Mock UserService userService;

  @Mock UserInvite userInvite;

  private static final String SEGMENT_URL = "https://api.segment.io";
  private static final String TEST_ACCOUNT = "TEST_ACCOUNT_ID";
  private static final String EMAIL_ADDRESS = "admin@harness.io";
  private static final String INVITE_URL = "http://harness.io";

  @Test
  @Owner(developers = VIKAS)
  @Category(UnitTests.class)
  public void testHandleEvent() throws URISyntaxException {
    Account account = testUtils.createAccount();
    when(accountService.get(TEST_ACCOUNT)).thenReturn(account);
    User user = testUtils.createUser(account);
    user.setSegmentIdentity(user.getUuid());
    user.setReportedSegmentTracks(null);
    when(utils.getUser(any(Map.class))).thenReturn(user);
    when(userService.getUserInviteByEmailAndAccount(any(String.class), any(String.class))).thenReturn(userInvite);
    when(utils.getUserInviteUrl(any(UserInvite.class), any(Account.class))).thenReturn(INVITE_URL);
    when(userService.getUserFromCacheOrDB(any(String.class))).thenReturn(user);
    when(userService.update(any(User.class))).thenReturn(user);

    when(segmentConfig.getUrl()).thenReturn(SEGMENT_URL);
    when(segmentConfig.isEnabled()).thenReturn(Boolean.TRUE);

    Map<String, String> properties = new HashMap<>();
    properties.put(ACCOUNT_ID, TEST_ACCOUNT);
    properties.put(EMAIL_ID, EMAIL_ADDRESS);

    Event event = Event.builder()
                      .eventType(USER_INVITE_ACCEPTED_FOR_TRIAL_ACCOUNT)
                      .eventData(EventData.builder().properties(properties).build())
                      .build();
    segmentHandler.handleEvent(event);
    verify(accountService, times(1)).get(TEST_ACCOUNT);
    verify(utils, times(1)).getUser(properties);
    verify(userService, times(1)).getUserFromCacheOrDB(user.getUuid());
    verify(userService, times(1)).update(user);
  }
}
