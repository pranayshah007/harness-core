/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.scm;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.grpc.client.SCMGrpcInterceptor;

import io.grpc.ManagedChannel;
import io.grpc.netty.NettyChannelBuilder;
import io.netty.channel.ChannelOption;
import io.netty.channel.kqueue.KQueueDomainSocketChannel;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.unix.DomainSocketAddress;
import java.io.IOException;

@OwnedBy(HarnessTeam.DX)
public class ScmMacOSManager extends ScmUnixManager {
  KQueueEventLoopGroup klg;

  public ScmMacOSManager() throws IOException {}

  public ManagedChannel getChannel() {
    klg = new KQueueEventLoopGroup();
    return NettyChannelBuilder.forAddress(new DomainSocketAddress(socketAddress))
        .eventLoopGroup(klg)
        .channelType(KQueueDomainSocketChannel.class)
        .intercept(SCMGrpcInterceptor.INTERCEPTOR)
        .usePlaintext()
        .withOption(ChannelOption.SO_KEEPALIVE, false)
        .build();
  }

  void shutdownEventLoopGroup() {
    klg.shutdownGracefully();
  }
}
