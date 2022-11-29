/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc.trigger;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.validation.Create;

import software.wings.beans.trigger.TriggerExecution;
import software.wings.beans.trigger.TriggerExecution.Status;
import software.wings.service.intfc.ownership.OwnedByAccount;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import org.hibernate.validator.constraints.NotEmpty;
import ru.vyarus.guice.validator.group.annotation.ValidationGroups;

@OwnedBy(CDC)
@TargetModule(HarnessModule._815_CG_TRIGGERS)
public interface TriggerExecutionService extends OwnedByAccount {
  @ValidationGroups(Create.class) TriggerExecution save(@Valid TriggerExecution triggerExecution);

  TriggerExecution get(@NotEmpty String appId, @NotEmpty String triggerId);

  PageResponse<TriggerExecution> list(PageRequest<TriggerExecution> pageRequest);

  TriggerExecution fetchLastSuccessOrRunningExecution(
      @NotEmpty String appId, @NotEmpty String triggerId, @NotEmpty String webhookToken);

  void updateStatus(String appId, String triggerExecutionId, @NotNull Status status, String errorMsg);
}
