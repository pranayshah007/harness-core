/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.grpc.delegateservice;

import static io.harness.delegate.DelegateServiceGrpc.newBlockingStub;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.DelegateServiceAgentClient;
import io.harness.delegate.DelegateServiceGrpc.DelegateServiceBlockingStub;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.grpc.CallCredentials;
import io.grpc.Channel;

@TargetModule(HarnessModule._420_DELEGATE_AGENT)
public class DelegateServiceGrpcAgentClientModule extends AbstractModule {
  private static DelegateServiceGrpcAgentClientModule instance;

  public static DelegateServiceGrpcAgentClientModule getInstance() {
    if (instance == null) {
      instance = new DelegateServiceGrpcAgentClientModule();
    }
    return instance;
  }

  private DelegateServiceGrpcAgentClientModule() {}

  @Override
  protected void configure() {
    bind(DelegateServiceAgentClient.class).in(Singleton.class);
  }

  @Named("agent-client-stub")
  @Provides
  @Singleton
  DelegateServiceBlockingStub delegateServiceBlockingStub(
      @Named("manager-channel") Channel channel, CallCredentials callCredentials) {
    return newBlockingStub(channel).withCallCredentials(callCredentials);
  }
}
