/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.provision.service;

import static io.harness.idp.provision.ProvisionConstants.ACCOUNT_ID;
import static io.harness.idp.provision.ProvisionConstants.NAMESPACE;
import static io.harness.idp.provision.ProvisionConstants.PROVISION_MODULE_CONFIG;
import static io.harness.remote.client.CGRestUtils.getResponse;

import io.harness.authorization.AuthorizationServiceHeader;
import io.harness.client.NgConnectorManagerClient;
import io.harness.exception.AccessDeniedException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.idp.common.Constants;
import io.harness.idp.envvariable.service.BackstageEnvVariableService;
import io.harness.idp.provision.ProvisionModuleConfig;
import io.harness.idp.settings.service.BackstagePermissionsService;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.ng.core.dto.secrets.SecretRequestWrapper;
import io.harness.ng.core.dto.secrets.SecretResponseWrapper;
import io.harness.ng.core.dto.secrets.SecretTextSpecDTO;
import io.harness.retry.RetryHelper;
import io.harness.secretmanagerclient.SecretType;
import io.harness.secretmanagerclient.ValueType;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.Principal;
import io.harness.security.dto.ServicePrincipal;
import io.harness.spec.server.idp.v1.model.BackstageEnvSecretVariable;
import io.harness.spec.server.idp.v1.model.BackstageEnvVariable;
import io.harness.spec.server.idp.v1.model.BackstagePermissions;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.github.resilience4j.retry.Retry;
import java.io.IOException;
import java.net.ConnectException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import net.sf.json.JSONObject;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.internal.http2.ConnectionShutdownException;
import okhttp3.internal.http2.StreamResetException;
import org.springframework.dao.DuplicateKeyException;

@Slf4j
public class ProvisionServiceImpl implements ProvisionService {
  private static final String ALPHANUMERIC = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
  private static SecureRandom rnd = new SecureRandom();
  private static final int SECRET_LENGTH = 32;
  @Inject @Named(PROVISION_MODULE_CONFIG) ProvisionModuleConfig provisionModuleConfig;
  private final Retry retry = buildRetryAndRegisterListeners();
  private final MediaType APPLICATION_JSON = MediaType.parse("application/json");
  @Inject NgConnectorManagerClient ngConnectorManagerClient;
  private static final List<String> permissions =
      List.of("user_read", "user_update", "user_delete", "owner_read", "owner_update", "owner_delete", "all_create");
  @Inject BackstagePermissionsService backstagePermissionsService;
  @Inject BackstageEnvVariableService backstageEnvVariableService;
  @Inject @Named("PRIVILEGED") private SecretManagerClientService ngSecretService;

  @Override
  public void triggerPipelineAndCreatePermissions(String accountIdentifier, String namespace) {
    createBackstageBackendSecret(accountIdentifier);
    createDefaultPermissions(accountIdentifier);
    makeTriggerApi(accountIdentifier, namespace);
  }

  public void createDefaultPermissions(String accountIdentifier) {
    try {
      BackstagePermissions backstagePermissions = new BackstagePermissions();
      backstagePermissions.setUserGroup(" ");
      backstagePermissions.setPermissions(permissions);
      backstagePermissionsService.createPermissions(backstagePermissions, accountIdentifier);
    } catch (DuplicateKeyException e) {
      String logMessage = String.format("Permissions already created for given account Id - %s", accountIdentifier);
      log.info(logMessage);
    } catch (Exception e) {
      log.error(e.getMessage());
      throw new InvalidRequestException(e.getMessage());
    }
  }

