/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.aws.asg.manifest;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.aws.asg.manifest.AsgManifestType.AsgScheduledUpdateGroupAction;

import static com.google.common.collect.Lists.newArrayList;
import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.asg.AsgContentParser;
import io.harness.aws.asg.AsgSdkManager;
import io.harness.manifest.request.ManifestRequest;

import com.amazonaws.services.autoscaling.model.AutoScalingGroup;
import com.amazonaws.services.autoscaling.model.PutScheduledUpdateGroupActionRequest;
import com.amazonaws.services.autoscaling.model.ScheduledUpdateGroupAction;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@OwnedBy(CDP)
public class AsgScheduledActionManifestHandler extends AsgManifestHandler<PutScheduledUpdateGroupActionRequest> {
  public AsgScheduledActionManifestHandler(AsgSdkManager asgSdkManager, ManifestRequest manifestRequest) {
    super(asgSdkManager, manifestRequest);
  }

  @Override
  public Class<PutScheduledUpdateGroupActionRequest> getManifestContentUnmarshallClass() {
    return PutScheduledUpdateGroupActionRequest.class;
  }

  @Override
  public AsgManifestHandlerChainState upsert(AsgManifestHandlerChainState chainState, ManifestRequest manifestRequest) {
    List<PutScheduledUpdateGroupActionRequest> manifests = new ArrayList<>();
    if (manifestRequest.getManifests() != null) {
      manifests =
          manifestRequest.getManifests().stream().map(this::parseContentToManifest).collect(Collectors.toList());
    }
    String asgName = chainState.getAsgName();
    String operationName = format("Modifying scheduled actions of autoscaling group %s", asgName);
    asgSdkManager.info("`%s` has started", operationName);
    asgSdkManager.clearAllScheduledActionsForAsg(asgName);
    asgSdkManager.attachScheduledActionsToAsg(asgName, manifests);
    asgSdkManager.infoBold("`%s` ended successfully", operationName);
    return chainState;
  }

  @Override
  public AsgManifestHandlerChainState delete(AsgManifestHandlerChainState chainState, ManifestRequest manifestRequest) {
    return chainState;
  }

  @Override
  public AsgManifestHandlerChainState getManifestTypeContent(
      AsgManifestHandlerChainState chainState, ManifestRequest manifestRequest) {
    if (chainState.getAutoScalingGroup() == null) {
      AutoScalingGroup autoScalingGroup = asgSdkManager.getASG(chainState.getAsgName());
      chainState.setAutoScalingGroup(autoScalingGroup);
    }

    AutoScalingGroup autoScalingGroup = chainState.getAutoScalingGroup();
    if (autoScalingGroup != null) {
      List<ScheduledUpdateGroupAction> actions = asgSdkManager.listAllScheduledActionsOfAsg(chainState.getAsgName());
      List<String> actionContentList = createScheduledActionRequestsListFromScheduledActionList(actions);

      if (chainState.getAsgManifestsDataForRollback() == null) {
        chainState.setAsgManifestsDataForRollback(new HashMap<>());
      }

      Map<String, List<String>> asgManifestsDataForRollback = chainState.getAsgManifestsDataForRollback();
      asgManifestsDataForRollback.put(AsgScheduledUpdateGroupAction, actionContentList);
    }

    return chainState;
  }

  private String createScheduledActionRequestFromScheduledActionMapper(ScheduledUpdateGroupAction action)
      throws JsonProcessingException {
    String content = AsgContentParser.toString(action, true);
    PutScheduledUpdateGroupActionRequest request =
        AsgContentParser.parseJson(content, PutScheduledUpdateGroupActionRequest.class, false);
    return AsgContentParser.toString(request, false);
  }

  private List<String> createScheduledActionRequestsListFromScheduledActionList(
      List<ScheduledUpdateGroupAction> actions) {
    List<String> actionList = newArrayList();
    actions.forEach(action -> {
      try {
        actionList.add(createScheduledActionRequestFromScheduledActionMapper(action));
      } catch (JsonProcessingException e) {
        throw new RuntimeException(e);
      }
    });
    return actionList;
  }
}
