/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.helpers.vault;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.VAULT_OPERATION_ERROR;
import static io.harness.exception.WingsException.USER;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.SecretManagementDelegateException;
import io.harness.helpers.ext.vault.VaultAppRoleLoginResult;
import io.harness.security.encryption.AccessType;

import software.wings.beans.BaseVaultConfig;
import software.wings.helpers.ext.vault.VaultAppRoleLoginRequest;
import software.wings.helpers.ext.vault.VaultAppRoleLoginResponse;
import software.wings.helpers.ext.vault.VaultAwsIamAuthLoginRequest;
import software.wings.helpers.ext.vault.VaultK8sAuthLoginRequest;
import software.wings.helpers.ext.vault.VaultK8sLoginResponse;
import software.wings.helpers.ext.vault.VaultK8sLoginResult;
import software.wings.helpers.ext.vault.VaultRestClientFactory;
import software.wings.helpers.ext.vault.VaultSysAuthRestClient;
import software.wings.helpers.ext.vault.VaultTokenLookupResponse;
import software.wings.helpers.ext.vault.VaultTokenLookupResult;

import com.amazonaws.DefaultRequest;
import com.amazonaws.auth.AWS4Signer;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.http.HttpMethodName;
import com.google.common.collect.LinkedHashMultimap;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import retrofit2.Response;

@UtilityClass
@OwnedBy(PL)
@Slf4j
public class NGVaultTaskHelper {
  public static final String AWS_IAM_REQUEST_BODY = "Action=GetCallerIdentity&Version=2011-06-15";
  public static final String AWS_IAM_ENDPOINT_FORMAT = "https://sts.%s.amazonaws.com";
  public static final String X_VAULT_AWS_IAM_SERVER_ID = "X-Vault-AWS-IAM-Server-ID";
  public static final String K8s_AUTH_DEFAULT_ENDPOINT = "kubernetes";
  public static final String CONTENT_TYPE = "Content-Type";
  public static final String APPLICATION_X_WWW_FORM_URLENCODED_CHARSET_UTF_8 =
      "application/x-www-form-urlencoded; charset=utf-8";
  public static final String STS = "sts";

  public static VaultAppRoleLoginResult getVaultAppRoleLoginResult(BaseVaultConfig vaultConfig) {
    try {
      VaultSysAuthRestClient restClient =
          VaultRestClientFactory.getVaultRetrofit(vaultConfig.getVaultUrl(), vaultConfig.isCertValidationRequired())
              .create(VaultSysAuthRestClient.class);

      VaultAppRoleLoginRequest loginRequest = VaultAppRoleLoginRequest.builder()
                                                  .roleId(vaultConfig.getAppRoleId())
                                                  .secretId(vaultConfig.getSecretId())
                                                  .build();
      Response<VaultAppRoleLoginResponse> response =
          restClient.appRoleLogin(vaultConfig.getNamespace(), loginRequest).execute();

      VaultAppRoleLoginResult result = null;
      if (response.isSuccessful()) {
        result = response.body().getAuth();
      } else {
        logAndThrowVaultError(vaultConfig, response, "AppRole Based Login");
      }
      return result;
    } catch (IOException e) {
      String message = "NG: Failed to perform AppRole based login for secret manager " + vaultConfig.getName() + " at "
          + vaultConfig.getVaultUrl();
      throw new SecretManagementDelegateException(VAULT_OPERATION_ERROR, message, e, USER);
    }
  }

  public static VaultTokenLookupResult getVaultTokenLookupResult(BaseVaultConfig vaultConfig) {
    try {
      VaultSysAuthRestClient restClient =
          VaultRestClientFactory.getVaultRetrofit(vaultConfig.getVaultUrl(), vaultConfig.isCertValidationRequired())
              .create(VaultSysAuthRestClient.class);

      Response<VaultTokenLookupResponse> response =
          restClient.tokenLookup(vaultConfig.getAuthToken(), vaultConfig.getNamespace()).execute();
      VaultTokenLookupResult result = null;
      if (response.isSuccessful()) {
        result = response.body().getData();
      } else {
        logAndThrowVaultError(vaultConfig, response, "Token lookup");
      }
      return result;
    } catch (IOException e) {
      String message = "Failed to perform Token Lookup for secret manager " + vaultConfig.getName() + " at "
          + vaultConfig.getVaultUrl();
      throw new SecretManagementDelegateException(VAULT_OPERATION_ERROR, message, e, USER);
    }
  }

