package io.harness.delegate.task.pcf.response;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateMetaInfo;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.pcf.CfAppSetupTimeDetails;
import io.harness.delegate.beans.pcf.CfDeployCommandResult;
import io.harness.delegate.beans.pcf.CfProdAppInfo;
import io.harness.logging.CommandExecutionStatus;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.NonFinal;

@Data
@Builder
@OwnedBy(HarnessTeam.CDP)
public class CfBasicSetupResponseNG implements CfCommandResponseNG {
  @NonFinal DelegateMetaInfo delegateMetaInfo;
  @NonFinal UnitProgressData unitProgressData;
  CommandExecutionStatus commandExecutionStatus;
  String errorMessage;

  private CfAppSetupTimeDetails newApplicationDetails;

  // require for BG
  private CfAppSetupTimeDetails downsizeDetails;
  // require only for BG
  private CfAppSetupTimeDetails mostRecentInactiveAppVersion;

  // private Integer totalPreviousInstanceCount;

  private CfProdAppInfo currentProdInfo;

  //  private boolean versioningChanged;
  //  private boolean nonVersioning;
  //  private Integer activeAppRevision;
  //  private String existingAppNamingStrategy;

  @Override
  public void setDelegateMetaInfo(DelegateMetaInfo metaInfo) {
    this.delegateMetaInfo = metaInfo;
  }

  @Override
  public void setCommandUnitsProgress(UnitProgressData unitProgressData) {
    this.unitProgressData = unitProgressData;
  }
}
