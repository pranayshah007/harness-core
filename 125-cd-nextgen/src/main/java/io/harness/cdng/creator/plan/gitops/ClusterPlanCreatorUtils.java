package io.harness.cdng.creator.plan.gitops;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.creator.plan.PlanCreatorConstants;
import io.harness.cdng.environment.yaml.EnvironmentPlanCreatorConfig;
import io.harness.cdng.gitops.steps.ClusterStepParameters;
import io.harness.cdng.gitops.steps.GitopsClustersStep;
import io.harness.data.structure.UUIDGenerator;
import io.harness.pms.contracts.facilitators.FacilitatorObtainment;
import io.harness.pms.contracts.facilitators.FacilitatorType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.sdk.core.plan.PlanNode;
import io.harness.pms.yaml.ParameterField;

import java.util.HashSet;
import java.util.Set;
import javax.validation.constraints.NotNull;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.GITOPS)
@UtilityClass
public class ClusterPlanCreatorUtils {
  @NotNull
  public PlanNode getGitopsClustersStepPlanNode(EnvironmentPlanCreatorConfig envConfig) {
    return PlanNode.builder()
        .uuid(UUIDGenerator.generateUuid())
        .name(PlanCreatorConstants.GITOPS_INFRA_NODE_NAME)
        .identifier(PlanCreatorConstants.SPEC_IDENTIFIER)
        .stepType(GitopsClustersStep.STEP_TYPE)
        .stepParameters(getStepParams(envConfig))
        .facilitatorObtainment(
            FacilitatorObtainment.newBuilder()
                .setType(FacilitatorType.newBuilder().setType(OrchestrationFacilitatorType.SYNC).build())
                .build())
        .build();
  }

  private ClusterStepParameters getStepParams(EnvironmentPlanCreatorConfig envConfig) {
    checkNotNull(envConfig, "environment must be present");

    final String envRef = fetchEnvRef(envConfig);
    if (envConfig.isDeployToAll()) {
      return ClusterStepParameters.WithEnv(envRef);
    }

    checkArgument(isNotEmpty(envConfig.getGitOpsClusterRefs()),
        "list of gitops clusterRefs must be provided when not deploying to all clusters");

    return ClusterStepParameters.WithEnvAndClusterRefs(envRef, getClusterRefs(envConfig));
  }

  private Set<String> getClusterRefs(EnvironmentPlanCreatorConfig config) {
    return new HashSet<>(config.getGitOpsClusterRefs());
  }

  private String fetchEnvRef(EnvironmentPlanCreatorConfig config) {
    final ParameterField<String> environmentRef = config.getEnvironmentRef();
    checkNotNull(environmentRef, "environment ref must be present");
    checkArgument(!environmentRef.isExpression(), "environment ref not resolved yet");
    return (String) environmentRef.fetchFinalValue();
  }
}
