package io.harness.cdng.gitops.beans;

import static io.harness.annotations.dev.HarnessTeam.GITOPS;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.gitops.models.Application;
import io.harness.pms.sdk.core.data.Outcome;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import org.codehaus.commons.nullanalysis.NotNull;

@Value
@JsonTypeName("gitOpsLinkedAppsOutcome")
@RecasterAlias("io.harness.cdng.gitops.beans.GitOpsLinkedAppsOutcome")
@Builder
@OwnedBy(GITOPS)
public class GitOpsLinkedAppsOutcome implements Outcome {
  @NotNull List<Application> apps;
}
