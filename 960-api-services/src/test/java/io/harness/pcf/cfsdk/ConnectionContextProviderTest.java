/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pcf.cfsdk;

import static io.harness.rule.OwnerRule.ADWAIT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pcf.PivotalClientApiException;
import io.harness.pcf.model.CfRequestConfig;
import io.harness.rule.Owner;

import java.time.Duration;
import java.util.Optional;
import org.cloudfoundry.reactor.ConnectionContext;
import org.cloudfoundry.reactor.DefaultConnectionContext;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(HarnessTeam.CDP)
@RunWith(MockitoJUnitRunner.class)
public class ConnectionContextProviderTest extends CategoryTest {
  @Spy private ConnectionContextProvider connectionContextProvider;

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGetConnectionContext() throws PivotalClientApiException {
    CfRequestConfig cfRequestConfig = CfRequestConfig.builder().endpointUrl("test").timeOutIntervalInMins(10).build();

    ConnectionContext connectionContext = connectionContextProvider.getConnectionContext(cfRequestConfig);

    assertThat(connectionContext).isInstanceOf(DefaultConnectionContext.class);
    Optional<Duration> connectTimeout = ((DefaultConnectionContext) connectionContext).getConnectTimeout();
    assertThat(connectTimeout.isPresent()).isTrue();
    assertThat(connectTimeout.get().getSeconds()).isEqualTo(300);
  }
}
