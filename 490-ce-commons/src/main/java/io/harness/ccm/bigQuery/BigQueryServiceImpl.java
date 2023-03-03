/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.bigQuery;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.inject.Singleton;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class BigQueryServiceImpl implements BigQueryService {
  public static final String GCP_CREDENTIALS_PATH = "GOOGLE_CREDENTIALS_PATH";

  @Override
  public BigQuery get() {
    BigQueryOptions.Builder bigQueryOptionsBuilder = null;
    boolean usingWorkloadIdentity = Boolean.parseBoolean(System.getenv("USE_WORKLOAD_IDENTITY"));
    if (!usingWorkloadIdentity) {
      ServiceAccountCredentials credentials = getCredentials(GCP_CREDENTIALS_PATH);
      log.info("WI: Initializing BQ with JSON Key file");
      bigQueryOptionsBuilder = BigQueryOptions.newBuilder().setCredentials(credentials);
    } else {
      log.info("WI: Initializing BQ with Google ADC");
      bigQueryOptionsBuilder = BigQueryOptions.newBuilder().setProjectId(System.getenv("GCP_PROJECT_ID"));
    }
    return bigQueryOptionsBuilder.build().getService();
  }

  public ServiceAccountCredentials getCredentials(String googleCredentialPathSystemEnv) {
    String googleCredentialsPath = System.getenv(googleCredentialPathSystemEnv);
    if (isEmpty(googleCredentialsPath)) {
      log.error("Missing environment variable for GCP credentials.");
    }
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
}
