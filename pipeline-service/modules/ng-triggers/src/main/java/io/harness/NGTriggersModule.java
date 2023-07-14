/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.authorization.AuthorizationServiceHeader.PIPELINE_SERVICE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ngtriggers.TriggerConfiguration;
import io.harness.ngtriggers.beans.source.webhook.WebhookSourceRepo;
import io.harness.ngtriggers.helpers.TriggerHelper;
import io.harness.ngtriggers.resource.NGTriggerEventHistoryResource;
import io.harness.ngtriggers.resource.NGTriggerEventHistoryResourceImpl;
import io.harness.ngtriggers.resource.NGTriggerResource;
import io.harness.ngtriggers.resource.NGTriggerResourceImpl;
import io.harness.ngtriggers.resource.NGTriggerWebhookConfigResource;
import io.harness.ngtriggers.resource.NGTriggerWebhookConfigResourceImpl;
import io.harness.ngtriggers.service.NGTriggerEventsService;
import io.harness.ngtriggers.service.NGTriggerService;
import io.harness.ngtriggers.service.NGTriggerWebhookRegistrationService;
import io.harness.ngtriggers.service.NGTriggerYamlSchemaService;
import io.harness.ngtriggers.service.impl.NGTriggerEventServiceImpl;
import io.harness.ngtriggers.service.impl.NGTriggerServiceImpl;
import io.harness.ngtriggers.service.impl.NGTriggerWebhookRegistrationServiceImpl;
import io.harness.ngtriggers.service.impl.NGTriggerYamlSchemaServiceImpl;
import io.harness.ngtriggers.service.impl.SecretDecryptorViaNg;
import io.harness.ngtriggers.utils.AwsCodeCommitDataObtainer;
import io.harness.ngtriggers.utils.GitProviderBaseDataObtainer;
import io.harness.ngtriggers.utils.SCMDataObtainer;
import io.harness.pipeline.remote.PipelineRemoteClientModule;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.secrets.SecretDecryptor;
import io.harness.webhook.WebhookConfigProvider;

import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapBinder;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@OwnedBy(PIPELINE)
public class NGTriggersModule extends AbstractModule {
  private static final AtomicReference<NGTriggersModule> instanceRef = new AtomicReference<>();
  private TriggerConfiguration triggerConfig;
  private ServiceHttpClientConfig pmsHttpClientConfig;
  private String pipelineServiceSecret;

  public static NGTriggersModule getInstance(
      TriggerConfiguration triggerConfig, ServiceHttpClientConfig pmsHttpClientConfig, String pipelineServiceSecret) {
    if (instanceRef.get() == null) {
      instanceRef.compareAndSet(null, new NGTriggersModule(triggerConfig, pmsHttpClientConfig, pipelineServiceSecret));
    }
    return instanceRef.get();
  }

  private NGTriggersModule(
      TriggerConfiguration triggerConfig, ServiceHttpClientConfig pmsHttpClientConfig, String pipelineServiceSecret) {
    this.triggerConfig = triggerConfig;
    this.pmsHttpClientConfig = pmsHttpClientConfig;
    this.pipelineServiceSecret = pipelineServiceSecret;
  }

  @Override
  protected void configure() {
    MapBinder<String, List<String>> variablesMapBinder =
        MapBinder.newMapBinder(binder(), new TypeLiteral<String>() {}, new TypeLiteral<List<String>>() {});
    // TODO(Harsh): add all trigger expressions in this list
    variablesMapBinder.addBinding("trigger").toInstance(TriggerHelper.getAllTriggerExpressions());

    install(SCMJavaClientModule.getInstance());
    bind(NGTriggerService.class).to(NGTriggerServiceImpl.class);
    bind(NGTriggerEventsService.class).to(NGTriggerEventServiceImpl.class);
    bind(NGTriggerWebhookRegistrationService.class).to(NGTriggerWebhookRegistrationServiceImpl.class);
    bind(NGTriggerYamlSchemaService.class).to(NGTriggerYamlSchemaServiceImpl.class);
    bind(NGTriggerResource.class).to(NGTriggerResourceImpl.class);
    bind(NGTriggerEventHistoryResource.class).to(NGTriggerEventHistoryResourceImpl.class);
    bind(NGTriggerWebhookConfigResource.class).to(NGTriggerWebhookConfigResourceImpl.class);
    bind(SecretDecryptor.class).to(SecretDecryptorViaNg.class);
    bind(WebhookConfigProvider.class).toInstance(new WebhookConfigProvider() {
      @Override
      public String getCustomApiBaseUrl() {
        return triggerConfig.getCustomBaseUrl();
      }

      @Override
      public String getWebhookApiBaseUrl() {
        return triggerConfig.getWebhookBaseUrl();
      }
    });
    install(new PipelineRemoteClientModule(
        ServiceHttpClientConfig.builder().baseUrl(pmsHttpClientConfig.getBaseUrl()).build(), pipelineServiceSecret,
        PIPELINE_SERVICE.toString()));
    MapBinder<String, GitProviderBaseDataObtainer> gitProviderBaseDataObtainerMap =
        MapBinder.newMapBinder(binder(), String.class, GitProviderBaseDataObtainer.class);
    gitProviderBaseDataObtainerMap.addBinding(WebhookSourceRepo.AWS_CODECOMMIT.name())
        .to(AwsCodeCommitDataObtainer.class);
    gitProviderBaseDataObtainerMap.addBinding(WebhookSourceRepo.AZURE_REPO.name()).to(SCMDataObtainer.class);
    gitProviderBaseDataObtainerMap.addBinding(WebhookSourceRepo.GITHUB.name()).to(SCMDataObtainer.class);
    gitProviderBaseDataObtainerMap.addBinding(WebhookSourceRepo.BITBUCKET.name()).to(SCMDataObtainer.class);
    gitProviderBaseDataObtainerMap.addBinding(WebhookSourceRepo.GITLAB.name()).to(SCMDataObtainer.class);
    // todo (abhinav): for advanced webhook details fix this.
    //    gitProviderBaseDataObtainerMap.addBinding(WebhookSourceRepo.HARNESS.name()).to(SCMDataObtainer.class);
  }
}
