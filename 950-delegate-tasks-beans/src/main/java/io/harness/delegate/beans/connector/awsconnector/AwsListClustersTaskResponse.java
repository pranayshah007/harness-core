package io.harness.delegate.beans.connector.awsconnector;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateMetaInfo;
import io.harness.logging.CommandExecutionStatus;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@OwnedBy(HarnessTeam.CDP)
public class AwsListClustersTaskResponse implements AwsDelegateTaskResponse {
    private CommandExecutionStatus commandExecutionStatus;
    private DelegateMetaInfo delegateMetaInfo;
    private List<String> clusters;
}
