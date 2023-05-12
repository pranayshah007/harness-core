/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.core.outbox;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.VIKAS_M;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.apache.commons.lang3.RandomStringUtils.randomNumeric;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.ResourceTypeConstants;
import io.harness.audit.client.api.AuditClientService;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.AccountScope;
import io.harness.ng.core.Resource;
import io.harness.ng.core.ResourceScope;
import io.harness.outbox.OutboxEvent;
import io.harness.rule.Owner;
import io.harness.usermembership.remote.UserMembershipClient;

import software.wings.WingsBaseTest;
import software.wings.app.MainConfiguration;

import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PL)
public class UserEventHandlerTest extends WingsBaseTest {
  @Mock private AuditClientService auditClientService;
  private UserEventHandler userEventHandler;
  @Mock private UserMembershipClient userMembershipClient;
  @Mock private MainConfiguration mainConfiguration;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    userEventHandler = new UserEventHandler(auditClientService, userMembershipClient, mainConfiguration);
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testLoginEvent_withoutEnableAuditShouldNotCallNgManager() {
    String accountIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    String userId = randomAlphabetic(10);
    String userName = randomAlphabetic(10);
    Map<String, String> labels = new HashMap<>();
    labels.put("userId", userId);
    labels.put("resourceName", userName);
    ResourceScope resourceScope = new AccountScope(accountIdentifier);
    Resource resource =
        Resource.builder().type(ResourceTypeConstants.USER).identifier(identifier).labels(labels).build();
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .eventType("Login")
                                  .resourceScope(resourceScope)
                                  .resource(resource)
                                  .createdAt(Long.parseLong(randomNumeric(5)))
                                  .build();
    when(mainConfiguration.isEnableAudit()).thenReturn(false);
    userEventHandler.handle(outboxEvent);
    verify(userMembershipClient, times(0)).isUserInScope(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testLoginEvent_withEnableAuditShouldCallNgManager() {
    String accountIdentifier = randomAlphabetic(10);
    String identifier = randomAlphabetic(10);
    String userId = randomAlphabetic(10);
    String userName = randomAlphabetic(10);
    Map<String, String> labels = new HashMap<>();
    labels.put("userId", userId);
    labels.put("resourceName", userName);
    ResourceScope resourceScope = new AccountScope(accountIdentifier);
    Resource resource =
        Resource.builder().type(ResourceTypeConstants.USER).identifier(identifier).labels(labels).build();
    OutboxEvent outboxEvent = OutboxEvent.builder()
                                  .id(randomAlphabetic(10))
                                  .blocked(false)
                                  .eventType("Login")
                                  .resourceScope(resourceScope)
                                  .resource(resource)
                                  .createdAt(Long.parseLong(randomNumeric(5)))
                                  .build();
    when(mainConfiguration.isEnableAudit()).thenReturn(true);
    when(auditClientService.publishAudit(any(), any(), any())).thenReturn(true);
    userEventHandler.handle(outboxEvent);
    verify(userMembershipClient, times(1)).isUserInScope(any(), any(), any(), any());
  }
}
