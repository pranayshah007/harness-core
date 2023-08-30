/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import static io.harness.annotations.dev.HarnessTeam.SSCA;
import static io.harness.authorization.AuthorizationServiceHeader.SSCA_SERVICE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.AbstractMongoModule;
import io.harness.mongo.MongoConfig;
import io.harness.mongo.MongoPersistence;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.persistence.HPersistence;
import io.harness.persistence.NoopUserProvider;
import io.harness.persistence.UserProvider;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.serializer.KryoRegistrar;
import io.harness.serializer.SSCAManagerModuleRegistrars;
import io.harness.spec.server.ssca.v1.EnforcementApi;
import io.harness.spec.server.ssca.v1.OrchestrationApi;
import io.harness.spec.server.ssca.v1.SbomProcessorApi;
import io.harness.spec.server.ssca.v1.TokenApi;
import io.harness.ssca.S3Config;
import io.harness.ssca.api.EnforcementApiImpl;
import io.harness.ssca.api.OrchestrationApiImpl;
import io.harness.ssca.api.SbomProcessorApiImpl;
import io.harness.ssca.api.TokenApiImpl;
import io.harness.ssca.services.ArtifactService;
import io.harness.ssca.services.ArtifactServiceImpl;
import io.harness.ssca.services.EnforcementResultService;
import io.harness.ssca.services.EnforcementResultServiceImpl;
import io.harness.ssca.services.EnforcementStepService;
import io.harness.ssca.services.EnforcementStepServiceImpl;
import io.harness.ssca.services.EnforcementSummaryService;
import io.harness.ssca.services.EnforcementSummaryServiceImpl;
import io.harness.ssca.services.NextGenService;
import io.harness.ssca.services.NextGenServiceImpl;
import io.harness.ssca.services.OrchestrationStepService;
import io.harness.ssca.services.OrchestrationStepServiceImpl;
import io.harness.ssca.services.RuleEngineService;
import io.harness.ssca.services.RuleEngineServiceImpl;
import io.harness.ssca.services.S3StoreService;
import io.harness.ssca.services.S3StoreServiceImpl;
import io.harness.token.TokenClientModule;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import dev.morphia.converters.TypeConverter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;

@Slf4j
@OwnedBy(SSCA)
public class SSCAManagerModule extends AbstractModule {
  private final io.harness.SSCAManagerConfiguration configuration;

  private static SSCAManagerModule sscaManagerModule;
  private SSCAManagerModule(io.harness.SSCAManagerConfiguration configuration) {
    this.configuration = configuration;
  }

  @Override
  protected void configure() {
    install(new AbstractMongoModule() {
      @Override
      public UserProvider userProvider() {
        return new NoopUserProvider();
      }
    });
    install(new io.harness.SSCAManagerModulePersistence());
    bind(HPersistence.class).to(MongoPersistence.class);
    bind(SbomProcessorApi.class).to(SbomProcessorApiImpl.class);
    bind(TokenApi.class).to(TokenApiImpl.class);
    bind(EnforcementApi.class).to(EnforcementApiImpl.class);
    bind(OrchestrationApi.class).to(OrchestrationApiImpl.class);
    bind(ArtifactService.class).to(ArtifactServiceImpl.class);
    bind(OrchestrationStepService.class).to(OrchestrationStepServiceImpl.class);
    bind(EnforcementStepService.class).to(EnforcementStepServiceImpl.class);
    bind(RuleEngineService.class).to(RuleEngineServiceImpl.class);
    bind(EnforcementResultService.class).to(EnforcementResultServiceImpl.class);
    bind(EnforcementSummaryService.class).to(EnforcementSummaryServiceImpl.class);
    bind(NextGenService.class).to(NextGenServiceImpl.class);
    bind(S3StoreService.class).to(S3StoreServiceImpl.class);
    install(new TokenClientModule(this.configuration.getNgManagerServiceHttpClientConfig(),
        this.configuration.getNgManagerServiceSecret(), SSCA_SERVICE.getServiceId()));
  }

  @Provides
  @Singleton
  @Named("ngManagerServiceHttpClientConfig")
  public ServiceHttpClientConfig ngManagerServiceHttpClientConfig() {
    return this.configuration.getNgManagerServiceHttpClientConfig();
  }
  @Provides
  @Singleton
  @Named("ngManagerServiceSecret")
  public String ngManagerServiceSecret() {
    return this.configuration.getNgManagerServiceSecret();
  }

  @Provides
  @Singleton
  @Named("sscaManagerServiceSecret")
  public String sscaManagerServiceSecret() {
    return this.configuration.getSscaManagerServiceSecret();
  }

  @Provides
  @Singleton
  @Named("jwtAuthSecret")
  public String jwtAuthSecret() {
    return this.configuration.getJwtAuthSecret();
  }

  @Provides
  @Singleton
  public S3Config s3Config() {
    return this.configuration.getS3Config();
  }

  @Provides
  @Singleton
  public AmazonS3 s3Client() {
    BasicAWSCredentials googleCreds = new BasicAWSCredentials(
        configuration.getS3Config().getAccessKeyId(), configuration.getS3Config().getAccessSecretKey());

    return AmazonS3ClientBuilder.standard()
        .withEndpointConfiguration(
            new AwsClientBuilder.EndpointConfiguration(configuration.getS3Config().getEndpoint(), "auto"))
        .withCredentials(new AWSStaticCredentialsProvider(googleCreds))
        .build();
  }

  @Provides
  @Singleton
  public Set<Class<? extends KryoRegistrar>> kryoRegistrars() {
    return ImmutableSet.<Class<? extends KryoRegistrar>>builder()
        .addAll(SSCAManagerModuleRegistrars.kryoRegistrars)
        .build();
  }

  @Provides
  @Singleton
  public Set<Class<? extends MorphiaRegistrar>> morphiaRegistrars() {
    return ImmutableSet.<Class<? extends MorphiaRegistrar>>builder()
        .addAll(SSCAManagerModuleRegistrars.morphiaRegistrars)
        .build();
  }

  @Provides
  @Singleton
  public Set<Class<? extends TypeConverter>> morphiaConverters() {
    return ImmutableSet.<Class<? extends TypeConverter>>builder()
        .addAll(SSCAManagerModuleRegistrars.morphiaConverters)
        .build();
  }

  @Provides
  @Singleton
  List<Class<? extends Converter<?, ?>>> springConverters() {
    return ImmutableList.<Class<? extends Converter<?, ?>>>builder()
        .addAll(SSCAManagerModuleRegistrars.springConverters)
        .build();
  }

  @Provides
  @Singleton
  public MongoConfig mongoConfig() {
    return configuration.getMongoConfig();
  }

  @Provides
  @Singleton
  @Named("morphiaClasses")
  Map<Class, String> morphiaCustomCollectionNames() {
    return Collections.emptyMap();
  }

  public static SSCAManagerModule getInstance(io.harness.SSCAManagerConfiguration sscaManagerConfiguration) {
    if (sscaManagerModule == null) {
      return new SSCAManagerModule(sscaManagerConfiguration);
    }
    return sscaManagerModule;
  }
}
