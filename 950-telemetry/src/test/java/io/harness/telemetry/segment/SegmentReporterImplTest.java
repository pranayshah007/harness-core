/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.telemetry.segment;

import static io.harness.TelemetryConstants.SEGMENT_DUMMY_ACCOUNT_PREFIX;
import static io.harness.TelemetryConstants.SYSTEM_USER;
import static io.harness.rule.OwnerRule.ALLEN;
import static io.harness.rule.OwnerRule.ZHUO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.UserPrincipal;
import io.harness.telemetry.Destination;
import io.harness.telemetry.TelemetrySdkTestBase;

import com.google.common.collect.ImmutableMap;
import com.segment.analytics.messages.GroupMessage;
import com.segment.analytics.messages.IdentifyMessage;
import com.segment.analytics.messages.TrackMessage;
import java.util.HashMap;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

@OwnedBy(HarnessTeam.GTM)
public class SegmentReporterImplTest extends TelemetrySdkTestBase {
  @Mock SegmentSender segmentSender;
  MockedStatic<SecurityContextBuilder> securityContextBuilderMockedStatic;
  @InjectMocks SegmentReporterImpl segmentReporterImpl;

  private static final String EMAIL = "dummy@dummy";
  private static final String DESTINATION = "Natero";
  private static final String USER_ID_KEY = "userId";
  private static final String GROUP_ID_KEY = "groupId";
  private static final String CATEGORY_KEY = "category";
  private static final String ACCOUNT_ID = "123";
  private static final String PROPERTY_KEY = "a";
  private static final String PROPERTY_VALUE = "b";
  private static final String NULL_KEY = "NULL_KEY";
  private static final String NULL_VALUE = "null";
  private static final String TEST_CATEGORY = io.harness.telemetry.Category.SIGN_UP;
  private HashMap<String, Object> properties;
  private Map<Destination, Boolean> destinations;
  private ArgumentCaptor<TrackMessage.Builder> trackCaptor;
  private ArgumentCaptor<GroupMessage.Builder> groupCaptor;
  private ArgumentCaptor<IdentifyMessage.Builder> identifyCaptor;

  @Before
  public void setUp() {
    initMocks(this);
    securityContextBuilderMockedStatic = Mockito.mockStatic(SecurityContextBuilder.class);
    securityContextBuilderMockedStatic.when(() -> SecurityContextBuilder.getPrincipal())
        .thenReturn(new UserPrincipal("dummy", EMAIL, "dummy", ACCOUNT_ID));
    properties = new HashMap<>();
    properties.put(PROPERTY_KEY, PROPERTY_VALUE);
    properties.put(NULL_KEY, null);
    destinations = ImmutableMap.<Destination, Boolean>builder().put(Destination.NATERO, true).build();
    trackCaptor = ArgumentCaptor.forClass(TrackMessage.Builder.class);
    groupCaptor = ArgumentCaptor.forClass(GroupMessage.Builder.class);
    identifyCaptor = ArgumentCaptor.forClass(IdentifyMessage.Builder.class);
  }

  @After
  public void cleanUp() {
    securityContextBuilderMockedStatic.close();
  }

  @Test
  @Owner(developers = ZHUO)
  @Category(UnitTests.class)
  public void testSendTrackEvent() {
    Mockito.when(segmentSender.isEnabled()).thenReturn(true);
    segmentReporterImpl.sendTrackEvent("test", properties, destinations, TEST_CATEGORY);
    Mockito.verify(segmentSender).enqueue(trackCaptor.capture());
    TrackMessage message = trackCaptor.getValue().build();
    assertThat(message.event()).isEqualTo("test");
    assertThat(message.properties().get(PROPERTY_KEY)).isEqualTo(PROPERTY_VALUE);
    assertThat(message.properties().get(NULL_KEY)).isEqualTo(NULL_VALUE);
    assertThat(message.properties().get(GROUP_ID_KEY)).isEqualTo(ACCOUNT_ID);
    assertThat(message.properties().get(USER_ID_KEY)).isEqualTo(EMAIL);
    assertThat(message.properties().get(CATEGORY_KEY)).isEqualTo(TEST_CATEGORY);
    assertThat(message.userId()).isEqualTo(EMAIL);
    assertThat(message.integrations().get(DESTINATION)).isEqualTo(true);
  }

