/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.notification.services.impl;

import static io.harness.cvng.notification.services.impl.ErrorTrackingTemplateDataGenerator.EMAIL_FORMATTED_VERSION_LIST;
import static io.harness.cvng.notification.services.impl.ErrorTrackingTemplateDataGenerator.EMAIL_MONITORED_SERVICE_NAME_HYPERLINK;
import static io.harness.cvng.notification.services.impl.ErrorTrackingTemplateDataGenerator.EMAIL_NOTIFICATION_NAME_HYPERLINK;
import static io.harness.cvng.notification.services.impl.ErrorTrackingTemplateDataGenerator.ENVIRONMENT_NAME;
import static io.harness.cvng.notification.services.impl.ErrorTrackingTemplateDataGenerator.NOTIFICATION_NAME;
import static io.harness.cvng.notification.services.impl.ErrorTrackingTemplateDataGenerator.NOTIFICATION_URL;
import static io.harness.cvng.notification.services.impl.ErrorTrackingTemplateDataGenerator.SLACK_FORMATTED_VERSION_LIST;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.ENTITY_IDENTIFIER;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.ENTITY_NAME;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.MONITORED_SERVICE_URL;
import static io.harness.cvng.notification.utils.NotificationRuleConstants.SERVICE_IDENTIFIER;
import static io.harness.rule.OwnerRule.JAMES_RICKS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.cvng.client.FakeAccountClient;
import io.harness.cvng.client.NextGenService;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.notification.beans.ErrorTrackingEventStatus;
import io.harness.cvng.notification.beans.ErrorTrackingEventType;
import io.harness.cvng.notification.entities.MonitoredServiceNotificationRule;
import io.harness.ng.core.dto.OrganizationDTO;
import io.harness.ng.core.dto.ProjectDTO;
import io.harness.ng.core.service.dto.ServiceResponseDTO;
import io.harness.rule.Owner;

import java.time.Clock;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mockito;

public class ErrorTrackingTemplateDataGeneratorTest {
  public static final String ACCOUNT_IDENTIFIER = "accountIdentifier";
  public static final String PROJECT_IDENTIFIER = "projectIdentifier";
  public static final String ORGANIZATION_IDENTIFIER = "organizationIdentifier";

  public static final String MONITORED_SERVICE_NAME = "monitoredServiceName";
  public static final String MONITORED_SERVICE_ID = "monitoredServiceId";
  public static final String SERVICE_ID = "serviceId";

  public static final String ENVIRONMENT_NAME_VALUE = "environmentNameValue";
  public static final String SLACK_FORMATTED_VERSION_LIST_VALUE = "slackFormattedVersionListValue";
  public static final String NOTIFICATION_URL_VALUE = "notificationUrlValue";
  public static final String EMAIL_FORMATTED_VERSION_LIST_VALUE = "emailFormattedVersionListValue";
  public static final String NOTIFICATION_NAME_VALUE = "notificationNameValue";

  public static final String ORGANIZATION_NAME = "organizationName";
  public static final String PROJECT_NAME = "projectName";
  public static final String SERVICE_NAME = "serviceName";
  public static final String PORTAL_URL_VALUE = "http://testPortalUrl/";

