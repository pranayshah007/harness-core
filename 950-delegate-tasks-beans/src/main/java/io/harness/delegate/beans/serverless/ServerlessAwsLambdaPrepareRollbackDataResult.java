package io.harness.delegate.beans.serverless;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(HarnessTeam.CDP)
public class ServerlessAwsLambdaPrepareRollbackDataResult implements ServerlessPrepareRollbackDataResult {
  private String previousVersionTimeStamp;
  private boolean isFirstDeployment;
  private String errorMessage;
}