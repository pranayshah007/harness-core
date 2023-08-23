/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

public class SecretManagerDescriptionConstants {
  public static final String HARNESS_MANAGED =
      "Set this to true if the Secret Manager is to be managed by Harness. Harness can manage and edit this Secret Manager in this case.";
  public static final String DEFAULT =
      "Boolean value to indicate if the Secret Manager is your default Secret Manager.";
  public static final String DELEGATE_SELECTORS =
      "List of Delegate Selectors that belong to the same Delegate and are used to connect to the Secret Manager.";
  public static final String ASSUME_CREDENTIALS_ON_DELEGATE =
      "Boolean value to indicate that Credentials are taken from the Delegate.";
  public static final String BASE_PATH = "This is the location of the Vault directory where Secret will be stored.";
  public static final String AUTH_TOKEN = "This is the authentication token for Vault.";
  public static final String NAMESPACE = "This is the Vault namespace where Secret will be created.";
  public static final String VAULT_AWS_IAM_ROLE =
      "This is the Vault role defined to bind to aws iam account/role being accessed.";
  public static final String USE_VAULT_AGENT = "Boolean value to indicate if Vault Agent is used for authentication.";
  public static final String USE_AWS_IAM = "Boolean value to indicate if Aws Iam is used for authentication.";
  public static final String AWS_REGION = "This is the Aws region where aws iam auth will happen.";
  public static final String VAULT_AWS_IAM_HEADER =
      "This is the Aws Iam Header Server ID that has been configured for this Aws Iam instance.";
  public static final String VAULT_URL = "URL of the HashiCorp Vault.";
  public static final String READ_ONLY = "Boolean value to indicate if the Secret Manager created is read only.";
  public static final String RENEWAL_INTERVAL_MINUTES = "This is the time interval for token renewal.";
  public static final String SECRET_ENGINE_NAME = "Name of the Secret Engine.";
  public static final String APP_ROLE_ID = "ID of App Role.";
  public static final String SECRET_ID = "ID of the Secret.";
  public static final String SECRET_ENGINE_VERSION = "Version of Secret Engine.";
  public static final String ENGINE_ENTERED_MANUALLY = "Manually entered Secret Engine.";
  public static final String AWS_CREDENTIAL =
      "This indicates AWS credential types, Manual Credential, Assume IAM Role, Assume STS Role.";
  public static final String USE_K8s_AUTH = "Boolean value to indicate if K8s Auth is used for authentication.";
  public static final String VAULT_K8S_AUTH_ROLE = "This is the role where K8s auth will happen.";
  public static final String SERVICE_ACCOUNT_TOKEN_PATH =
      "This is the SA token path where the token is mounted in the K8s Pod.";
  public static final String K8S_AUTH_ENDPOINT = "This is the path where kubernetes auth is enabled in Vault.";
  public static final String RENEW_APPROLE_TOKEN =
      "Boolean value to indicate if appRole token renewal is enabled or not.";
  public static final String ACCESS_KEY = "Access Key for AWS authentication.";
  public static final String SECRET_KEY = "Secret Key for AWS authentication.";
  public static final String ROLE_ARN = "Role ARN for the Delegate with STS Role.";
  public static final String EXTERNAL_NAME = "External Name.";
  public static final String ASSUME_STS_ROLE_DURATION = "This is the time duration for STS Role.";
  public static final String SINK_PATH = "This is the location at which auth token is to be read from.";
  public static final String AWS_AUTH_CRED_KMS = "Type of Credential to be used to authenticate AWS KMS.";
  public static final String AWS_ARN_KMS = "ARN for AWS KMS.";
  public static final String AWS_REGION_KMS = "Region for AWS KMS.";
  public static final String AWS_SM_CONFIG = "Returns AWS Secret Manager configuration details.";
  public static final String AWS_AUTH_CRED_SM = "Type of Credential to be used to authenticate AWS KMS.";
  public static final String AWS_REGION_SM = "Region for AWS SM.";
  public static final String AWS_SECRET_NAME_PREFIX = "Text that is prepended to the Secret name as a prefix.";
  public static final String GCP_KMS_PROJECT_ID = "ID of the project on GCP.";
  public static final String GCP_KMS_REGION = "Region for GCP KMS";
  public static final String GCP_KEYRING = "Name of the Key Ring where Google Cloud Symmetric Key is created.";
  public static final String GCP_KEYNAME = "Name of the Google Cloud Symmetric Key.";
  public static final String GCP_CRED_FILE = "File Secret which is Service Account Key.";
  public static final String CUSTOM_AUTH_TOKEN =
      "This is the authentication token used to connect underlying secret manager.";
  public static final String GOOGLE_SECRET_MANAGER_CREDENTIALS =
      "Reference to the secret containing credentials of IAM service account for Google Secret Manager";
  public static final String ENABLE_CACHE = "Boolean value to indicate if cache is enabled for App Role Token.";
}
