/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.appservice.deployment;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.azure.model.AzureConstants.START_DEPLOYMENT_SLOT;
import static io.harness.azure.model.AzureConstants.STOP_DEPLOYMENT_SLOT;
import static io.harness.delegate.task.azure.appservice.deployment.verifier.SlotStatusVerifier.SlotStatus.RUNNING;
import static io.harness.delegate.task.azure.appservice.deployment.verifier.SlotStatusVerifier.SlotStatus.STOPPED;
import static io.harness.delegate.task.azure.appservice.deployment.verifier.SlotStatusVerifier.SlotStatusVerifierType.START_VERIFIER;
import static io.harness.delegate.task.azure.appservice.deployment.verifier.SlotStatusVerifier.SlotStatusVerifierType.STOP_VERIFIER;
import static io.harness.delegate.task.azure.appservice.deployment.verifier.SlotStatusVerifier.SlotStatusVerifierType.SWAP_VERIFIER;
import static io.harness.rule.OwnerRule.ANIL;
import static io.harness.rule.OwnerRule.MLUKIC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.client.AzureMonitorClient;
import io.harness.azure.client.AzureWebClient;
import io.harness.azure.context.AzureWebClientContext;
import io.harness.azure.model.AzureConfig;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.azure.appservice.deployment.verifier.SlotStatusVerifier;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.runtime.azure.AzureAppServicesSlotSteadyStateException;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import com.azure.core.http.HttpHeaders;
import com.azure.core.http.HttpRequest;
import com.azure.core.http.rest.Response;
import com.azure.resourcemanager.appservice.models.DeploymentSlot;
import com.azure.resourcemanager.monitor.models.EventData;
import com.azure.resourcemanager.monitor.models.LocalizableString;
import com.google.common.util.concurrent.FakeTimeLimiter;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import reactor.core.publisher.Mono;

@OwnedBy(CDP)
public class SlotSteadyStateCheckerTest extends CategoryTest {
  @Mock private LogCallback mockLogCallback;
  @InjectMocks private SlotSteadyStateChecker slotSteadyStateChecker;
  private final TimeLimiter timeLimiter = new FakeTimeLimiter();

