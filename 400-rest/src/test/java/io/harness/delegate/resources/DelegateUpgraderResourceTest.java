/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.resources;

import static io.harness.annotations.dev.HarnessTeam.DEL;
import static io.harness.rule.OwnerRule.ARPIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.UpgradeCheckResult;
import io.harness.delegate.service.intfc.DelegateUpgraderService;
import io.harness.rule.Owner;

import javax.ws.rs.core.Application;
import javax.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.glassfish.jersey.test.inmemory.InMemoryTestContainerFactory;
import org.glassfish.jersey.test.spi.TestContainerException;
import org.glassfish.jersey.test.spi.TestContainerFactory;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@OwnedBy(DEL)
@RunWith(MockitoJUnitRunner.class)
@Slf4j
public class DelegateUpgraderResourceTest extends JerseyTest {
  private static final String ACCOUNT_ID = "account_id";
  private static final String DELEGATE_IMAGE_TAG = "harness/delegate:1";
  private static final String UPGRADER_IMAGE_TAG = "harness/upgrader:1";

  @Mock private DelegateUpgraderService upgraderService;

  @Override
  protected Application configure() {
    // needs to initialize mocks here, even though we are using MockitoJUnitRunner class
    initMocks(this);
    final ResourceConfig resourceConfig = new ResourceConfig();
    resourceConfig.register(new DelegateUpgraderResource(upgraderService));
    return resourceConfig;
  }

  @Override
  protected TestContainerFactory getTestContainerFactory() throws TestContainerException {
    return new InMemoryTestContainerFactory();
  }

  @Test
  @Owner(developers = ARPIT)
  @Category(UnitTests.class)
  public void testDelegateImageTag() {
    lenient()
        .when(upgraderService.getDelegateImageTag(ACCOUNT_ID, DELEGATE_IMAGE_TAG))
        .thenReturn(new UpgradeCheckResult(DELEGATE_IMAGE_TAG, false));

    final Response response = client()
                                  .target("/upgrade-check/delegate?accountId=" + ACCOUNT_ID
                                      + "&currentDelegateImageTag=" + DELEGATE_IMAGE_TAG)
                                  .request()
                                  .get();

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response).isNotNull();
  }

  @Test
  @Owner(developers = ARPIT)
  @Category(UnitTests.class)
  public void testUpgraderImageTag() {
    lenient()
        .when(upgraderService.getUpgraderImageTag(ACCOUNT_ID, UPGRADER_IMAGE_TAG))
        .thenReturn(new UpgradeCheckResult(UPGRADER_IMAGE_TAG, false));

    final Response response = client()
                                  .target("/upgrade-check/upgrader?accountId=" + ACCOUNT_ID
                                      + "&currentUpgraderImageTag=" + UPGRADER_IMAGE_TAG)
                                  .request()
                                  .get();

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response).isNotNull();
  }
}
