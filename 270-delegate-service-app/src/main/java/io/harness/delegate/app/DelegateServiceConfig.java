/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.app;

import static io.harness.annotations.dev.HarnessTeam.DEL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.grpc.server.GrpcServerConfig;

import software.wings.app.MainConfiguration;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.inject.Singleton;
import io.dropwizard.bundles.assets.AssetsBundleConfiguration;
import io.dropwizard.jetty.ConnectorFactory;
import io.dropwizard.jetty.HttpConnectorFactory;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Used to load all the delegate mgr configuration.
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Singleton
@OwnedBy(DEL)
public class DelegateServiceConfig extends MainConfiguration implements AssetsBundleConfiguration {
}
