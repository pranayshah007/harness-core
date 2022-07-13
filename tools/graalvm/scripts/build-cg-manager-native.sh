#!/bin/sh
# Copyright 2022 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

#Build your java service and then pass in the path to that jar
if [[ $# = 0 ]] ; then printf "Error - You did not pass in the path the jar you want nativized!\n"; exit 1; else JAR_PATH="${1}"; fi

native-image --no-fallback -H:TraceClassInitialization=true -H:ConfigurationFileDirectories=target/config \
--initialize-at-build-time=io.netty \
--initialize-at-run-time=io.netty.channel.kqueue.KQueueEventArray \
--initialize-at-run-time=io.netty.channel.kqueue.Native \
--initialize-at-build-time=ch.qos.logback \
--initialize-at-build-time=ch.qos.logback.classic.Logger \
--initialize-at-build-time=org.apache.sshd.client.subsystem.sftp.SftpFileSystemProvider \
--initialize-at-run-time=io.netty.util.NetUtil \
--initialize-at-run-time=io.netty.channel.epoll.Epoll \
--initialize-at-run-time=io.netty.channel.epoll.Native \
--initialize-at-run-time=io.netty.channel.epoll.EpollEventArray \
--initialize-at-run-time=io.netty.handler.ssl.JdkNpnApplicationProtocolNegotiator \
--initialize-at-run-time=io.netty.handler.ssl.ReferenceCountedOpenSslEngine \
--initialize-at-run-time=io.netty.channel.unix.Limits \
--initialize-at-run-time=io.netty.handler.ssl.JettyNpnSslEngine \
--trace-class-initialization=io.netty.channel.socket.InternetProtocolFamily \
--initialize-at-run-time=io.netty.channel.socket.InternetProtocolFamily \
--initialize-at-run-time=io.netty.resolver.dns.DnsServerAddressStreamProviders \
--initialize-at-run-time=io.netty.resolver.dns.DnsNameResolver \
--initialize-at-run-time=io.netty.resolver.dns.DefaultDnsServerAddressStreamProvider \
--initialize-at-run-time=io.netty.channel.DefaultChannelId \
--initialize-at-run-time=io.netty.resolver.dns.DnsServerAddressStreamProviders\$DefaultProviderHolder \
--initialize-at-run-time=io.netty.util.AbstractReferenceCounted \
--trace-class-initialization=javax.xml.parsers.FactoryFinder \
--trace-class-initialization=org.apache.sshd.common.util.GenericUtils \
--trace-class-initialization=org.conscrypt.OpenSSLProvider \
--trace-class-initialization=org.conscrypt.HostProperties\$Architecture\$1 \
--trace-class-initialization=org.conscrypt.Platform \
--trace-class-initialization=org.conscrypt.NativeCrypto \
--trace-class-initialization=org.conscrypt.HostProperties \
--trace-class-initialization=org.slf4j.impl.StaticLoggerBinder \
--trace-class-initialization=org.conscrypt.Conscrypt \
--trace-class-initialization=io.netty.util.AbstractReferenceCounted \
-H:+ReportExceptionStackTraces \
-jar $JAR_PATH
