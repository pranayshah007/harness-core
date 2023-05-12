/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.events;

import static io.harness.rule.OwnerRule.RAMA;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.event.handler.impl.MarketoHandler;
import io.harness.event.handler.impl.MarketoHelper;
import io.harness.event.handler.marketo.MarketoConfig;
import io.harness.event.listener.EventListener;
import io.harness.event.model.Event;
import io.harness.event.model.EventData;
import io.harness.event.model.EventType;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.Account;
import software.wings.beans.User;
import software.wings.security.UserThreadLocal;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.UserService;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

/**
 * @author rktummala on 12/06/18
 */
public class MarketoHandlerTest extends WingsBaseTest {
  @Mock private AccountService accountService;
  @Mock private EventListener eventListener;
  @Mock private UserService userService;
  @Inject private TestUtils testUtils;
  @Inject private MarketoHelper marketoHelper;

  private User user;
  private Account account;

  private MarketoHandler marketoHandler;

  @Before
  public void setup() throws IllegalAccessException {
    MarketoConfig marketoConfig = testUtils.initializeMarketoConfig();
    marketoHandler = new MarketoHandler(marketoConfig, eventListener);
    FieldUtils.writeField(marketoHandler, "userService", userService, true);
    FieldUtils.writeField(marketoHandler, "marketoHelper", marketoHelper, true);
    FieldUtils.writeField(marketoHelper, "marketoConfig", marketoConfig, true);
    FieldUtils.writeField(marketoHelper, "accountService", accountService, true);
    FieldUtils.writeField(marketoHelper, "userService", userService, true);
    when(accountService.get(anyString())).thenReturn(account);
    when(accountService.save(any(), eq(false))).thenReturn(account);
    account = testUtils.createAccount();
    user = testUtils.createUser(account);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  @Ignore("TODO: please provide clear motivation why this test is ignored")
  public void testCreateLeadAndTriggerCampaign() {
    UserThreadLocal.set(user);
    try {
      EventType eventType = EventType.COMPLETE_USER_REGISTRATION;
      Map<String, String> properties = new HashMap<>();
      properties.put("ACCOUNT_ID", "ACCOUNT_ID");
      properties.put("EMAIL_ID", "admin@harness.io");

      EventData eventData = EventData.builder().properties(properties).build();
      Event event = Event.builder().eventData(eventData).eventType(eventType).build();
      User newUser = User.Builder.anUser().email("admin@harness.io").accounts(Arrays.asList(account)).build();
      when(userService.getUserByEmail(anyString())).thenReturn(newUser);
      when(userService.update(any(User.class))).thenReturn(newUser);
      when(accountService.get(anyString())).thenReturn(account);

      marketoHandler.handleEvent(event);
      verify(userService, times(1)).update(any(User.class));
    } finally {
      UserThreadLocal.unset();
    }
  }
}
