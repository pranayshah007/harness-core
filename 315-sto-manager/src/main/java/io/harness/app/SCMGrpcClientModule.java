/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.app;

import static io.harness.product.ci.scm.proto.SCMGrpc.newBlockingStub;

import io.harness.govern.ProviderModule;
import io.harness.product.ci.scm.proto.SCMGrpc;

import com.google.inject.Provides;
import com.google.inject.Singleton;
import io.grpc.Channel;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import javax.net.ssl.SSLException;

public class SCMGrpcClientModule extends ProviderModule {
  ScmConnectionConfig scmConnectionConfig;

  public SCMGrpcClientModule(ScmConnectionConfig scmConnectionConfig) {
    this.scmConnectionConfig = scmConnectionConfig;
  }

  @Provides
  @Singleton
  SCMGrpc.SCMBlockingStub scmServiceBlockingStub() throws SSLException {
    return newBlockingStub(scmChannel(scmConnectionConfig.getUrl()));
  }

  @Singleton
  @Provides
  public Channel scmChannel(String connectionUrl) {
    // TODO: Authentication Needs to be added here.
    return NettyChannelBuilder.forTarget(connectionUrl).usePlaintext().build();
  }
}
