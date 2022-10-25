/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.azure;

import static io.harness.azure.model.AzureConstants.AZURE_VMSS_SWAP_BACKEND_POOL;
import static io.harness.azure.model.AzureConstants.DEPLOYMENT_STATUS;
import static io.harness.azure.model.AzureConstants.DOWN_SCALE_COMMAND_UNIT;
import static io.harness.azure.model.AzureConstants.DOWN_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT;
import static io.harness.azure.model.AzureConstants.SKIP_VMSS_ROLLBACK;
import static io.harness.azure.model.AzureConstants.UP_SCALE_COMMAND_UNIT;
import static io.harness.azure.model.AzureConstants.UP_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT;

import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.exception.utils.ExceptionUtils;

import software.wings.beans.command.AzureVMSSDummyCommandUnit;
import software.wings.beans.command.CommandUnit;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.StateType;

import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableList;
import java.util.List;

public class AzureVMSSSwitchRoutesRollbackState extends AzureVMSSSwitchRoutesState {
  public AzureVMSSSwitchRoutesRollbackState(String name) {
    super(name, StateType.AZURE_VMSS_SWITCH_ROUTES_ROLLBACK.name());
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
  @SchemaIgnore
  public boolean isDownsizeOldVMSS() {
    return super.isDownsizeOldVMSS();
  }

  @Override
  protected String getSkipMessage() {
    return SKIP_VMSS_ROLLBACK;
  }

  @Override
  public boolean isRollback() {
    return true;
  }

  @Override
  protected List<CommandUnit> getCommandUnits() {
    return ImmutableList.of(new AzureVMSSDummyCommandUnit(UP_SCALE_COMMAND_UNIT),
        new AzureVMSSDummyCommandUnit(UP_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT),
        new AzureVMSSDummyCommandUnit(AZURE_VMSS_SWAP_BACKEND_POOL),
        new AzureVMSSDummyCommandUnit(DOWN_SCALE_COMMAND_UNIT),
        new AzureVMSSDummyCommandUnit(DOWN_SCALE_STEADY_STATE_WAIT_COMMAND_UNIT),
        new AzureVMSSDummyCommandUnit(DEPLOYMENT_STATUS));
  }

  @Override
  @SchemaIgnore
  public Integer getTimeoutMillis() {
    return super.getTimeoutMillis();
  }
}
