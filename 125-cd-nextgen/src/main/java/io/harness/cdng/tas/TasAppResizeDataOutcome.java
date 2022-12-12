package io.harness.cdng.tas;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.pcf.CfServiceData;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;
import io.harness.pms.sdk.core.data.Outcome;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.CDP)
@Value
@Builder
@TypeAlias("TasAppResizeDataOutcome")
@JsonTypeName("TasAppResizeDataOutcome")
@RecasterAlias("io.harness.cdng.tas.beans.TasAppResizeDataOutcome")
public class TasAppResizeDataOutcome implements Outcome, ExecutionSweepingOutput {
    List<CfServiceData> instanceData;
}
