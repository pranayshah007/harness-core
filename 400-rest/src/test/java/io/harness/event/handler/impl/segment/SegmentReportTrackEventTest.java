/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.event.handler.impl.segment;

import static io.harness.TelemetryConstants.SEGMENT_DUMMY_ACCOUNT_PREFIX;
import static io.harness.event.handler.impl.Constants.ACCOUNT_ID;
import static io.harness.event.handler.impl.Constants.EMAIL_ID;
import static io.harness.rule.OwnerRule.XIN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.Account;
import software.wings.events.TestUtils;

import com.google.inject.Inject;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class SegmentReportTrackEventTest extends WingsBaseTest {
  @Mock SegmentHelper segmentHelper;

  @Inject private TestUtils testUtils;

  @Inject @InjectMocks private SegmentHandler segmentHandler;

  private Account account;

  private String deploymentEvent;

  private Map<String, String> properties;

  private Map<String, Boolean> integrations;

  private static final String TEST_ACCOUNT = "ACCOUNT_ID";
  private static final String EMAIL_ADDRESS = "admin@harness.io";

  private String emptyUserId;
  @Before
  public void setUp() {
    account = testUtils.createAccount();
    deploymentEvent = "Deployment Running";
    properties = new HashMap<>();
    properties.put(ACCOUNT_ID, TEST_ACCOUNT);
    properties.put(EMAIL_ID, EMAIL_ADDRESS);

    integrations = new HashMap<>();
    integrations.put(SegmentHandler.Keys.NATERO, true);
    integrations.put(SegmentHandler.Keys.SALESFORCE, false);

    emptyUserId = null;
  }

  @Test
  @Owner(developers = XIN)
  @Category(UnitTests.class)
  public void testReportTrackEventWithEmptyUserId() throws URISyntaxException {
    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    when(segmentHelper.reportTrackEvent(any(), any(), any(), any())).thenReturn(true);
    segmentHandler.reportTrackEvent(account, deploymentEvent, emptyUserId, properties, integrations);
    verify(segmentHelper, times(1)).reportTrackEvent(captor.capture(), any(), any(), any());
    String identity = captor.getValue();
    assertThat(identity).isEqualTo(SEGMENT_DUMMY_ACCOUNT_PREFIX + account.getUuid());
  }
}
