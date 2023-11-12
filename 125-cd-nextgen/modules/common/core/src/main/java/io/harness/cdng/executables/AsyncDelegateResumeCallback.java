/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.executables;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.common.beans.StepDelegateInfo;
import io.harness.cdng.common.beans.StepDetailsDelegateInfo;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.execution.SdkGraphVisualizationDataService;
import io.harness.tasks.ResponseData;
import io.harness.waiter.OldNotifyCallback;

import com.google.inject.Inject;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@Value
@Builder
@Slf4j
public class AsyncDelegateResumeCallback implements OldNotifyCallback {
  @Inject SdkGraphVisualizationDataService sdkGraphVisualizationDataService;
  @Inject AsyncDelegateInfoHelper asyncDelegateInfoHelper;

  // graph details service
  byte[] ambianceBytes;
  String taskId;
  String taskName;

  @Override
  public void notify(Map<String, ResponseData> response) {
    // THis means new way of event got called and ambiance should be present
    notifyWithError(response);
  }

  @Override
  public void notifyError(Map<String, ResponseData> response) {
    notifyWithError(response);
  }

  private void notifyWithError(Map<String, ResponseData> responses) {
    try {
      if (responses == null) {
        return;
      }
      if (responses.entrySet()
              .stream()
              .filter(entry -> taskId.equals(entry.getKey()))
              .map(Map.Entry::getValue)
              .findFirst()
              .isEmpty()) {
        return;
      }
      Ambiance ambiance = Ambiance.parseFrom(ambianceBytes);
      String accountId = AmbianceUtils.getAccountId(ambiance);
      Optional<StepDelegateInfo> stepDelegateInfo =
          asyncDelegateInfoHelper.getDelegateInformationForGivenTask(taskName, taskId, accountId);
      if (stepDelegateInfo.isPresent()) {
        List<StepDelegateInfo> stepDelegateInfos = Collections.singletonList(stepDelegateInfo.get());
        sdkGraphVisualizationDataService.publishStepDetailInformation(
            ambiance, StepDetailsDelegateInfo.builder().stepDelegateInfos(stepDelegateInfos).build(), taskName);
      }
    } catch (InvalidProtocolBufferException e) {
      log.warn("Not able to deserialize Ambiance bytes. Progress Callback will not be executed");
    }
  }

  @Override
  public void notifyTimeout(Map<String, ResponseData> responseMap) {
    notifyWithError(responseMap);
  }
}
