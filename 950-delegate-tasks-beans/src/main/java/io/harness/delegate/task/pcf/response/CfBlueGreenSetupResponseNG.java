package io.harness.delegate.task.pcf.response;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateMetaInfo;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.pcf.TasApplicationInfo;
import io.harness.logging.CommandExecutionStatus;

import lombok.Builder;
import lombok.Data;
import lombok.experimental.NonFinal;

@Data
@Builder
@OwnedBy(HarnessTeam.CDP)
public class CfBlueGreenSetupResponseNG implements CfCommandResponseNG {
  @NonFinal DelegateMetaInfo delegateMetaInfo;
  @NonFinal UnitProgressData unitProgressData;
  CommandExecutionStatus commandExecutionStatus;
  String errorMessage;
  private TasApplicationInfo newApplicationInfo;
  private TasApplicationInfo activeApplicationInfo;
  private TasApplicationInfo inActiveApplicationInfo;

  @Override
  public void setDelegateMetaInfo(DelegateMetaInfo metaInfo) {
    this.delegateMetaInfo = metaInfo;
  }

  @Override
  public void setCommandUnitsProgress(UnitProgressData unitProgressData) {
    this.unitProgressData = unitProgressData;
  }
}
