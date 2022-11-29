package io.harness.delegate.task.pcf.request;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.pcf.CfAppSetupTimeDetails;
import io.harness.delegate.beans.pcf.CfServiceData;
import io.harness.delegate.task.pcf.CfCommandTypeNG;
import io.harness.delegate.task.pcf.response.TasInfraConfig;
import io.harness.pcf.model.CfCliVersion;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
@OwnedBy(HarnessTeam.CDP)
public class CfSwapRollbackCommandRequestNG extends CfRollbackCommandRequestNG{
    List<CfAppSetupTimeDetails> existingApplicationDetails;
    List<CfAppSetupTimeDetails> appDetailsToBeDownsized;
}
