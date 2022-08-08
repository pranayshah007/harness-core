/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.appservice.deployment.verifier;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.AzureServiceCallBack;
import io.harness.azure.client.AzureMonitorClient;
import io.harness.azure.client.AzureWebClient;
import io.harness.azure.context.AzureWebClientContext;
import io.harness.azure.model.AzureConfig;
import io.harness.delegate.task.azure.appservice.deployment.context.SwapSlotStatusVerifierContext;
import io.harness.logging.LogCallback;

import com.microsoft.azure.management.monitor.EventData;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.joda.time.DateTime;

@OwnedBy(CDP)
public class SwapSlotStatusVerifier extends SlotStatusVerifier {
  private final DateTime startTime;
  private final AzureMonitorClient azureMonitorClient;
  private final Set<SwapSlotEvent> seenBefore = new HashSet<>();
  private SwapSlotAction previousHighestAction = SwapSlotAction.NOT_STARTED;

  public SwapSlotStatusVerifier(LogCallback logCallback, String slotName, AzureWebClient azureWebClient,
      AzureMonitorClient azureMonitorClient, AzureWebClientContext azureWebClientContext,
      AzureServiceCallBack restCallBack) {
    super(logCallback, slotName, azureWebClient, azureWebClientContext, restCallBack);
    startTime = DateTime.now().minusMinutes(1);
    this.azureMonitorClient = azureMonitorClient;
  }

  public SwapSlotStatusVerifier(SwapSlotStatusVerifierContext context) {
    super(context.getLogCallback(), context.getSlotName(), context.getAzureWebClient(),
        context.getAzureWebClientContext(), context.getRestCallBack());
    startTime = DateTime.now().minusMinutes(1);
    this.azureMonitorClient = context.getAzureMonitorClient();
  }

  @Override
  public boolean hasReachedSteadyState() {
    String slotId = getDeploymentSlot().id();
    AzureConfig azureConfig = azureWebClientContext.getAzureConfig();
    String subscriptionId = azureWebClientContext.getSubscriptionId();
    List<EventData> eventData = azureMonitorClient.listEventDataWithAllPropertiesByResourceId(
        azureConfig, subscriptionId, startTime, DateTime.now(), slotId);
    if (eventData.isEmpty()) {
      return false;
    }

    // the oldest event is at the end
    for (int index = eventData.size() - 1; index >= 0; index--) {
      EventData event = eventData.get(index);
      SwapSlotEvent currentEvent = SwapSlotEvent.getSlotEvent(event);
      if (SwapSlotEvent.isSuccessEvent(currentEvent)) {
        return true;
      }

      if (!seenBefore.contains(currentEvent) || SwapSlotAction.compare(currentEvent.action, previousHighestAction)) {
        if (SwapSlotAction.shouldLog(currentEvent.action)) {
          logCallback.saveExecutionLog(String.format("Operation name : [%s]%nStatus : [%s]%nDescription : [%s]",
              currentEvent.action, currentEvent.status, event.description()));
        }
        previousHighestAction = currentEvent.action;
        seenBefore.add(currentEvent);
      }
    }
    return false;
  }

  @Override
  public String getSteadyState() {
    return "Running";
  }

  private static class SwapSlotEvent {
    private static final String successStatus = "Succeeded";
    private final SwapSlotAction action;
    private final String status;

    SwapSlotEvent(SwapSlotAction action, String status) {
      this.action = action;
      this.status = status;
    }

    public static SwapSlotEvent getSlotEvent(EventData event) {
      String action = event.operationName().localizedValue();
      String status = event.status().localizedValue();
      return new SwapSlotEvent(SwapSlotAction.getSlotAction(action), status);
    }

    public static boolean isSuccessEvent(SwapSlotEvent slotEvent) {
      return slotEvent.action == SwapSlotAction.SLOT_SWAP_WRAPPER && successStatus.equals(slotEvent.status);
    }

    @Override
    public int hashCode() {
      return Objects.hash(action, status);
    }

    @Override
    public boolean equals(Object obj) {
      if (obj.getClass() != this.getClass()) {
        return false;
      }

      final SwapSlotEvent other = (SwapSlotEvent) obj;
      return Objects.equals(this.action, other.action) || Objects.equals(this.status, other.status);
    }
  }

  private enum SwapSlotAction {
    NOT_STARTED(""),
    APPLY_SLOT_CONFIGURATION("Microsoft.Web/sites/slots/ApplySlotConfig/action"),
    START_SLOT_WARM_UP("Microsoft.Web/sites/slots/StartSlotWarmup/action"),
    END_SLOT_WARM_UP("Microsoft.Web/sites/slots/EndSlotWarmup/action"),
    SLOT_SWAP_ACTION("Microsoft.Web/sites/slots/SlotSwap/action"),
    SLOT_SWAP_WRAPPER("Swap Web App Slots");

    private final String action;
    SwapSlotAction(String action) {
      this.action = action;
    }

    public static boolean compare(SwapSlotAction action1, SwapSlotAction action2) {
      return action1.ordinal() >= action2.ordinal();
    }

    public static SwapSlotAction getSlotAction(String localizedAction) {
      for (SwapSlotAction actionEnum : values()) {
        if (actionEnum.action.contains(localizedAction)) {
          return actionEnum;
        }
      }
      return NOT_STARTED;
    }

    public static boolean shouldLog(SwapSlotAction action) {
      return !NOT_STARTED.equals(action) && !SLOT_SWAP_WRAPPER.equals(action);
    }
  }
}
