/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.spotinst.model.SpotInstConstants.DOWN_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT;
import static io.harness.spotinst.model.SpotInstConstants.UP_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT;

import static software.wings.service.impl.aws.model.AwsConstants.MIN_TRAFFIC_SHIFT_WEIGHT;
import static software.wings.sm.states.AwsAmiSwitchRoutesState.SWAP_AUTO_SCALING_ROUTES;

import static java.util.Collections.emptyMap;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.exception.utils.ExceptionUtils;

import software.wings.beans.command.CommandUnit;
import software.wings.beans.command.SpotinstDummyCommandUnit;
import software.wings.service.impl.aws.model.AwsConstants;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.StateType;

import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Map;

@OwnedBy(CDP)
public class AwsAmiRollbackTrafficShiftAlbSwitchRoutesState extends AwsAmiTrafficShiftAlbSwitchRoutesState {
  public AwsAmiRollbackTrafficShiftAlbSwitchRoutesState(String name) {
    super(name, StateType.ASG_AMI_ROLLBACK_ALB_SHIFT_SWITCH_ROUTES.name());
  }

  @Override
  public ExecutionResponse execute(ExecutionContext context) {
    try {
      return executeInternal(context);
    } catch (WingsException e) {
      throw e;
    } catch (Exception e) {
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
  }

  @Override
  protected int getNewAutoscalingGroupWeight(ExecutionContext context) {
    return MIN_TRAFFIC_SHIFT_WEIGHT;
  }

  @Override
  public Map<String, String> validateFields() {
    return emptyMap();
  }

  @SchemaIgnore
  @Override
  public boolean isRollback() {
    return true;
  }

  @Override
  @SchemaIgnore
  public boolean isDownsizeOldAsg() {
    return super.isDownsizeOldAsg();
  }

  @Override
  @SchemaIgnore
  public String getNewAutoScalingGroupWeightExpr() {
    return super.getNewAutoScalingGroupWeightExpr();
  }

  @Override
  protected List<CommandUnit> getCommandUnits() {
    return ImmutableList.of(new SpotinstDummyCommandUnit(AwsConstants.UP_SCALE_ASG_COMMAND_UNIT),
        new SpotinstDummyCommandUnit(UP_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT),
        new SpotinstDummyCommandUnit(SWAP_AUTO_SCALING_ROUTES),
        new SpotinstDummyCommandUnit(AwsConstants.DOWN_SCALE_ASG_COMMAND_UNIT),
        new SpotinstDummyCommandUnit(DOWN_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT));
  }
}
