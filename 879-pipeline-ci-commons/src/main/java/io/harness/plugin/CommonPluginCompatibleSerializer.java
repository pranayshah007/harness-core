/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plugin;

import io.harness.beans.plugin.compatible.PluginCompatibleStep;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.callback.DelegateCallbackToken;
import io.harness.cdng.plugininfoproviders.PluginExecutionConfig;
import io.harness.plugin.service.BasePluginCompatibleSerializer;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

@Singleton
public class CommonPluginCompatibleSerializer extends BasePluginCompatibleSerializer {
  @Inject PluginExecutionConfig pluginExecutionConfig;
  @Inject Supplier<DelegateCallbackToken> delegateCallbackTokenSupplier;

  @Override
  public String getImageName(PluginCompatibleStep pluginCompatibleStep, String accountId) {
    return pluginExecutionConfig.getGitCloneConfig().getImage();
  }

  @Override
  public List<String> getOutputVariables(PluginCompatibleStep pluginCompatibleStep) {
    return new ArrayList<>();
  }

  @Override
  public List<String> getEntryPoint(PluginCompatibleStep pluginCompatibleStep, String accountId, OSType os) {
    return pluginExecutionConfig.getGitCloneConfig().getEntrypoint();
  }

  @Override
  public String getDelegateCallbackToken() {
    return delegateCallbackTokenSupplier.get().getToken();
  }
}
