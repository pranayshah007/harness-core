/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.billing;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;
import static java.util.Collections.singletonList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.setup.config.CESetUpConfig;

import software.wings.app.MainConfiguration;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.iam.v1.Iam;
import com.google.api.services.iam.v1.IamScopes;
import com.google.api.services.iam.v1.model.Binding;
import com.google.api.services.iam.v1.model.CreateServiceAccountRequest;
import com.google.api.services.iam.v1.model.Policy;
import com.google.api.services.iam.v1.model.ServiceAccount;
import com.google.api.services.iam.v1.model.SetIamPolicyRequest;
import com.google.auth.Credentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ImpersonatedCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(CE)
public class GcpServiceAccountServiceImpl implements GcpServiceAccountService {
  public static final String CE_GCP_CREDENTIALS_PATH = "CE_GCP_CREDENTIALS_PATH";
  private Iam iamService;

  @Inject private MainConfiguration mainConfiguration;
  @Inject private GcpResourceManagerService gcpResourceManagerService;

  private void initService() {
    if (iamService != null) {
      return;
    }
    try {
      ServiceAccountCredentials serviceAccountCredentials = getCredentials(CE_GCP_CREDENTIALS_PATH);
      if (serviceAccountCredentials == null) {
        return;
      }
      GoogleCredential googleCredential = toGoogleCredential(serviceAccountCredentials);
      iamService = new Iam
                       .Builder(GoogleNetHttpTransport.newTrustedTransport(), JacksonFactory.getDefaultInstance(),
                           googleCredential)
                       .setApplicationName("service-accounts")
                       .build();
    } catch (IOException | GeneralSecurityException e) {
      log.error("Unable to initialize service.", e);
    }
  }

  @Override
  public ServiceAccount create(String serviceAccountId, String displayName) throws IOException {
    initService();
    ServiceAccount serviceAccount = null;

    serviceAccount = new ServiceAccount();
    serviceAccount.setDisplayName(displayName);

    CreateServiceAccountRequest request = new CreateServiceAccountRequest();
    request.setAccountId(serviceAccountId);
    request.setServiceAccount(serviceAccount);
    CESetUpConfig ceSetUpConfig = mainConfiguration.getCeSetUpConfig();
    String projectId = ceSetUpConfig.getGcpProjectId();

    log.info("Creating the service account {} in project {}.", serviceAccountId, projectId);
    return iamService.projects().serviceAccounts().create("projects/" + projectId, request).execute();
  }

  @Override
  public void setIamPolicies(String serviceAccountEmail) throws IOException {
    initService();
    String resource = format("projects/-/serviceAccounts/%s", serviceAccountEmail);

    SetIamPolicyRequest requestBody = new SetIamPolicyRequest();
    Policy policy = new Policy();
    policy.setEtag("ACAB");
    ServiceAccountCredentials serviceAccountCredentials = getCredentials(CE_GCP_CREDENTIALS_PATH);
    String member = serviceAccountCredentials.getClientEmail();
    Binding binding1 = new Binding();
    binding1.setRole("roles/iam.serviceAccountUser");
    binding1.setMembers(singletonList(format("serviceAccount:%s", member)));
    Binding binding2 = new Binding();
    binding2.setRole("roles/iam.serviceAccountTokenCreator");
    binding2.setMembers(singletonList(format("serviceAccount:%s", member)));
    policy.setBindings(Arrays.asList(binding1, binding2));
    requestBody.setPolicy(policy);

    Iam.Projects.ServiceAccounts.SetIamPolicy request =
        iamService.projects().serviceAccounts().setIamPolicy(resource, requestBody);
    request.execute();
  }

  public void addRolesToServiceAccount(String serviceAccountEmail, String[] roles) {
    CESetUpConfig ceSetUpConfig = mainConfiguration.getCeSetUpConfig();
    String projectId = ceSetUpConfig.getGcpProjectId();
    com.google.api.services.cloudresourcemanager.model.Policy projectPolicy =
        gcpResourceManagerService.getIamPolicy(projectId); // TODO: should check if the policy is empty
    for (String role : roles) {
      // Add roles in the service account
      log.info("Adding role {} to gcpServiceAccount {}", role, serviceAccountEmail);
      GcpResourceManagerUtils.addBinding(projectPolicy, role, format("serviceAccount:%s", serviceAccountEmail));
      log.info("Added role {} to gcpServiceAccount {}", role, serviceAccountEmail);
    }
    gcpResourceManagerService.setPolicy(projectId, projectPolicy);
  }

  // read the credential path from env variables
  public static ServiceAccountCredentials getCredentials(String googleCredentialPathSystemEnv) {
    String googleCredentialsPath = System.getenv(googleCredentialPathSystemEnv);
    Preconditions.checkState(isNotEmpty(googleCredentialsPath), "Missing environment variable for GCP credentials.");
    File credentialsFile = new File(googleCredentialsPath);
    ServiceAccountCredentials credentials = null;
    try (FileInputStream serviceAccountStream = new FileInputStream(credentialsFile)) {
      credentials = ServiceAccountCredentials.fromStream(serviceAccountStream);
    } catch (FileNotFoundException e) {
      log.error("Failed to find Google credential file for the CE service account in the specified path.", e);
    } catch (IOException e) {
      log.error("Failed to get Google credential file for the CE service account.", e);
    }
    return credentials;
  }

  public static Credentials getImpersonatedCredentials(
      GoogleCredentials sourceCredentials, String impersonatedServiceAccount) {
    if (impersonatedServiceAccount == null) {
      return sourceCredentials;
    } else {
      return ImpersonatedCredentials.create(sourceCredentials, impersonatedServiceAccount, null,
          Arrays.asList("https://www.googleapis.com/auth/cloud-platform"), 300);
    }
  }

  public static GoogleCredential toGoogleCredential(ServiceAccountCredentials serviceAccountCredentials)
      throws GeneralSecurityException, IOException {
    HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
    JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
    return new GoogleCredential.Builder()
        .setServiceAccountProjectId(serviceAccountCredentials.getProjectId())
        .setServiceAccountId(serviceAccountCredentials.getClientEmail())
        .setServiceAccountPrivateKeyId(serviceAccountCredentials.getPrivateKeyId())
        .setServiceAccountPrivateKey(serviceAccountCredentials.getPrivateKey())
        .setTransport(httpTransport)
        .setJsonFactory(jsonFactory)
        .setServiceAccountScopes(singletonList(IamScopes.CLOUD_PLATFORM))
        .build();
  }
}
