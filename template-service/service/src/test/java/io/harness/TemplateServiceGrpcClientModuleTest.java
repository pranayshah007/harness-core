/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.SHIVAM;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.grpc.client.GrpcClientConfig;
import io.harness.rule.Owner;

import io.grpc.Channel;
import javax.net.ssl.SSLException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDC)
public class TemplateServiceGrpcClientModuleTest {
  @InjectMocks TemplateServiceGrpcClientModule templateServiceGrpcClientModule;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetSwaggerTest() throws SSLException {
    GrpcClientConfig clientConfig = GrpcClientConfig.builder().target("t2").authority("a2").build();
    Channel channel = templateServiceGrpcClientModule.getChannel(clientConfig);
    assertThat(channel).isNotNull();
    assertThat(channel.authority()).isEqualTo(clientConfig.getAuthority());
  }
}
