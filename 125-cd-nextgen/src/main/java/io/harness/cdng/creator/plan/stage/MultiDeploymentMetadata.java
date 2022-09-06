package io.harness.cdng.creator.plan.stage;

import io.harness.cdng.pipeline.beans.MultiDeploymentStepParameters;
import io.harness.pms.contracts.advisers.AdviserObtainment;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MultiDeploymentMetadata {
  String multiDeploymentNodeId;
  List<AdviserObtainment> adviserObtainments;
  String strategyNodeName;
  String strategyNodeIdentifier;
  MultiDeploymentStepParameters multiDeploymentStepParameters;
}
