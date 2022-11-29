/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;

import software.wings.beans.command.Command;
import software.wings.beans.command.ServiceCommand;
import software.wings.service.intfc.ownership.OwnedByAccount;

/**
 * Created by peeyushaggarwal on 11/16/16.
 */
@OwnedBy(CDC)
public interface CommandService extends OwnedByAccount {
  Command getCommand(String appId, String originEntityId, int version);
  ServiceCommand getServiceCommand(String appId, String serviceCommandId);
  ServiceCommand getServiceCommandByName(String appId, String serviceId, String serviceCommandName);
  Command save(Command command, boolean pushToYaml);
}
