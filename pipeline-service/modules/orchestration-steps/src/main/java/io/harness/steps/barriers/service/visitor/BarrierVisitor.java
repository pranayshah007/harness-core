/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.steps.barriers.service.visitor;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.steps.StepSpecTypeConstants;
import io.harness.steps.barriers.beans.BarrierPositionInfo;
import io.harness.steps.barriers.beans.BarrierSetupInfo;
import io.harness.steps.barriers.beans.StageDetail;
import io.harness.walktree.beans.VisitElementResult;
import io.harness.walktree.visitor.DummyVisitableElement;
import io.harness.walktree.visitor.SimpleVisitor;

import com.google.api.client.util.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import lombok.Getter;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TEMPLATE_LIBRARY})
@Singleton
@OwnedBy(HarnessTeam.PIPELINE)
public class BarrierVisitor extends SimpleVisitor<DummyVisitableElement> {
  private static final String BARRIER_TYPE = "Barrier";

  private static final String FLOW_CONTROL_FIELD = "flowControl";
  private static final String STAGE_FIELD = "stage";
  private static final String BARRIERS_FIELD = "barriers";
  private static final String SPEC_FIELD = "spec";
  private static final String BARRIER_REF_FIELD = "barrierRef";

  private final Map<String, BarrierSetupInfo> barrierIdentifierMap;
  @Getter private final Map<String, List<BarrierPositionInfo.BarrierPosition>> barrierPositionInfoMap;

  // state variable
  private String stageName; // this field will be changed each time we encounter a stage
  private String stageIdentifier; // this field will be changed each time we encounter a stage
  private String stageSetupId; // this field will be changed each time we encounter a stage

  @Inject
  public BarrierVisitor(Injector injector) {
    super(injector);
    this.barrierIdentifierMap = new HashMap<>();
    this.barrierPositionInfoMap = new HashMap<>();
    this.stageName = null;
    this.stageIdentifier = null;
  }

  @Override
  public VisitElementResult visitElement(Object currentElement) {
    YamlNode element = (YamlNode) currentElement;

    // Skip barrier initialization for pipeline stage
    if (StepSpecTypeConstants.PIPELINE_STAGE.equals(element.getType())) {
      return VisitElementResult.SKIP_SUBTREE;
    }

    addMetadataIfFlowControlNode(element);
    addMetadataIfStageNode(element);
    addMetadataIfBarrierStep(element);

    return VisitElementResult.CONTINUE;
  }

  /**
   * If condition is met, populates <code>barrierIdentifierMap</code> from flowControl field
   * @param element current yaml node
   */
  private void addMetadataIfFlowControlNode(YamlNode element) {
    // let's instantiate the map first from flowControl field in yaml
    YamlField flowControlField = element.nextSiblingNodeFromParentObject(FLOW_CONTROL_FIELD);
    if (flowControlField != null) {
      YamlField barriersField = flowControlField.getNode().getField(BARRIERS_FIELD);
      if (barriersField != null) {
        List<YamlNode> barriers = barriersField.getNode().asArray();
        for (YamlNode barrier : barriers) {
          String identifier = barrier.getIdentifier();
          String name = barrier.getName();
          barrierIdentifierMap.putIfAbsent(
              identifier, BarrierSetupInfo.builder().identifier(identifier).name(name).stages(new HashSet<>()).build());

          barrierPositionInfoMap.putIfAbsent(identifier, new ArrayList<>());
        }
      }
    }
  }

  /**
   * If condition is met, populates the <code>stageName</code> field
   * @param element current yaml node
   */
  private void addMetadataIfStageNode(YamlNode element) {
    // let's obtain the current stage name by checking if the parent is a stage node
    if (element.nextSiblingNodeFromParentObject(STAGE_FIELD) != null) {
      stageName = element.getName();
      stageIdentifier = element.getIdentifier();
      stageSetupId = element.getUuid();
    }
  }

  /**
   * If condition is met, populates <code>barrierIdentifierMap</code> with stage in which barrier was found
   * @param element current yaml node
   */
  private void addMetadataIfBarrierStep(YamlNode element) {
    String type = element.getType();
    if (BARRIER_TYPE.equals(type)) {
      String identifier = obtainBarrierIdentifierFromStep(element);
      if (!barrierIdentifierMap.containsKey(identifier)) {
        throw new InvalidRequestException(
            String.format("Barrier Identifier %s was not present in flowControl", identifier));
      }
      barrierIdentifierMap.get(identifier)
          .getStages()
          .add(StageDetail.builder()
                   .name(Preconditions.checkNotNull(stageName, "Stage name should not be null"))
                   .identifier(Preconditions.checkNotNull(stageIdentifier, "Stage identifier should not be null"))
                   .build());

      barrierPositionInfoMap.get(identifier)
          .add(BarrierPositionInfo.BarrierPosition.builder()
                   .stageSetupId(stageSetupId)
                   .stepGroupSetupId(obtainStepGroupSetupIdOrNull(element))
                   .stepSetupId(element.getUuid())
                   .stepGroupRollback(isInsideStepGroupRollback(element))
                   .build());
    }
  }

  private String obtainStepGroupSetupIdOrNull(YamlNode currentElement) {
    YamlNode stepGroup = findStepGroupBottomUp(currentElement);
    return stepGroup != null ? stepGroup.getUuid() : null;
  }

  private String obtainBarrierIdentifierFromStep(YamlNode currentElement) {
    return Preconditions.checkNotNull(currentElement.getField(SPEC_FIELD).getNode().getStringValue(BARRIER_REF_FIELD),
        String.format(BARRIER_REF_FIELD + " cannot be null -> %s", currentElement.asText()));
  }

  private boolean isInsideStepGroupRollback(YamlNode currentElement) {
    YamlNode rollbackSteps = YamlUtils.findParentNode(currentElement, YAMLFieldNameConstants.ROLLBACK_STEPS);
    if (rollbackSteps == null) {
      return false;
    }

    YamlNode stepGroup = findStepGroupBottomUp(rollbackSteps);
    return stepGroup != null;
  }

  private YamlNode findStepGroupBottomUp(YamlNode currentElement) {
    return YamlUtils.findParentNode(currentElement, YAMLFieldNameConstants.STEP_GROUP);
  }

  public Map<String, BarrierSetupInfo> getBarrierIdentifierMap() {
    return barrierIdentifierMap;
  }
}
