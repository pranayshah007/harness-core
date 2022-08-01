/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.notification;

import static io.harness.rule.OwnerRule.BRIJESH;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.notification.bean.NotificationChannelWrapper;
import io.harness.notification.bean.NotificationRules;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class NotificationInstrumentationHelperTest extends CategoryTest {
  @Mock private NotificationHelper notificationHelper;
  @InjectMocks NotificationInstrumentationHelper notificationInstrumentationHelper;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetNotificationRules() throws IOException {
    String planExecutionId = "planExecutionId";
    Ambiance ambiance = Ambiance.newBuilder().build();
    doReturn(null).when(notificationHelper).obtainYaml(planExecutionId);
    List<NotificationRules> response =
        notificationInstrumentationHelper.getNotificationRules(planExecutionId, ambiance);
    assertEquals(response.size(), 0);

    doReturn("yaml").when(notificationHelper).obtainYaml(planExecutionId);
    doThrow(new IOException()).when(notificationHelper).getNotificationRulesFromYaml("yaml", ambiance);
    response = notificationInstrumentationHelper.getNotificationRules(planExecutionId, ambiance);
    assertEquals(response.size(), 0);
    doReturn(Collections.singletonList(NotificationRules.builder().build()))
        .when(notificationHelper)
        .getNotificationRulesFromYaml("yaml", ambiance);
    response = notificationInstrumentationHelper.getNotificationRules(planExecutionId, ambiance);
    assertEquals(response.size(), 1);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetNotificationMethodTypes() {
    List<NotificationRules> notificationRulesList = new ArrayList<>();
    notificationRulesList.add(NotificationRules.builder()
                                  .notificationChannelWrapper(ParameterField.createValueField(
                                      NotificationChannelWrapper.builder().type("Slack").build()))
                                  .build());
    Set<String> response = notificationInstrumentationHelper.getNotificationMethodTypes(notificationRulesList);
    assertEquals(response.size(), 1);
    assertTrue(response.contains("Slack"));

    notificationRulesList.add(NotificationRules.builder()
                                  .notificationChannelWrapper(ParameterField.createValueField(
                                      NotificationChannelWrapper.builder().type("Slack").build()))
                                  .build());
    response = notificationInstrumentationHelper.getNotificationMethodTypes(notificationRulesList);
    assertEquals(response.size(), 1);
    assertTrue(response.contains("Slack"));

    notificationRulesList.add(NotificationRules.builder()
                                  .notificationChannelWrapper(ParameterField.createValueField(
                                      NotificationChannelWrapper.builder().type("Email").build()))
                                  .build());
    response = notificationInstrumentationHelper.getNotificationMethodTypes(notificationRulesList);
    assertEquals(response.size(), 2);
    assertTrue(response.contains("Slack"));
    assertTrue(response.contains("Email"));
  }
}
