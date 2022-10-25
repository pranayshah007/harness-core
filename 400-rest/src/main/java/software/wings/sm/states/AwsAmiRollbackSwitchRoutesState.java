/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.exception.utils.ExceptionUtils;

import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.StateType;

import com.github.reinert.jjschema.SchemaIgnore;

@OwnedBy(CDP)
public class AwsAmiRollbackSwitchRoutesState extends AwsAmiSwitchRoutesState {
  public AwsAmiRollbackSwitchRoutesState(String name) {
    super(name, StateType.AWS_AMI_ROLLBACK_SWITCH_ROUTES.name());
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
  @SchemaIgnore
  public boolean isDownsizeOldAsg() {
    return super.isDownsizeOldAsg();
  }

  @Override
  @SchemaIgnore
  public void setDownsizeOldAsg(boolean downsizeOldAsg) {
    super.setDownsizeOldAsg(downsizeOldAsg);
  }

  @Override
  @SchemaIgnore
  public Integer getTimeoutMillis() {
    return super.getTimeoutMillis();
  }
}
