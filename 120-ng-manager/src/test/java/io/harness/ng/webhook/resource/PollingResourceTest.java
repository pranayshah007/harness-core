/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.webhook.resource;

import static io.harness.rule.OwnerRule.MEET;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.polling.PollingDelegateResponse;
import io.harness.dto.PollingInfoForTriggers;
import io.harness.ng.webhook.polling.PollingResponseHandler;
import io.harness.ng.webhook.resources.PollingResource;
import io.harness.polling.contracts.PollingItem;
import io.harness.polling.service.intfc.PollingService;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class PollingResourceTest extends CategoryTest {
  @InjectMocks PollingResource pollingResource;
  @Mock KryoSerializer kryoSerializer;
  @Mock PollingResponseHandler pollingResponseHandler;
  @Mock PollingService pollingService;
  String accountId = "accountId";
  String perpetualTaskId = "perpetualTaskId";
  byte[] bytes = new byte[] {};
  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testProcessPollingResultNg() {
    PollingDelegateResponse pollingDelegateResponse = PollingDelegateResponse.builder().build();
    when(kryoSerializer.asObject((byte[]) any())).thenReturn(pollingDelegateResponse);
    doNothing().when(pollingResponseHandler).handlePollingResponse(perpetualTaskId, accountId, pollingDelegateResponse);
    pollingResource.processPollingResultNg(perpetualTaskId, accountId, bytes);
    verify(pollingResponseHandler, times(1)).handlePollingResponse(perpetualTaskId, accountId, pollingDelegateResponse);
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testSubscribe() {
    when(pollingService.subscribe(any())).thenReturn("pollingDocId");
    PollingItem pollingItem = PollingItem.newBuilder().build();
    when(kryoSerializer.asObject((byte[]) any())).thenReturn(pollingItem);
    when(kryoSerializer.asBytes(any())).thenReturn(bytes);
    assertThat(pollingResource.subscribe(bytes).getData().getPollingResponse()).isEqualTo(bytes);
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testGetPollingDocmentForTriggers() {
    PollingInfoForTriggers pollingInfoForTriggers = PollingInfoForTriggers.builder().build();
    when(pollingService.getPollingInfoForTriggers(accountId, "pollingDocId")).thenReturn(pollingInfoForTriggers);
    assertThat(pollingResource.getPollingDocmentForTriggers(accountId, "pollingDocId").getData())
        .isEqualTo(pollingInfoForTriggers);
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testUnsubscribe() {
    when(pollingService.unsubscribe(any())).thenReturn(Boolean.TRUE);
    when(kryoSerializer.asObject((byte[]) any())).thenReturn(PollingItem.newBuilder().build());
    assertThat(pollingResource.unsubscribe(bytes)).isEqualTo(true);
  }
}
