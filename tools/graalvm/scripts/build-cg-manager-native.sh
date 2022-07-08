#!/bin/sh

#Build your java service and then change the following JAR variable to point to the one on your machine
JAR=$HOME/.cache/bazel/_bazel_loco/73eb0bf1eff209005bf26dc1460c3f43/execroot/harness_monorepo/bazel-out/k8-fastbuild/bin/360-cg-manager/module_deploy.jar

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
-jar $JAR

