package io.harness.delegate.task.artifacts.mappers;

import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.delegate.task.artifacts.azuremachineimage.AzureMachineImageDelegateResponse;

import lombok.experimental.UtilityClass;

@UtilityClass
public class AzureMachineImageResponseMapper {
  public static AzureMachineImageDelegateResponse toAzureMachineImageResponse(
      BuildDetailsInternal buildDetailsInternal) {
    return AzureMachineImageDelegateResponse.builder().name(buildDetailsInternal.getNumber()).build();
  }
}
