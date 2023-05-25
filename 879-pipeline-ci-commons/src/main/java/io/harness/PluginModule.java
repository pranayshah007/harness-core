/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.plugin.compatible.PluginCompatibleStep;
import io.harness.cdng.plugininfoproviders.PluginExecutionConfig;
import io.harness.ci.serializer.ProtobufStepSerializer;
import io.harness.plugin.CommonPluginCompatibleSerializer;
import io.harness.plugin.service.BasePluginCompatibleSerializer;
import io.harness.plugin.service.K8InitializeServiceImpl;
import io.harness.plugin.service.K8sInitializeService;
import io.harness.plugin.service.PluginService;
import io.harness.plugin.service.PluginServiceImpl;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;

@OwnedBy(HarnessTeam.PIPELINE)
public class PluginModule extends AbstractModule {
  private static PluginModule instance;
  private PluginConfiguration config;

  public static PluginModule getInstance(PluginConfiguration config) {
    if (instance == null) {
      instance = new PluginModule(config);
    }
    return instance;
  }

  private PluginModule(PluginConfiguration config) {
    this.config = config;
  }

  @Override
  protected void configure() {
    if (config.shouldSkipBinding) {
      return;
    }
    bind(new TypeLiteral<ProtobufStepSerializer<PluginCompatibleStep>>() {
    }).toInstance(new CommonPluginCompatibleSerializer());
    bind(K8sInitializeService.class).to(K8InitializeServiceImpl.class);
    bind(PluginService.class).to(PluginServiceImpl.class);
    bind(BasePluginCompatibleSerializer.class).to(CommonPluginCompatibleSerializer.class);
  }

  @Provides
  @Singleton
  @Named("ngBaseUrl")
  String getNgBaseUrl() {
    String apiUrl = config.getPluginExecutionConfig().getApiUrl();
    if (apiUrl.endsWith("/")) {
      return apiUrl.substring(0, apiUrl.length() - 1);
    }
    return apiUrl;
  }

  @Provides
  @Singleton
  PluginExecutionConfig pluginExecutionConfig() {
    return config.getPluginExecutionConfig();
  }
}
