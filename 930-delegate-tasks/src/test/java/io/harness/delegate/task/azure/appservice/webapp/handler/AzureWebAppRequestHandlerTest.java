/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.appservice.webapp.handler;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ABOSII;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.context.AzureWebClientContext;
import io.harness.azure.model.AzureConfig;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.task.azure.appservice.webapp.ng.AzureWebAppInfraDelegateConfig;
import io.harness.delegate.task.azure.appservice.webapp.ng.AzureWebAppRequestType;
import io.harness.delegate.task.azure.appservice.webapp.ng.request.AbstractWebAppTaskRequest;
import io.harness.delegate.task.azure.appservice.webapp.ng.response.AzureWebAppRequestResponse;
import io.harness.delegate.task.azure.common.AzureConnectorMapper;
import io.harness.delegate.task.azure.common.AzureLogCallbackProvider;
import io.harness.exception.InvalidArgumentsException;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CDP)
public class AzureWebAppRequestHandlerTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  private final AzureConfig azureConfig = AzureConfig.builder().build();
  private final AzureWebAppInfraDelegateConfig infraDelegateConfig = AzureWebAppInfraDelegateConfig.builder()
                                                                         .appName("app")
                                                                         .deploymentSlot("deploymentSlot")
                                                                         .resourceGroup("resourceGroup")
                                                                         .subscription("subscription")
                                                                         .build();

  @Mock private AzureLogCallbackProvider logCallbackProvider;
  @Mock private AzureWebAppRequestResponse requestResponse;
  @Mock private AzureConnectorMapper azureConnectorMapper;

  @InjectMocks
  @Spy
  private AzureWebAppRequestHandler<Test1AzureWebAppRequest> testRequestHandler =
      new AzureWebAppRequestHandler<Test1AzureWebAppRequest>() {
        @Override
        protected AzureWebAppRequestResponse execute(Test1AzureWebAppRequest taskRequest, AzureConfig azureConfig,
            AzureLogCallbackProvider logCallbackProvider) {
          // do nothing
          return requestResponse;
        }

        @Override
        protected Class<Test1AzureWebAppRequest> getRequestType() {
          return Test1AzureWebAppRequest.class;
        }
      };

  @Before
  public void setup() {
    doReturn(requestResponse)
        .when(testRequestHandler)
        .execute(any(Test1AzureWebAppRequest.class), eq(azureConfig), eq(logCallbackProvider));
    doReturn(azureConfig).when(azureConnectorMapper).toAzureConfig(any(AzureConnectorDTO.class));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testHandleRequest() {
    AzureWebAppRequestResponse response =
        testRequestHandler.handleRequest(new Test1AzureWebAppRequest(), logCallbackProvider);
    assertThat(response).isSameAs(requestResponse);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testHandleRequestInvalidType() {
    assertThatThrownBy(() -> testRequestHandler.handleRequest(new Test2AzureWebAppRequest(), logCallbackProvider))
        .isInstanceOf(InvalidArgumentsException.class);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testBuildAzureWebClientContext() {
    AzureWebClientContext clientContext =
        testRequestHandler.buildAzureWebClientContext(infraDelegateConfig, azureConfig);
    assertThat(clientContext.getAppName()).isEqualTo("app");
    assertThat(clientContext.getSubscriptionId()).isEqualTo("subscription");
    assertThat(clientContext.getResourceGroupName()).isEqualTo("resourceGroup");
    assertThat(clientContext.getAzureConfig()).isSameAs(azureConfig);
  }

  private class Test1AzureWebAppRequest extends AbstractWebAppTaskRequest {
    Test1AzureWebAppRequest() {
      super(null, null);
    }

    @Override
    public AzureWebAppInfraDelegateConfig getInfrastructure() {
      return infraDelegateConfig;
    }

    @Override
    public AzureWebAppRequestType getRequestType() {
      return AzureWebAppRequestType.SLOT_DEPLOYMENT;
    }
  }

  private class Test2AzureWebAppRequest extends AbstractWebAppTaskRequest {
    Test2AzureWebAppRequest() {
      super(null, null);
    }

    @Override
    public AzureWebAppInfraDelegateConfig getInfrastructure() {
      return infraDelegateConfig;
    }

    @Override
    public AzureWebAppRequestType getRequestType() {
      return AzureWebAppRequestType.SLOT_TRAFFIC_SHIFT;
    }
  }
}