/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector.entities.embedded.vaultconnector;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.entities.Connector;
import io.harness.iterator.PersistentRegularIterable;
import io.harness.mongo.index.FdIndex;
import io.harness.security.encryption.AccessType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.NonFinal;
import org.mongodb.morphia.annotations.Entity;
import org.springframework.data.annotation.Persistent;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(PL)
@Value
@Builder
@EqualsAndHashCode(callSuper = true)
@FieldNameConstants(innerTypeName = "VaultConnectorKeys")
@JsonInclude(JsonInclude.Include.NON_NULL)
@Entity(value = "connectors", noClassnameStored = true)
@Persistent
@TypeAlias("io.harness.connector.entities.embedded.vaultconnector.VaultConnector")
@JsonIgnoreProperties(ignoreUnknown = true)
public class VaultConnector extends Connector implements PersistentRegularIterable {
  String authTokenRef;
  String vaultUrl;
  String secretEngineName;
  String appRoleId;
  boolean isDefault;
  boolean isReadOnly;
  AccessType accessType;
  int secretEngineVersion;
  String secretIdRef;
  @FdIndex @NonFinal Long nextTokenRenewIteration;
  @Getter(AccessLevel.NONE) @NonFinal Long renewalIntervalMinutes;
  @Getter(AccessLevel.NONE) Boolean secretEngineManuallyConfigured;
  @Getter(AccessLevel.NONE) String basePath;
  String namespace;
  String sinkPath;

  @Builder.Default Boolean useVaultAgent = Boolean.FALSE;
  @Setter @NonFinal Long renewedAt;

  @Builder.Default Boolean useAwsIam = Boolean.FALSE;
  String awsRegion;
  String vaultAwsIamRoleRef;
  String xVaultAwsIamServerIdRef;

  @Builder.Default Boolean useK8sAuth = Boolean.FALSE;
  String vaultK8sAuthRole;
  String serviceAccountTokenPath;

  public long getRenewedAt() {
    if (renewedAt == null) {
      return 0;
    }
    return renewedAt;
  }

  public long getRenewalIntervalMinutes() {
    if (renewalIntervalMinutes == null) {
      return 0;
    }
    return renewalIntervalMinutes;
  }

  public boolean isSecretEngineManuallyConfigured() {
    return secretEngineManuallyConfigured != null && secretEngineManuallyConfigured;
  }

  public String getBasePath() {
    return Optional.ofNullable(basePath).filter(x -> !x.isEmpty()).orElse("/harness");
  }

  @Override
  public Long obtainNextIteration(String fieldName) {
    if (VaultConnectorKeys.nextTokenRenewIteration.equals(fieldName)) {
      return nextTokenRenewIteration;
    }
    throw new IllegalArgumentException("Invalid fieldName " + fieldName);
  }

  @Override
  public void updateNextIteration(String fieldName, long nextIteration) {
    if (VaultConnectorKeys.nextTokenRenewIteration.equals(fieldName)) {
      this.nextTokenRenewIteration = nextIteration;
      return;
    }
    throw new IllegalArgumentException("Invalid fieldName " + fieldName);
  }

  public Boolean isUseVaultAgent() {
    return useVaultAgent == null ? Boolean.FALSE : useVaultAgent;
  }

  public Boolean getUseAwsIam() {
    return useAwsIam == null ? Boolean.FALSE : useAwsIam;
  }

  public Boolean getUseK8sAuth() {
    return useK8sAuth == null ? Boolean.FALSE : useK8sAuth;
  }
}
