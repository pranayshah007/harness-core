/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.azureconnector;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

@OwnedBy(HarnessTeam.CDP)
public class AzureConstants {
  public static final String INHERIT_FROM_DELEGATE = "InheritFromDelegate";
  public static final String MANUAL_CONFIG = "ManualConfig";
  public static final String SECRET_KEY = "Secret";
  public static final String KEY_CERT = "Certificate";
  public static final String SYSTEM_ASSIGNED_MANAGED_IDENTITY = "SystemAssignedManagedIdentity";
  public static final String USER_ASSIGNED_MANAGED_IDENTITY = "UserAssignedManagedIdentity";
}
