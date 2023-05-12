/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.marketplace;

import static io.harness.annotations.dev.HarnessModule._940_MARKETPLACE_INTEGRATIONS;
import static io.harness.annotations.dev.HarnessTeam.GTM;
import static io.harness.rule.OwnerRule.RAMA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.configuration.DeployMode;
import io.harness.event.handler.impl.segment.SegmentHelper;
import io.harness.marketplace.gcp.GcpMarketPlaceApiHandlerImpl;
import io.harness.marketplace.gcp.procurement.GcpProcurementService;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.app.MainConfiguration;
import software.wings.beans.marketplace.gcp.GCPMarketplaceCustomer;
import software.wings.dl.WingsPersistence;

import com.google.inject.Inject;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@OwnedBy(GTM)
@TargetModule(_940_MARKETPLACE_INTEGRATIONS)
public class GcpMarketPlaceApiHandlerTest extends WingsBaseTest {
  @Mock private MainConfiguration configuration;
  @Inject private WingsPersistence wingsPersistence;
  @Mock private GcpProcurementService gcpProcurementService;
  @Mock private SegmentHelper segmentHelper;
  @Spy private GcpMarketPlaceApiHandlerImpl gcpMarketPlaceApiHandler;

  @Before
  public void setupContext() throws IllegalAccessException {
    MockitoAnnotations.initMocks(this);
    when(configuration.getDeployMode()).thenReturn(DeployMode.KUBERNETES);
    FieldUtils.writeField(gcpMarketPlaceApiHandler, "configuration", configuration, true);
    FieldUtils.writeField(gcpMarketPlaceApiHandler, "segmentHelper", segmentHelper, true);
    FieldUtils.writeField(gcpMarketPlaceApiHandler, "wingsPersistence", wingsPersistence, true);
    FieldUtils.writeField(gcpMarketPlaceApiHandler, "gcpProcurementService", gcpProcurementService, true);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testRegisterBillingOnlyTransaction() {
    doReturn("testGcpAccountId").when(gcpMarketPlaceApiHandler).verifyGcpMarketplaceToken(anyString());
    gcpMarketPlaceApiHandler.registerBillingOnlyTransaction("testGcpAccountId");
    GCPMarketplaceCustomer gcpMarketplaceCustomer =
        wingsPersistence.createQuery(GCPMarketplaceCustomer.class).filter("gcpAccountId", "testGcpAccountId").get();
    assertThat(gcpMarketplaceCustomer).isNotNull();
    verify(gcpProcurementService).approveAccount("testGcpAccountId");
  }
}
