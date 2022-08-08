/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.app;

import io.harness.expression.app.ExpressionServiceModule;
import io.harness.expression.service.ExpressionEvaulatorServiceGrpc;
import io.harness.expression.service.ExpressionServiceImpl;
import io.harness.grpc.auth.ServiceInfo;
import io.harness.grpc.server.Connector;
import io.harness.grpc.server.GrpcServerModule;
import io.harness.task.service.TaskServiceGrpc;
import io.harness.task.service.impl.TaskServiceImpl;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;
import io.grpc.BindableService;
import io.grpc.ServerInterceptor;
import java.util.List;
import java.util.Set;

public class DelegateGrpcServiceModule extends AbstractModule {
  private static final String SERVICE_ID = "delegate-grpc-service";
  private final int servicePort;
  private final String serviceSecret;

  @Inject
  public DelegateGrpcServiceModule(int servicePort, String serviceSecret) {
    this.servicePort = servicePort;
    this.serviceSecret = serviceSecret;
  }

  @Override
  protected void configure() {
    install(new ExpressionServiceModule());

    Multibinder<BindableService> bindableServiceMultibinder = Multibinder.newSetBinder(binder(), BindableService.class);
    bindableServiceMultibinder.addBinding().to(TaskServiceImpl.class);
    bindableServiceMultibinder.addBinding().to(ExpressionServiceImpl.class);

    Multibinder<String> nonAuthServices =
        Multibinder.newSetBinder(binder(), String.class, Names.named("excludedGrpcAuthValidationServices"));
    nonAuthServices.addBinding().toInstance(TaskServiceGrpc.SERVICE_NAME);

    MapBinder<String, ServiceInfo> stringServiceInfoMapBinder =
        MapBinder.newMapBinder(binder(), String.class, ServiceInfo.class);
    stringServiceInfoMapBinder.addBinding(ExpressionEvaulatorServiceGrpc.SERVICE_NAME)
        .toInstance(ServiceInfo.builder().id(SERVICE_ID).secret(serviceSecret).build());

    install(new GrpcServerModule(getConnectors(), getProvider(Key.get(new TypeLiteral<Set<BindableService>>() {})),
        getProvider(Key.get(new TypeLiteral<Set<ServerInterceptor>>() {}))));
  }

  @Provides
  public ServiceManager serviceManager(Set<Service> services) {
    return new ServiceManager(services);
  }

  private List<Connector> getConnectors() {
    final Connector connector = Connector.builder().port(servicePort).build();
    return ImmutableList.of(connector);
  }
}
