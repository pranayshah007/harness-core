/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import software.wings.beans.Event.Type;
import software.wings.beans.command.Command;
import software.wings.beans.command.Command.CommandKeys;
import software.wings.beans.command.ServiceCommand;
import software.wings.beans.command.ServiceCommand.ServiceCommandKeys;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.CommandService;
import software.wings.service.intfc.yaml.YamlPushService;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class CommandServiceImpl implements CommandService {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private AppService appService;
  @Inject private YamlPushService yamlPushService;

  @Override
  public Command getCommand(String appId, String originEntityId, int version) {
    return wingsPersistence.createQuery(Command.class)
        .filter("appId", appId)
        .filter(CommandKeys.originEntityId, originEntityId)
        .filter(CommandKeys.version, version)
        .get();
  }

  @Override
  public ServiceCommand getServiceCommand(String appId, String serviceCommandId) {
    return wingsPersistence.getWithAppId(ServiceCommand.class, appId, serviceCommandId);
  }

  @Override
  public ServiceCommand getServiceCommandByName(String appId, String serviceId, String serviceCommandName) {
    return wingsPersistence.createQuery(ServiceCommand.class)
        .filter("appId", appId)
        .filter(ServiceCommandKeys.serviceId, serviceId)
        .filter(ServiceCommandKeys.name, serviceCommandName)
        .get();
  }

  @Override
  public Command save(Command command, boolean pushToYaml) {
    if (command != null && command.getAccountId() == null) {
      command.setAccountId(appService.getAccountIdByAppId(command.getAppId()));
    }

    Command savedCommand = wingsPersistence.saveAndGet(Command.class, command);

    if (savedCommand != null && pushToYaml) {
      ServiceCommand serviceCommand = getServiceCommand(command.getAppId(), command.getOriginEntityId());
      yamlPushService.pushYamlChangeSet(
          serviceCommand.getAccountId(), null, serviceCommand, Type.CREATE, command.isSyncFromGit(), false);
    }
    return savedCommand;
  }

  @Override
  public void deleteByAccountId(String accountId) {
    wingsPersistence.delete(wingsPersistence.createQuery(Command.class).filter(CommandKeys.accountId, accountId));
  }
}
