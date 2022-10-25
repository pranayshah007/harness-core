/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.spotinst;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.spotinst.model.SpotInstConstants.DEPLOYMENT_ERROR;
import static io.harness.spotinst.model.SpotInstConstants.DOWN_SCALE_COMMAND_UNIT;
import static io.harness.spotinst.model.SpotInstConstants.DOWN_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT;
import static io.harness.spotinst.model.SpotInstConstants.RENAME_NEW_COMMAND_UNIT;
import static io.harness.spotinst.model.SpotInstConstants.RENAME_OLD_COMMAND_UNIT;
import static io.harness.spotinst.model.SpotInstConstants.SWAP_ROUTES_COMMAND_UNIT;
import static io.harness.spotinst.model.SpotInstConstants.UP_SCALE_COMMAND_UNIT;
import static io.harness.spotinst.model.SpotInstConstants.UP_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT;

import static software.wings.service.impl.aws.model.AwsConstants.MIN_TRAFFIC_SHIFT_WEIGHT;

import static java.util.Collections.emptyMap;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.exception.utils.ExceptionUtils;

import software.wings.beans.command.CommandUnit;
import software.wings.beans.command.SpotinstDummyCommandUnit;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.StateType;

import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Map;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@ToString
@Slf4j
@OwnedBy(CDP)
public class SpotinstTrafficShiftAlbRollbackSwitchRoutesState extends SpotinstTrafficShiftAlbSwitchRoutesState {
  public SpotinstTrafficShiftAlbRollbackSwitchRoutesState(String name) {
    super(name, StateType.SPOTINST_LISTENER_ALB_SHIFT_ROLLBACK.name());
  }

  @Override
  public ExecutionResponse execute(ExecutionContext context) {
    try {
      return executeInternal(context, true);
    } catch (WingsException e) {
      throw e;
    } catch (Exception e) {
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
  }

  @Override
  protected List<CommandUnit> getCommandUnits() {
    return ImmutableList.of(new SpotinstDummyCommandUnit(UP_SCALE_COMMAND_UNIT),
        new SpotinstDummyCommandUnit(UP_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT),
        new SpotinstDummyCommandUnit(RENAME_OLD_COMMAND_UNIT), new SpotinstDummyCommandUnit(SWAP_ROUTES_COMMAND_UNIT),
        new SpotinstDummyCommandUnit(DOWN_SCALE_COMMAND_UNIT),
        new SpotinstDummyCommandUnit(DOWN_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT),
        new SpotinstDummyCommandUnit(RENAME_NEW_COMMAND_UNIT), new SpotinstDummyCommandUnit(DEPLOYMENT_ERROR));
  }

  @Override
  protected int getNewElastigroupWeight(ExecutionContext context) {
    return MIN_TRAFFIC_SHIFT_WEIGHT;
  }

  @Override
  @SchemaIgnore
  public boolean isDownsizeOldElastigroup() {
    return super.isDownsizeOldElastigroup();
  }

  @Override
  @SchemaIgnore
  public String getNewElastigroupWeightExpr() {
    return super.getNewElastigroupWeightExpr();
  }

  @Override
  public Map<String, String> validateFields() {
    return emptyMap();
  }
}
