/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.taskapps.shell.kryo;

import io.harness.delegate.beans.DelegateMetaInfo;
import io.harness.delegate.beans.DelegateTaskNotifyResponseData;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.task.shell.ShellScriptTaskResponseNG;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.UnitProgress;
import io.harness.logging.serializer.kryo.UnitProgressKryoSerializer;
import io.harness.serializer.KryoRegistrar;
import io.harness.shell.CommandExecutionData;
import io.harness.shell.ExecuteCommandResponse;
import io.harness.shell.ScriptType;

import software.wings.beans.bash.ShellScriptTaskParametersNG;

import com.esotericsoftware.kryo.Kryo;

public class ShellScriptNgTaskKryoRegistrars implements KryoRegistrar {
  @Override
  public void register(final Kryo kryo) {
    // Task Request
    // This hijacks io.harness.delegate.task.shell.ShellScriptTaskParametersNG
    kryo.register(ShellScriptTaskParametersNG.class, 19463);
    kryo.register(ScriptType.class, 5253);

    // Task Response
    kryo.register(ShellScriptTaskResponseNG.class, 19464);
    kryo.register(ExecuteCommandResponse.class, 1438);
    kryo.register(CommandExecutionStatus.class, 5037);
    kryo.register(CommandExecutionData.class, 5035);
    kryo.register(UnitProgressData.class, 95001);
    kryo.register(UnitProgress.class, UnitProgressKryoSerializer.getInstance(), 9701);

    kryo.register(DelegateTaskNotifyResponseData.class, 5373);
    kryo.register(DelegateMetaInfo.class, 5372);
  }
}
