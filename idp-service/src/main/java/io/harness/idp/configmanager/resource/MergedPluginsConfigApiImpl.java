/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.configmanager.resource;

import static io.harness.idp.common.CommonUtils.readFileFromClassPath;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ResponseMessage;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.configmanager.service.ConfigManagerService;
import io.harness.idp.configmanager.utils.ConfigManagerUtils;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.security.annotations.NextGenManagerAuth;
import io.harness.spec.server.idp.v1.MergedPluginsConfigApi;
import io.harness.spec.server.idp.v1.model.MergedPluginConfigResponse;
import io.harness.spec.server.idp.v1.model.MergedPluginConfigs;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import javax.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@NextGenManagerAuth
@Slf4j
public class MergedPluginsConfigApiImpl implements MergedPluginsConfigApi {
  private final ConfigManagerService configManagerService;
  private final IdpCommonService idpCommonService;
  private final NamespaceService namespaceService;
  private final String backstageAppBaseUrl;
  private final String backstageBackendBaseUrl;
  private final String backstagePostgresHost;

  @Inject
  public MergedPluginsConfigApiImpl(@Named("backstageAppBaseUrl") String backstageAppBaseUrl,
      @Named("backstagePostgresHost") String backstagePostgresHost,
      @Named("backstageHttpClientConfig") ServiceHttpClientConfig backstageHttpClientConfig,
      NamespaceService namespaceService, IdpCommonService idpCommonService, ConfigManagerService configManagerService) {
    this.backstageAppBaseUrl = backstageAppBaseUrl;
    this.backstageBackendBaseUrl = backstageHttpClientConfig.getBaseUrl();
    this.backstagePostgresHost = backstagePostgresHost;
    this.namespaceService = namespaceService;
    this.idpCommonService = idpCommonService;
    this.configManagerService = configManagerService;
  }
  private static final String APP_CONFIG_PATH = "app-config.yml";
  private static final String BASE_CONFIG_NAME = "backstage-base-config";
  private static final String OVERRIDE_CONFIG_NAME = "backstage-override-config";

  @Override
  public Response getMergedPluginsConfig(String accountIdentifier) {
    try {
      MergedPluginConfigs mergedEnabledPluginAppConfigsForAccount =
          configManagerService.mergeEnabledPluginConfigsForAccount(accountIdentifier);
      MergedPluginConfigResponse mergedPluginConfigResponse = new MergedPluginConfigResponse();
      mergedPluginConfigResponse.setMergedConfig(mergedEnabledPluginAppConfigsForAccount);
      return Response.status(Response.Status.OK).entity(mergedPluginConfigResponse).build();
    } catch (Exception e) {
      log.error("Error in merging configs for enabled plugins for account - {}", accountIdentifier, e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
  }

  @Override
  public Response syncAppConfig(String harnessAccount, Boolean baseConfig) {
    try {
      idpCommonService.checkUserAuthorization();
      if (baseConfig != null && baseConfig) {
        String nameSpace = namespaceService.getNamespaceForAccountIdentifier(harnessAccount).getNamespace();
        String appConfig = readFileFromClassPath(APP_CONFIG_PATH);
        JsonNode config = ConfigManagerUtils.asJsonNode(appConfig);
        String baseAppConfig = ConfigManagerUtils.asYaml(config.toString());
        String finalBaseAppConfig = baseAppConfig.replace("${ACCOUNT_ID}", harnessAccount)
                                        .replace("${NAMESPACE}", nameSpace)
                                        .replace("${APP_BASE_URL}", backstageAppBaseUrl)
                                        .replace("${BACKEND_BASE_URL}", backstageBackendBaseUrl)
                                        .replace("${POSTGRES_HOST}", backstagePostgresHost);
        configManagerService.updateConfigMap(harnessAccount, finalBaseAppConfig, BASE_CONFIG_NAME);
        log.info("Sync for base app-config completed for account Id - {}", harnessAccount);
      }
      String mergedAppConfig = configManagerService.mergeAllAppConfigsForAccount(harnessAccount);
      configManagerService.updateConfigMap(harnessAccount, mergedAppConfig, OVERRIDE_CONFIG_NAME);
      log.info("Sync for merged app-config completed for account Id - {}", harnessAccount);
    } catch (Exception e) {
      log.error("Could not sync app-config for account Id - {}", harnessAccount, e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
          .entity(ResponseMessage.builder().message(e.getMessage()).build())
          .build();
    }
    return Response.status(Response.Status.NO_CONTENT).build();
  }
}