  private static final String SOURCE_SLOT = "sourceSlot";
  private static final String TARGET_SLOT = "targetSlot";
  private static final String SLOT_ID = "slotId";

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    on(slotSteadyStateChecker).set("timeLimiter", timeLimiter);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testStopSlotSteadyState() {
    AzureWebClient azureWebClient = mock(AzureWebClient.class);
    AzureMonitorClient azureMonitorClient = mock(AzureMonitorClient.class);

    AzureWebClientContext azureWebClientContext = getAzureWebClientContext();

    SlotStatusVerifier statusVerifier = SlotStatusVerifier.getStatusVerifier(STOP_VERIFIER.name(), mockLogCallback,
        SOURCE_SLOT, azureWebClient, azureMonitorClient, azureWebClientContext, null);

    DeploymentSlot deploymentSlot = mock(DeploymentSlot.class);
    doReturn(Optional.of(deploymentSlot))
        .when(azureWebClient)
        .getDeploymentSlotByName(eq(azureWebClientContext), eq(SOURCE_SLOT));
    doReturn(RUNNING.name()).when(deploymentSlot).state();

    ExecutorService executorService = Executors.newFixedThreadPool(1);
    executorService.submit(new SlotStopStatusTask(deploymentSlot));
    executorService.shutdown();

    slotSteadyStateChecker.waitUntilCompleteWithTimeout(1, 1, mockLogCallback, STOP_DEPLOYMENT_SLOT, statusVerifier);
    verify(deploymentSlot, Mockito.atLeast(1)).state();
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testStopSlotSteadyStateWithMonoResponse() {
    AzureWebClient azureWebClient = mock(AzureWebClient.class);
    AzureMonitorClient azureMonitorClient = mock(AzureMonitorClient.class);

    AzureWebClientContext azureWebClientContext = getAzureWebClientContext();

    Response<Void> response = new Response<Void>() {
      @Override
      public int getStatusCode() {
        return 200;
      }

      @Override
      public HttpHeaders getHeaders() {
        return null;
      }

      @Override
      public HttpRequest getRequest() {
        return null;
      }

      @Override
      public Void getValue() {
        return null;
      }
    };

    Mono<Response<Void>> responseMono = mock(Mono.class);
    doReturn(responseMono).when(responseMono).doOnError(any());
    doReturn(responseMono).when(responseMono).doOnSuccess(any());
    doReturn(response).when(responseMono).block(any());

    SlotStatusVerifier statusVerifier = SlotStatusVerifier.getStatusVerifier(STOP_VERIFIER.name(), mockLogCallback,
        SOURCE_SLOT, azureWebClient, azureMonitorClient, azureWebClientContext, responseMono);

    slotSteadyStateChecker.waitUntilCompleteWithTimeout(1, 1, mockLogCallback, STOP_DEPLOYMENT_SLOT, statusVerifier);
    verify(responseMono, Mockito.times(1)).block(any());
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testStartSlotSteadyState() {
    AzureWebClient azureWebClient = mock(AzureWebClient.class);
    AzureMonitorClient azureMonitorClient = mock(AzureMonitorClient.class);
    AzureWebClientContext azureWebClientContext = getAzureWebClientContext();

    SlotStatusVerifier statusVerifier = SlotStatusVerifier.getStatusVerifier(START_VERIFIER.name(), mockLogCallback,
        SOURCE_SLOT, azureWebClient, azureMonitorClient, azureWebClientContext, null);

    DeploymentSlot deploymentSlot = mock(DeploymentSlot.class);
    doReturn(Optional.of(deploymentSlot))
        .when(azureWebClient)
        .getDeploymentSlotByName(eq(azureWebClientContext), eq(SOURCE_SLOT));
    doReturn(RUNNING.name()).when(deploymentSlot).state();
    slotSteadyStateChecker.waitUntilCompleteWithTimeout(1, 5, mockLogCallback, START_DEPLOYMENT_SLOT, statusVerifier);
    verify(deploymentSlot, Mockito.atLeast(1)).state();

    // Failure
    doAnswer(invocation -> { throw new UncheckedTimeoutException(); }).when(deploymentSlot).state();
    assertThatThrownBy(()
                           -> slotSteadyStateChecker.waitUntilCompleteWithTimeout(
                               1, 5, mockLogCallback, START_DEPLOYMENT_SLOT, statusVerifier))
        .isInstanceOf(AzureAppServicesSlotSteadyStateException.class)
        .hasMessageContaining("Timed out waiting for executing operation");

    doAnswer(invocation -> { throw new Exception(); }).when(deploymentSlot).state();
    assertThatThrownBy(()
                           -> slotSteadyStateChecker.waitUntilCompleteWithTimeout(
                               1, 5, mockLogCallback, START_DEPLOYMENT_SLOT, statusVerifier))
        .isInstanceOf(AzureAppServicesSlotSteadyStateException.class)
        .hasMessageContaining("Error while waiting for executing operation");

    doReturn(Optional.empty()).when(azureWebClient).getDeploymentSlotByName(eq(azureWebClientContext), eq(SOURCE_SLOT));
    assertThatThrownBy(()
                           -> slotSteadyStateChecker.waitUntilCompleteWithTimeout(
                               1, 5, mockLogCallback, START_DEPLOYMENT_SLOT, statusVerifier))
        .isInstanceOf(AzureAppServicesSlotSteadyStateException.class)
        .hasMessageContaining("Unable to find deployment slot with name");
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testStartSlotSteadyStateWithMonoResponse() {
    AzureWebClient azureWebClient = mock(AzureWebClient.class);
    AzureMonitorClient azureMonitorClient = mock(AzureMonitorClient.class);
    AzureWebClientContext azureWebClientContext = getAzureWebClientContext();

    Response<Void> response = new Response<Void>() {
      @Override
      public int getStatusCode() {
        return 200;
      }

      @Override
      public HttpHeaders getHeaders() {
        return null;
      }

      @Override
      public HttpRequest getRequest() {
        return null;
      }

      @Override
      public Void getValue() {
        return null;
      }
    };

    Mono<Response<Void>> responseMono = mock(Mono.class);
    doReturn(responseMono).when(responseMono).doOnError(any());
    doReturn(responseMono).when(responseMono).doOnSuccess(any());
    doReturn(response).when(responseMono).block(any());

    SlotStatusVerifier statusVerifier = SlotStatusVerifier.getStatusVerifier(START_VERIFIER.name(), mockLogCallback,
        SOURCE_SLOT, azureWebClient, azureMonitorClient, azureWebClientContext, responseMono);

    slotSteadyStateChecker.waitUntilCompleteWithTimeout(1, 5, mockLogCallback, START_DEPLOYMENT_SLOT, statusVerifier);
    verify(responseMono, Mockito.times(1)).block(any());

    // Failure
    doAnswer(invocation -> { throw new UncheckedTimeoutException(); }).when(responseMono).block(any());
    assertThatThrownBy(()
                           -> slotSteadyStateChecker.waitUntilCompleteWithTimeout(
                               1, 5, mockLogCallback, START_DEPLOYMENT_SLOT, statusVerifier))
        .isInstanceOf(AzureAppServicesSlotSteadyStateException.class)
        .hasMessageContaining("Timed out waiting for executing operation");

    doAnswer(invocation -> { throw new RuntimeException(); }).when(responseMono).block(any());
    assertThatThrownBy(()
                           -> slotSteadyStateChecker.waitUntilCompleteWithTimeout(
                               1, 5, mockLogCallback, START_DEPLOYMENT_SLOT, statusVerifier))
        .isInstanceOf(AzureAppServicesSlotSteadyStateException.class)
        .hasMessageContaining("Error while waiting for executing operation");
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testStartSlotSteadyStateFailed() {
    AzureWebClient azureWebClient = mock(AzureWebClient.class);
    AzureMonitorClient azureMonitorClient = mock(AzureMonitorClient.class);
    AzureWebClientContext azureWebClientContext = getAzureWebClientContext();

    Mono<Response<Void>> responseMono = mock(Mono.class);
    doReturn(responseMono).when(responseMono).doOnError(any());
    doReturn(responseMono).when(responseMono).doOnSuccess(any());
    doThrow(new RuntimeException("some response error")).when(responseMono).block(any());

    SlotStatusVerifier statusVerifier = SlotStatusVerifier.getStatusVerifier(START_VERIFIER.name(), mockLogCallback,
        SOURCE_SLOT, azureWebClient, azureMonitorClient, azureWebClientContext, responseMono);

    DeploymentSlot deploymentSlot = mock(DeploymentSlot.class);
    doReturn(Optional.of(deploymentSlot))
        .when(azureWebClient)
        .getDeploymentSlotByName(eq(azureWebClientContext), eq(SOURCE_SLOT));
    doReturn(STOPPED.name()).when(deploymentSlot).state();

    assertThatThrownBy(()
                           -> slotSteadyStateChecker.waitUntilCompleteWithTimeout(
                               10, 1, mockLogCallback, START_DEPLOYMENT_SLOT, statusVerifier))
        .isInstanceOf(AzureAppServicesSlotSteadyStateException.class)
        .hasMessageContaining("some response error");
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testSwapSlotSteadyState() {
    AzureWebClient azureWebClient = mock(AzureWebClient.class);
    AzureMonitorClient azureMonitorClient = mock(AzureMonitorClient.class);
    AzureWebClientContext azureWebClientContext = getAzureWebClientContext();

    DeploymentSlot deploymentSlot = mock(DeploymentSlot.class);
    doReturn(Optional.of(deploymentSlot))
        .when(azureWebClient)
        .getDeploymentSlotByName(eq(azureWebClientContext), eq(SOURCE_SLOT));
    doReturn(SLOT_ID).when(deploymentSlot).id();

    doReturn(createEventData())
        .when(azureMonitorClient)
        .listEventDataWithAllPropertiesByResourceId(eq(azureWebClientContext.getAzureConfig()),
            eq(azureWebClientContext.getSubscriptionId()), any(), any(), eq(SLOT_ID));

    doNothing().when(mockLogCallback).saveExecutionLog(any());

    SlotStatusVerifier statusVerifier = SlotStatusVerifier.getStatusVerifier(SWAP_VERIFIER.name(), mockLogCallback,
        SOURCE_SLOT, azureWebClient, azureMonitorClient, azureWebClientContext, null);

    slotSteadyStateChecker.waitUntilCompleteWithTimeout(1, 1, mockLogCallback, STOP_DEPLOYMENT_SLOT, statusVerifier);
    verify(azureMonitorClient, Mockito.atLeast(1))
        .listEventDataWithAllPropertiesByResourceId(eq(azureWebClientContext.getAzureConfig()),
            eq(azureWebClientContext.getSubscriptionId()), any(), any(), eq(SLOT_ID));
    assertThat(statusVerifier.getSteadyState().equalsIgnoreCase(RUNNING.name())).isTrue();
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testSwapSlotSteadyStateWithMonoResponse() {
    AzureWebClient azureWebClient = mock(AzureWebClient.class);
    AzureMonitorClient azureMonitorClient = mock(AzureMonitorClient.class);
    AzureWebClientContext azureWebClientContext = getAzureWebClientContext();

    DeploymentSlot deploymentSlot = mock(DeploymentSlot.class);
    doReturn(Optional.of(deploymentSlot))
        .when(azureWebClient)
        .getDeploymentSlotByName(eq(azureWebClientContext), eq(SOURCE_SLOT));
    doReturn(SLOT_ID).when(deploymentSlot).id();

    doReturn(createEventData())
        .when(azureMonitorClient)
        .listEventDataWithAllPropertiesByResourceId(eq(azureWebClientContext.getAzureConfig()),
            eq(azureWebClientContext.getSubscriptionId()), any(), any(), eq(SLOT_ID));

    doNothing().when(mockLogCallback).saveExecutionLog(any());

    Response<Void> response = new Response<Void>() {
      @Override
      public int getStatusCode() {
        return 200;
      }

      @Override
      public HttpHeaders getHeaders() {
        return null;
      }

      @Override
      public HttpRequest getRequest() {
        return null;
      }

      @Override
      public Void getValue() {
        return null;
      }
    };

    Mono<Response<Void>> responseMono = mock(Mono.class);
    doReturn(responseMono).when(responseMono).doOnError(any());
    doReturn(responseMono).when(responseMono).doOnSuccess(any());
    doReturn(response).when(responseMono).block(any());

    SlotStatusVerifier statusVerifier = SlotStatusVerifier.getStatusVerifier(SWAP_VERIFIER.name(), mockLogCallback,
        SOURCE_SLOT, azureWebClient, azureMonitorClient, azureWebClientContext, responseMono);

    slotSteadyStateChecker.waitUntilCompleteWithTimeout(1, 1, mockLogCallback, STOP_DEPLOYMENT_SLOT, statusVerifier);
    verify(responseMono, Mockito.times(1)).block(any());
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testSlotSwapper() {
    AzureWebClient azureWebClient = mock(AzureWebClient.class);
    AzureWebClientContext azureWebClientContext = getAzureWebClientContext();

    SwapSlotTask swapper =
        new SwapSlotTask(SOURCE_SLOT, TARGET_SLOT, azureWebClient, azureWebClientContext, mockLogCallback);

    ExecutorService executorService = Executors.newFixedThreadPool(1);
    executorService.submit(swapper);
    executorService.shutdown();

    try {
      Thread.sleep(5000);
    } catch (InterruptedException exception) {
      exception.printStackTrace();
    }
    verify(azureWebClient).swapDeploymentSlotsAsync(eq(azureWebClientContext), eq(SOURCE_SLOT), eq(TARGET_SLOT));
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testSlotStatusVerifierFail() {
    AzureWebClient azureWebClient = mock(AzureWebClient.class);
    AzureMonitorClient azureMonitorClient = mock(AzureMonitorClient.class);
    AzureWebClientContext azureWebClientContext = getAzureWebClientContext();

    assertThatThrownBy(()
                           -> SlotStatusVerifier.getStatusVerifier("verifierType", mockLogCallback, SOURCE_SLOT,
                               azureWebClient, azureMonitorClient, azureWebClientContext, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("No slot status verifier defined for");
  }

  private List<EventData> createEventData() {
    List<EventData> eventDataList = new ArrayList<>();
    EventData slotSwap = mock(EventData.class);
    LocalizableString slotSwapLocalizableString = mock(LocalizableString.class);
    LocalizableString slotSwapStatusLocalizableString = mock(LocalizableString.class);
    doReturn(slotSwapLocalizableString).when(slotSwap).operationName();
    doReturn(slotSwapStatusLocalizableString).when(slotSwap).status();
    doReturn("Succeeded").when(slotSwapStatusLocalizableString).localizedValue();
    doReturn("Microsoft.Web/sites/slots/SlotSwap/action").when(slotSwapLocalizableString).localizedValue();
    eventDataList.add(slotSwap);

    EventData endSlotWarmUp = mock(EventData.class);
    LocalizableString endSlotWarmUpLocalizableString = mock(LocalizableString.class);
    LocalizableString endSlotWarmUpStatusLocalizableString = mock(LocalizableString.class);
    doReturn(endSlotWarmUpLocalizableString).when(endSlotWarmUp).operationName();
    doReturn(endSlotWarmUpStatusLocalizableString).when(endSlotWarmUp).status();
    doReturn("Succeeded").when(endSlotWarmUpStatusLocalizableString).localizedValue();
    doReturn("Microsoft.Web/sites/slots/EndSlotWarmup/action").when(endSlotWarmUpLocalizableString).localizedValue();
    eventDataList.add(endSlotWarmUp);

    EventData startSlotWarmUp = mock(EventData.class);
    LocalizableString startSlotWarmUpLocalizableString = mock(LocalizableString.class);
    LocalizableString startSlotWarmUpStatusLocalizableString = mock(LocalizableString.class);
    doReturn(startSlotWarmUpLocalizableString).when(startSlotWarmUp).operationName();
    doReturn(startSlotWarmUpStatusLocalizableString).when(startSlotWarmUp).status();
    doReturn("Succeeded").when(startSlotWarmUpStatusLocalizableString).localizedValue();
    doReturn("Microsoft.Web/sites/slots/StartSlotWarmup/action")
        .when(startSlotWarmUpLocalizableString)
        .localizedValue();
    eventDataList.add(startSlotWarmUp);

    EventData applyConfig = mock(EventData.class);
    LocalizableString applyConfigLocalizableString = mock(LocalizableString.class);
    LocalizableString applyConfigStatusLocalizableString = mock(LocalizableString.class);
    doReturn(applyConfigLocalizableString).when(applyConfig).operationName();
    doReturn(applyConfigStatusLocalizableString).when(applyConfig).status();
    doReturn("Succeeded").when(applyConfigStatusLocalizableString).localizedValue();
    doReturn("Microsoft.Web/sites/slots/ApplySlotConfig/action").when(applyConfigLocalizableString).localizedValue();
    eventDataList.add(applyConfig);

    EventData notStarted = mock(EventData.class);
    LocalizableString notStartedLocalizableString = mock(LocalizableString.class);
    LocalizableString notStartedStatusLocalizableString = mock(LocalizableString.class);
    doReturn(notStartedLocalizableString).when(notStarted).operationName();
    doReturn(notStartedStatusLocalizableString).when(notStarted).status();
    doReturn("Succeeded").when(notStartedStatusLocalizableString).localizedValue();
    doReturn("Microsoft.Web/sites/slots/RandomAction/action").when(notStartedLocalizableString).localizedValue();
    eventDataList.add(notStarted);

    EventData slotSwapWrapper = mock(EventData.class);
    LocalizableString slotSwapWrapperLocalizableString = mock(LocalizableString.class);
    LocalizableString slotSwapWrapperStatusLocalizableString = mock(LocalizableString.class);
    doReturn(slotSwapLocalizableString).when(slotSwap).operationName();
    doReturn(slotSwapStatusLocalizableString).when(slotSwap).status();
    doReturn("Succeeded").when(slotSwapStatusLocalizableString).localizedValue();
    doReturn("Swap Web App Slots").when(slotSwapLocalizableString).localizedValue();
    eventDataList.add(slotSwap);

    return eventDataList;
  }

  private static class SlotStopStatusTask implements Runnable {
    private final DeploymentSlot deploymentSlot;
    SlotStopStatusTask(DeploymentSlot deploymentSlot) {
      this.deploymentSlot = deploymentSlot;
    }

    @Override
    public void run() {
      try {
        doReturn(RUNNING.name()).when(deploymentSlot).state();
        Thread.sleep(3000);
        doReturn(STOPPED.name()).when(deploymentSlot).state();
      } catch (InterruptedException exception) {
        exception.printStackTrace();
      }
    }
  }

  private AzureWebClientContext getAzureWebClientContext() {
    return AzureWebClientContext.builder()
        .azureConfig(AzureConfig.builder().build())
        .resourceGroupName("rg")
        .appName("app")
        .subscriptionId("subId")
        .build();
  }
}