  public static VaultAppRoleLoginResult getVaultAwmIamAuthLoginResult(BaseVaultConfig vaultConfig) {
    validateVaultConfigAwsIam(vaultConfig);
    try {
      VaultSysAuthRestClient restClient =
          VaultRestClientFactory.getVaultRetrofit(vaultConfig.getVaultUrl(), vaultConfig.isCertValidationRequired())
              .create(VaultSysAuthRestClient.class);

      String base64EncodedRequestBody = getBase64EncodedRequestBody();
      String base64EncodedRequestUrl = getBase64EncodedRequestUrl(vaultConfig.getAwsRegion());
      String iamRequestHeaders = getBase64EncodedRequestHeaders(vaultConfig);

      VaultAwsIamAuthLoginRequest loginRequest = VaultAwsIamAuthLoginRequest.builder()
                                                     .roleId(vaultConfig.getVaultAwsIamRole())
                                                     .iamHttpRequestMethod("POST")
                                                     .iamRequestHeaders(iamRequestHeaders)
                                                     .iamRequestBody(base64EncodedRequestBody)
                                                     .iamRequestUrl(base64EncodedRequestUrl)
                                                     .build();
      Response<VaultAppRoleLoginResponse> response =
          restClient.awsIamAuthLogin(vaultConfig.getNamespace(), loginRequest).execute();

      VaultAppRoleLoginResult result = null;
      if (response.isSuccessful()) {
        result = response.body().getAuth();
      } else {
        logAndThrowVaultError(vaultConfig, response, "Aws IAM Auth based login");
      }
      return result;
    } catch (IOException | URISyntaxException e) {
      String message = "NG: Failed to perform Aws IAM Auth based login for secret manager " + vaultConfig.getName()
          + " at " + vaultConfig.getVaultUrl();
      throw new SecretManagementDelegateException(VAULT_OPERATION_ERROR, message, e, USER);
    }
  }

  public static VaultK8sLoginResult getVaultK8sAuthLoginResult(BaseVaultConfig vaultConfig) {
    validateVaultConfigK8sAuth(vaultConfig);
    try {
      VaultSysAuthRestClient restClient =
          VaultRestClientFactory.getVaultRetrofit(vaultConfig.getVaultUrl(), vaultConfig.isCertValidationRequired())
              .create(VaultSysAuthRestClient.class);

      String jwt;
      try {
        byte[] content = Files.readAllBytes(Paths.get(vaultConfig.getServiceAccountTokenPath()));
        jwt = new String(content);
      } catch (IOException e) {
        throw new SecretManagementDelegateException(VAULT_OPERATION_ERROR,
            "Unable to read service account token From the service account token Path: "
                + vaultConfig.getServiceAccountTokenPath(),
            e, USER);
      }
      VaultK8sAuthLoginRequest loginRequest =
          VaultK8sAuthLoginRequest.builder().role(vaultConfig.getVaultK8sAuthRole()).jwt(jwt).build();
      Response<VaultK8sLoginResponse> response =
          restClient
              .k8sAuthLogin(isNotEmpty(vaultConfig.getK8sAuthEndpoint()) ? vaultConfig.getK8sAuthEndpoint()
                                                                         : K8s_AUTH_DEFAULT_ENDPOINT,
                  vaultConfig.getNamespace(), loginRequest)
              .execute();

      VaultK8sLoginResult result = null;
      if (response.isSuccessful()) {
        result = response.body().getAuth();
      } else {
        logAndThrowVaultError(vaultConfig, response, "K8s Auth based login");
      }
      return result;
    } catch (IOException e) {
      String message = "NG: Failed to perform K8s Auth based login for secret manager " + vaultConfig.getName() + " at "
          + vaultConfig.getVaultUrl();
      throw new SecretManagementDelegateException(VAULT_OPERATION_ERROR, message, e, USER);
    }
  }