  @Test
  @Owner(developers = JAMES_RICKS)
  @Category(UnitTests.class)
  public void getTemplateDataTest() throws IllegalAccessException {
    ProjectParams projectParams = ProjectParams.builder()
                                      .accountIdentifier(ACCOUNT_IDENTIFIER)
                                      .projectIdentifier(PROJECT_IDENTIFIER)
                                      .orgIdentifier(ORGANIZATION_IDENTIFIER)
                                      .build();

    ErrorTrackingTemplateDataGenerator errorTrackingTemplateDataGenerator =
        spy(new ErrorTrackingTemplateDataGenerator());
    Clock clock = Clock.systemUTC();
    FakeAccountClient accountClient = new FakeAccountClient();

    NextGenService nextGenService = Mockito.mock(NextGenService.class);

    OrganizationDTO organizationDTO = OrganizationDTO.builder().name(ORGANIZATION_NAME).build();
    ProjectDTO projectDTO = ProjectDTO.builder().name(PROJECT_NAME).build();
    ServiceResponseDTO serviceResponseDTO = ServiceResponseDTO.builder().name(SERVICE_NAME).build();

    when(nextGenService.getOrganization(ACCOUNT_IDENTIFIER, ORGANIZATION_IDENTIFIER)).thenReturn(organizationDTO);
    when(nextGenService.getProject(ACCOUNT_IDENTIFIER, ORGANIZATION_IDENTIFIER, PROJECT_IDENTIFIER))
        .thenReturn(projectDTO);
    when(nextGenService.getService(ACCOUNT_IDENTIFIER, ORGANIZATION_IDENTIFIER, PROJECT_IDENTIFIER, SERVICE_ID))
        .thenReturn(serviceResponseDTO);

    FieldUtils.writeField(errorTrackingTemplateDataGenerator, "portalUrl", PORTAL_URL_VALUE, true);
    FieldUtils.writeField(errorTrackingTemplateDataGenerator, "clock", clock, true);
    FieldUtils.writeField(errorTrackingTemplateDataGenerator, "accountClient", accountClient, true);
    FieldUtils.writeField(errorTrackingTemplateDataGenerator, "nextGenService", nextGenService, true);

    Map<String, String> notificationDataMap = new HashMap<>();
    notificationDataMap.put(ENVIRONMENT_NAME, ENVIRONMENT_NAME_VALUE);
    notificationDataMap.put(SLACK_FORMATTED_VERSION_LIST, SLACK_FORMATTED_VERSION_LIST_VALUE);
    notificationDataMap.put(NOTIFICATION_URL, NOTIFICATION_URL_VALUE);
    notificationDataMap.put(EMAIL_FORMATTED_VERSION_LIST, EMAIL_FORMATTED_VERSION_LIST_VALUE);
    notificationDataMap.put(NOTIFICATION_NAME, NOTIFICATION_NAME_VALUE);

    MonitoredServiceNotificationRule.MonitoredServiceCodeErrorCondition codeErrorCondition =
        MonitoredServiceNotificationRule.MonitoredServiceCodeErrorCondition.builder()
            .errorTrackingEventStatus(Collections.singletonList(ErrorTrackingEventStatus.NEW_EVENTS))
            .errorTrackingEventTypes(Collections.singletonList(ErrorTrackingEventType.EXCEPTION))
            .build();

    Map<String, Object> entityDetails = Map.of(
        ENTITY_NAME, MONITORED_SERVICE_NAME, ENTITY_IDENTIFIER, MONITORED_SERVICE_ID, SERVICE_IDENTIFIER, SERVICE_ID);
    final Map<String, String> templateData = errorTrackingTemplateDataGenerator.getTemplateData(
        projectParams, entityDetails, codeErrorCondition, notificationDataMap);

    assertThat(templateData.get(ENVIRONMENT_NAME)).isEqualTo(ENVIRONMENT_NAME_VALUE);
    assertThat(templateData.get(MONITORED_SERVICE_URL)).contains(MONITORED_SERVICE_ID);
    assertThat(templateData.get(SLACK_FORMATTED_VERSION_LIST)).contains(SLACK_FORMATTED_VERSION_LIST_VALUE);
    assertThat(templateData.get(NOTIFICATION_URL)).isEqualTo(NOTIFICATION_URL_VALUE);
    assertThat(templateData.get(NOTIFICATION_NAME)).isEqualTo(NOTIFICATION_NAME_VALUE);
    assertThat(templateData.get(EMAIL_MONITORED_SERVICE_NAME_HYPERLINK))
        .contains(MONITORED_SERVICE_ID)
        .contains(MONITORED_SERVICE_NAME);
    assertThat(templateData.get(EMAIL_NOTIFICATION_NAME_HYPERLINK))
        .contains(NOTIFICATION_URL_VALUE)
        .contains(NOTIFICATION_NAME_VALUE);
    assertThat(templateData.get(EMAIL_FORMATTED_VERSION_LIST)).isEqualTo(EMAIL_FORMATTED_VERSION_LIST_VALUE);
  }
}