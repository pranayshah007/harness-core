/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.grpc.pingpong;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.event.PingPongServiceGrpc;
import io.harness.event.PingPongServiceGrpc.PingPongServiceBlockingStub;
import io.harness.govern.ProviderModule;
import io.harness.version.VersionInfoManager;

import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.grpc.CallCredentials;
import io.grpc.Channel;

@TargetModule(HarnessModule._420_DELEGATE_AGENT)
public class PingPongModule extends ProviderModule {
  @Provides
  @Singleton
  PingPongServiceBlockingStub pingPongServiceBlockingStub(
      @Named("manager-channel") Channel channel, CallCredentials callCredentials) {
    return PingPongServiceGrpc.newBlockingStub(channel).withCallCredentials(callCredentials);
  }

  @Provides
  @Singleton
  PingPongClient pingPongClient(
      PingPongServiceBlockingStub pingPongServiceBlockingStub, VersionInfoManager versionInfoManager) {
    return new PingPongClient(pingPongServiceBlockingStub, versionInfoManager.getVersionInfo().getVersion());
  }
}