  private static void validateVaultConfigAwsIam(BaseVaultConfig vaultConfig) {
    if (vaultConfig.isUseAwsIam()) {
      if (isBlank(vaultConfig.getVaultAwsIamRole())) {
        throw new io.harness.exception.SecretManagementException(
            VAULT_OPERATION_ERROR, "You must provide vault role if you are using Vault with Aws Iam Auth method", USER);
      }
      if (isBlank(vaultConfig.getAwsRegion())) {
        throw new io.harness.exception.SecretManagementException(
            VAULT_OPERATION_ERROR, "You must provide aws region if you are using Vault with Aws Iam Auth method", USER);
      }
      if (isEmpty(vaultConfig.getDelegateSelectors())) {
        throw new io.harness.exception.SecretManagementException(
            VAULT_OPERATION_ERROR, "You must provide a delegate selector to read token if you are using Aws Iam", USER);
      }
    }
  }

  private static void validateVaultConfigK8sAuth(BaseVaultConfig vaultConfig) {
    if (vaultConfig.isUseK8sAuth()) {
      if (isBlank(vaultConfig.getVaultK8sAuthRole())) {
        throw new io.harness.exception.SecretManagementException(
            VAULT_OPERATION_ERROR, "You must provide vault role if you are using Vault with K8s Auth method", USER);
      }
      if (isBlank(vaultConfig.getServiceAccountTokenPath())) {
        throw new io.harness.exception.SecretManagementException(VAULT_OPERATION_ERROR,
            "You must provide service account token path if you are using Vault with K8s Auth method", USER);
      }
      if (isEmpty(vaultConfig.getDelegateSelectors())) {
        throw new io.harness.exception.SecretManagementException(VAULT_OPERATION_ERROR,
            "You must provide a delegate selector to read service account token if you are using K8s Auth method",
            USER);
      }
    }
  }

  public static void logAndThrowVaultError(BaseVaultConfig baseVaultConfig, Response response, String operation)
      throws IOException {
    if (baseVaultConfig == null || response == null) {
      return;
    }
    String errorMsg = "";
    if (response.errorBody() != null) {
      errorMsg = String.format(
          "Failed to %s for Vault: %s And Namespace: %s due to the following error from vault: \"%s\" \"%s\".",
          operation, baseVaultConfig.getName(), baseVaultConfig.getNamespace(), response.message(),
          response.errorBody().string());
    } else {
      errorMsg = String.format(
          "Failed to %s for Vault: %s And Namespace: %s due to the following error from vault: \"%s\".", operation,
          baseVaultConfig.getName(), baseVaultConfig.getNamespace(), response.message() + response.body());
    }
    throw new SecretManagementDelegateException(VAULT_OPERATION_ERROR, errorMsg, USER);
  }

  public static String getToken(BaseVaultConfig vaultConfig) {
    if (vaultConfig.isUseVaultAgent()) {
      try {
        byte[] content = Files.readAllBytes(Paths.get(URI.create("file://" + vaultConfig.getSinkPath())));
        String token = new String(content);
        vaultConfig.setAuthToken(token);
      } catch (IOException e) {
        throw new SecretManagementDelegateException(VAULT_OPERATION_ERROR,
            "Using Vault Agent Cannot read Token From Sink Path:" + vaultConfig.getSinkPath(), e, USER);
      }
    } else if (vaultConfig.isUseAwsIam()) {
      VaultAppRoleLoginResult vaultAwmIamAuthLoginResult = getVaultAwmIamAuthLoginResult(vaultConfig);
      vaultConfig.setAuthToken(vaultAwmIamAuthLoginResult.getClientToken());
    } else if (vaultConfig.isUseK8sAuth()) {
      VaultK8sLoginResult vaultK8sLoginResult = getVaultK8sAuthLoginResult(vaultConfig);
      vaultConfig.setAuthToken(vaultK8sLoginResult.getClientToken());
    } else if (AccessType.APP_ROLE.equals(vaultConfig.getAccessType()) && !vaultConfig.getRenewAppRoleToken()) {
      vaultConfig.setAuthToken(getVaultAppRoleToken(vaultConfig));
    }
    return vaultConfig.getAuthToken();
  }