  public void createBackstageBackendSecret(String accountIdentifier) {
    String actualSecret = generateEncodedSecret();
    SecretRequestWrapper secretRequestWrapper =
        SecretRequestWrapper.builder()
            .secret(SecretDTOV2.builder()
                        .identifier(Constants.IDP_BACKEND_SECRET)
                        .name(Constants.IDP_BACKEND_SECRET)
                        .description("Auto Generated Secret for Backstage Backend")
                        .type(SecretType.SecretText)
                        .spec(SecretTextSpecDTO.builder()
                                  .secretManagerIdentifier("harnessSecretManager")
                                  .value(actualSecret)
                                  .valueType(ValueType.Inline)
                                  .build())
                        .build())
            .build();

    SecretResponseWrapper secretDto = createSecret(accountIdentifier, secretRequestWrapper);
    BackstageEnvSecretVariable backstageEnvSecretVariable = new BackstageEnvSecretVariable();
    backstageEnvSecretVariable.setEnvName(Constants.BACKEND_SECRET);
    backstageEnvSecretVariable.setHarnessSecretIdentifier(secretDto.getSecret().getIdentifier());
    backstageEnvSecretVariable.setType(BackstageEnvVariable.TypeEnum.SECRET);
    backstageEnvVariableService.create(backstageEnvSecretVariable, accountIdentifier);
    log.info("Created BACKEND_SECRET for account Id - {}", accountIdentifier);
  }

  private SecretResponseWrapper createSecret(String accountIdentifier, SecretRequestWrapper secretRequestWrapper) {
    // Source principal should match the owner in case of a private secret
    // In our case, the source principal is USER, but the owner is IDP Service which is set while creating the client
    // Hence we are setting source principal manually to IDPService and unsetting it after the create call.
    Principal currentPrincipal = SourcePrincipalContextBuilder.getSourcePrincipal();
    SourcePrincipalContextBuilder.setSourcePrincipal(
        new ServicePrincipal(AuthorizationServiceHeader.IDP_SERVICE.getServiceId()));
    SecretResponseWrapper dto = ngSecretService.create(accountIdentifier, null, null, true, secretRequestWrapper);
    SourcePrincipalContextBuilder.setSourcePrincipal(currentPrincipal);
    return dto;
  }

  public static String generateEncodedSecret() {
    return Base64.getEncoder().encodeToString(generateSecret().getBytes());
  }

  static String generateSecret() {
    StringBuilder sb = new StringBuilder(SECRET_LENGTH);
    for (int i = 0; i < SECRET_LENGTH; i++) {
      sb.append(ALPHANUMERIC.charAt(rnd.nextInt(ALPHANUMERIC.length())));
    }
    return sb.toString();
  }

  private void makeTriggerApi(String accountIdentifier, String namespace) {
    Request request = createHttpRequest(accountIdentifier, namespace);
    OkHttpClient client = new OkHttpClient();
    Supplier<Response> response = Retry.decorateSupplier(retry, () -> {
      try {
        return client.newCall(request).execute();
      } catch (IOException e) {
        String errMessage = "Error occurred while reaching pipeline trigger API";
        log.error(errMessage, e);
        throw new InvalidRequestException(errMessage);
      }
    });

    if (!response.get().isSuccessful()) {
      throw new InvalidRequestException("Pipeline Trigger http call failed");
    }
  }

  private Request createHttpRequest(String accountIdentifier, String namespace) {
    String url = provisionModuleConfig.getTriggerPipelineUrl();

    JSONObject jsonObject = new JSONObject();
    jsonObject.put(ACCOUNT_ID, accountIdentifier);
    jsonObject.put(NAMESPACE, namespace);

    RequestBody requestBody = RequestBody.create(jsonObject.toString(), APPLICATION_JSON);

    return new Request.Builder().url(url).post(requestBody).build();
  }

  private Retry buildRetryAndRegisterListeners() {
    final Retry exponentialRetry = RetryHelper.getExponentialRetry(this.getClass().getSimpleName(),
        new Class[] {ConnectException.class, TimeoutException.class, ConnectionShutdownException.class,
            StreamResetException.class});
    RetryHelper.registerEventListeners(exponentialRetry);
    return exponentialRetry;
  }
}
