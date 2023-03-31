/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service;

import static io.harness.rule.OwnerRule.GEORGE;
import static io.harness.rule.OwnerRule.UNKNOWN;

import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.Event.Builder.anEvent;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ARTIFACT_ID;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.beans.Event;
import software.wings.beans.Event.Type;
import software.wings.service.impl.EventEmitter;
import software.wings.service.impl.EventEmitter.Channel;
import software.wings.service.intfc.AppService;

import org.atmosphere.cpr.Broadcaster;
import org.atmosphere.cpr.BroadcasterFactory;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/**
 * Created by peeyushaggarwal on 8/16/16.
 */
public class EventEmitterTest extends CategoryTest {
  /**
   * The Mockito rule.
   */
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @InjectMocks private EventEmitter eventEmitter = new EventEmitter();

  @Mock private Broadcaster broadcaster;
  @Mock private BroadcasterFactory broadcasterFactory;
  @Mock private AppService appService;

  @Before
  public void setupMocks() {
    when(broadcasterFactory.lookup(anyString(), anyBoolean())).thenReturn(broadcaster);
  }
  /**
   * Should send to both id and general channel.
   *
   * @throws Exception the exception
   */
  @Test
  @Owner(developers = UNKNOWN)
  @Category(UnitTests.class)
  public void shouldSendToBothIdAndGeneralChannel() throws Exception {
    Event event = anEvent().withUuid(ARTIFACT_ID).withType(Type.UPDATE).build();
    eventEmitter.send(Channel.ARTIFACTS, event);
    verify(broadcasterFactory).lookup("/stream/ui/*/all/all/all/artifacts", true);
    verify(broadcasterFactory).lookup("/stream/ui/*/all/all/all/artifacts/" + ARTIFACT_ID, true);
    verify(broadcaster, times(2)).broadcast(event);
  }

  /**
   * Should send to general channel when id channel not connected.
   *
   * @throws Exception the exception
   */
  @Test
  @Owner(developers = UNKNOWN)
  @Category(UnitTests.class)
  public void shouldSendToGeneralChannelWhenIdisNull() throws Exception {
    Event event = anEvent().withType(Type.UPDATE).build();
    eventEmitter.send(Channel.ARTIFACTS, event);
    verify(broadcasterFactory).lookup("/stream/ui/*/all/all/all/artifacts", true);
    verify(broadcaster, times(1)).broadcast(event);
  }

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  public void shouldGetAccountIdToBroadcast() throws Exception {
    when(appService.get(APP_ID)).thenReturn(anApplication().accountId("ACCOUNT_ID").appId(APP_ID).build());
    Event event = anEvent().withUuid(ARTIFACT_ID).withType(Type.UPDATE).withAppId(APP_ID).build();
    eventEmitter.send(Channel.ARTIFACTS, event);
    verify(broadcasterFactory).lookup("/stream/ui/ACCOUNT_ID/APP_ID/all/all/artifacts", true);
    verify(broadcasterFactory).lookup("/stream/ui/ACCOUNT_ID/APP_ID/all/all/artifacts/" + ARTIFACT_ID, true);
    verify(broadcaster, times(2)).broadcast(event);
  }
}
