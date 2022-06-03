package io.harness.cdng.gitops;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;
import io.harness.pms.sdk.core.data.Outcome;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(CDP)
@Value
@Builder
@TypeAlias("CreatePROutcome")
@JsonTypeName("CreatePROutcome")
@RecasterAlias("io.harness.cdng.gitops.CreatePROutcome")
public class CreatePROutcome implements Outcome, ExecutionSweepingOutput {
  String prLink;
  List<String> changedFiles;
  String commitId;
}