  private static String getBase64EncodedRequestBody() {
    return Base64.getEncoder().encodeToString(AWS_IAM_REQUEST_BODY.getBytes(StandardCharsets.UTF_8));
  }

  private static String getBase64EncodedRequestUrl(String region) {
    return Base64.getEncoder().encodeToString(getEndPoint(region).getBytes(StandardCharsets.UTF_8));
  }

  private static String getEndPoint(String region) {
    return String.format(AWS_IAM_ENDPOINT_FORMAT, region);
  }

  private static String getBase64EncodedRequestHeaders(BaseVaultConfig vaultConfig) throws URISyntaxException {
    final LinkedHashMap<String, String> headers = new LinkedHashMap<>();
    if (isNotBlank(vaultConfig.getXVaultAwsIamServerId())) {
      headers.put(X_VAULT_AWS_IAM_SERVER_ID, vaultConfig.getXVaultAwsIamServerId());
    }
    headers.put(CONTENT_TYPE, APPLICATION_X_WWW_FORM_URLENCODED_CHARSET_UTF_8);

    final DefaultRequest defaultRequest = new DefaultRequest(STS);
    defaultRequest.setContent(new ByteArrayInputStream(AWS_IAM_REQUEST_BODY.getBytes(StandardCharsets.UTF_8)));
    defaultRequest.setHeaders(headers);
    defaultRequest.setHttpMethod(HttpMethodName.POST);
    defaultRequest.setEndpoint(new URI(getEndPoint(vaultConfig.getAwsRegion())));

    final AWS4Signer aws4Signer = new AWS4Signer();
    aws4Signer.setServiceName(defaultRequest.getServiceName());
    aws4Signer.setRegionName(vaultConfig.getAwsRegion());
    AWSCredentials awsCredentials = new DefaultAWSCredentialsProviderChain().getCredentials();
    aws4Signer.sign(defaultRequest, awsCredentials);

    final LinkedHashMultimap<String, String> signedHeaders = LinkedHashMultimap.create();
    final Map<String, String> defaultRequestHeaders = defaultRequest.getHeaders();
    defaultRequestHeaders.forEach((k, v) -> signedHeaders.put(k, v));

    final JsonObject jsonObject = new JsonObject();
    signedHeaders.asMap().forEach((k, v) -> {
      final JsonArray array = new JsonArray();
      v.forEach(array::add);
      jsonObject.add(k, array);
    });

    final String signedHeaderString = jsonObject.toString();
    return Base64.getEncoder().encodeToString(signedHeaderString.getBytes(StandardCharsets.UTF_8));
  }

  public static String getVaultAppRoleToken(BaseVaultConfig vaultConfig) {
    String token;
    if (vaultConfig.getEnableCache()) {
      token = HashicorpVaultTokenCacheHelper.getAppRoleToken(vaultConfig);
      if (isEmpty(token)) {
        VaultAppRoleLoginResult vaultAppRoleLoginResult = getVaultAppRoleLoginResult(vaultConfig);
        HashicorpVaultTokenCacheHelper.putInAppRoleTokenCache(vaultConfig, vaultAppRoleLoginResult);
        token = vaultAppRoleLoginResult.getClientToken();
      }
    } else {
      VaultAppRoleLoginResult vaultAppRoleLoginResult = getVaultAppRoleLoginResult(vaultConfig);
      token = vaultAppRoleLoginResult.getClientToken();
    }
    return token;
  }
}
