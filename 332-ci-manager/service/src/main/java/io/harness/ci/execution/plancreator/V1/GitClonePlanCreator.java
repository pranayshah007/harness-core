/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.plancreator.V1;

import static io.harness.ci.commonconstants.CIExecutionConstants.GIT_CLONE_STEP_ID;
import static io.harness.ci.commonconstants.CIExecutionConstants.GIT_CLONE_STEP_NAME;
import static io.harness.ci.commonconstants.CIExecutionConstants.STEP_MOUNT_PATH;
import static io.harness.data.structure.UUIDGenerator.generateUuid;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.steps.nodes.GitCloneStepNode;
import io.harness.beans.steps.stepinfo.GitCloneStepInfo;
import io.harness.ci.plan.creator.step.CIPMSStepPlanCreatorV2;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.core.timeout.Timeout;
import io.harness.yaml.extended.ci.codebase.CodeBase;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(HarnessTeam.CI)
public class GitClonePlanCreator extends CIPMSStepPlanCreatorV2<GitCloneStepNode> {
  private static final String ONE_HOUR = "1h";

  public Pair<PlanCreationResponse, JsonNode> createPlan(PlanCreationContext ctx, CodeBase codebase, String childID) {
    // create GitCloneStepNode
    GitCloneStepNode gitCloneStepNode = getStepNode(codebase);
    // create JsonNode
    JsonNode jsonNode = getJsonNode(gitCloneStepNode);
    // create Plan node
    PlanCreationResponse planCreationResponse = createInternalStepPlan(ctx, gitCloneStepNode, childID);
    return Pair.of(planCreationResponse, jsonNode);
  }

  private GitCloneStepNode getStepNode(CodeBase codeBase) {
    if (codeBase == null) {
      throw new CIStageExecutionException("Codebase is mandatory with enabled cloneCodebase flag");
    }
    GitCloneStepInfo gitCloneStepInfo = GitCloneStepInfo.builder()
                                            .identifier(GIT_CLONE_STEP_ID)
                                            .name(GIT_CLONE_STEP_NAME)
                                            .repoName(codeBase.getRepoName())
                                            .connectorRef(codeBase.getConnectorRef())
                                            .depth(codeBase.getDepth())
                                            .sslVerify(codeBase.getSslVerify())
                                            .resources(codeBase.getResources())
                                            .build(codeBase.getBuild())
                                            .cloneDirectory(ParameterField.createValueField(STEP_MOUNT_PATH))
                                            .build();

    return GitCloneStepNode.builder()
        .identifier(GIT_CLONE_STEP_ID)
        .name(GIT_CLONE_STEP_NAME)
        .timeout(ParameterField.createValueField(Timeout.builder().timeoutString(ONE_HOUR).build()))
        .uuid(generateUuid())
        .type(GitCloneStepNode.StepType.GitClone)
        .gitCloneStepInfo(gitCloneStepInfo)
        .build();
  }

  @Override
  public Set<String> getSupportedStepTypes() {
    return null;
  }

  @Override
  public Class<GitCloneStepNode> getFieldClass() {
    return GitCloneStepNode.class;
  }
}