  @Test
  @Owner(developers = ALLEN)
  @Category(UnitTests.class)
  public void testSendTrackEventWithAnalyticsUser() {
    securityContextBuilderMockedStatic.when(() -> SecurityContextBuilder.getPrincipal())
        .thenReturn(new UserPrincipal(null, null, null, null));
    Mockito.when(segmentSender.isEnabled()).thenReturn(true);
    String testAccountId = "testAccount";
    segmentReporterImpl.sendTrackEvent("test", null, testAccountId, properties, destinations, TEST_CATEGORY);
    Mockito.verify(segmentSender).enqueue(trackCaptor.capture());
    TrackMessage message = trackCaptor.getValue().build();
    assertThat(message.event()).isEqualTo("test");
    assertThat(message.properties().get(PROPERTY_KEY)).isEqualTo(PROPERTY_VALUE);
    assertThat(message.properties().get(NULL_KEY)).isEqualTo(NULL_VALUE);
    assertThat(message.properties().get(GROUP_ID_KEY)).isEqualTo(testAccountId);
    assertThat(message.properties().get(USER_ID_KEY)).isEqualTo(SEGMENT_DUMMY_ACCOUNT_PREFIX + testAccountId);
    assertThat(message.properties().get(CATEGORY_KEY)).isEqualTo(TEST_CATEGORY);
    assertThat(message.userId()).isEqualTo(SEGMENT_DUMMY_ACCOUNT_PREFIX + testAccountId);
    assertThat(message.integrations().get(DESTINATION)).isEqualTo(true);
  }

  @Test
  @Owner(developers = ALLEN)
  @Category(UnitTests.class)
  public void testSendTrackEventWithSystemUser() {
    securityContextBuilderMockedStatic.when(() -> SecurityContextBuilder.getPrincipal())
        .thenReturn(new UserPrincipal(null, null, null, null));
    Mockito.when(segmentSender.isEnabled()).thenReturn(true);
    segmentReporterImpl.sendTrackEvent("test", null, null, properties, destinations, TEST_CATEGORY);
    Mockito.verify(segmentSender).enqueue(trackCaptor.capture());
    TrackMessage message = trackCaptor.getValue().build();
    assertThat(message.event()).isEqualTo("test");
    assertThat(message.properties().get(PROPERTY_KEY)).isEqualTo(PROPERTY_VALUE);
    assertThat(message.properties().get(NULL_KEY)).isEqualTo(NULL_VALUE);
    assertThat(message.properties().get(GROUP_ID_KEY)).isEqualTo("null");
    assertThat(message.properties().get(USER_ID_KEY)).isEqualTo(SYSTEM_USER);
    assertThat(message.properties().get(CATEGORY_KEY)).isEqualTo(TEST_CATEGORY);
    assertThat(message.userId()).isEqualTo(SYSTEM_USER);
    assertThat(message.integrations().get(DESTINATION)).isEqualTo(true);
  }

  @Test
  @Owner(developers = ZHUO)
  @Category(UnitTests.class)
  public void testSendTrackEventWithNull() {
    Mockito.when(segmentSender.isEnabled()).thenReturn(true);
    segmentReporterImpl.sendTrackEvent(null, null, null, properties, destinations, null);
  }

  @Test
  @Owner(developers = ZHUO)
  @Category(UnitTests.class)
  public void testSendGroupEvent() {
    Mockito.when(segmentSender.isEnabled()).thenReturn(true);
    segmentReporterImpl.sendGroupEvent("accountId", properties, destinations);
    Mockito.verify(segmentSender).enqueue(groupCaptor.capture());
    GroupMessage message = groupCaptor.getValue().build();
    assertThat(message.groupId()).isEqualTo("accountId");
    assertThat(message.traits().get(PROPERTY_KEY)).isEqualTo(PROPERTY_VALUE);
    assertThat(message.traits().get(NULL_KEY)).isEqualTo(NULL_VALUE);
    assertThat(message.userId()).isEqualTo(EMAIL);
    assertThat(message.integrations().get(DESTINATION)).isEqualTo(true);
  }

  @Test
  @Owner(developers = ZHUO)
  @Category(UnitTests.class)
  public void testSendGroupEventWithNull() {
    Mockito.when(segmentSender.isEnabled()).thenReturn(true);
    segmentReporterImpl.sendGroupEvent(null, null, properties, destinations, null);
  }

  @Test
  @Owner(developers = ZHUO)
  @Category(UnitTests.class)
  public void testSendIdentifyEent() {
    Mockito.when(segmentSender.isEnabled()).thenReturn(true);
    segmentReporterImpl.sendIdentifyEvent("user", properties, destinations);
    Mockito.verify(segmentSender).enqueue(identifyCaptor.capture());
    IdentifyMessage message = identifyCaptor.getValue().build();
    assertThat(message.userId()).isEqualTo("user");
    assertThat(message.traits().get(PROPERTY_KEY)).isEqualTo(PROPERTY_VALUE);
    assertThat(message.traits().get(NULL_KEY)).isEqualTo(NULL_VALUE);
    assertThat(message.integrations().get(DESTINATION)).isEqualTo(true);
  }

  @Test
  @Owner(developers = ZHUO)
  @Category(UnitTests.class)
  public void testSendIdentifyEventWithNull() {
    Mockito.when(segmentSender.isEnabled()).thenReturn(true);
    segmentReporterImpl.sendIdentifyEvent(null, properties, destinations);
  }
}
