/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

@OwnedBy(CDP)
public final class ConnectorTestConstants {
  private ConnectorTestConstants() {}

  public static final String ACCOUNT_IDENTIFIER = "accountIdentifier";
  public static final String ORG_IDENTIFIER = "orgIdentifier";
  public static final String PROJECT_IDENTIFIER = "projectIdentifier";
  public static final String CONNECTOR_NAME = "connectorName";
  public static final String CONNECTOR_IDENTIFIER = "connectorIdentifier";
  public static final String SECRET_IDENTIFIER = "secretIdentifier";
  public static final String SSK_KEY_REF_IDENTIFIER = "sskKeyRefIdentifier";
  public static final String SSK_KEY_REF_IDENTIFIER_WITH_ACCOUNT_SCOPE = "account.sskKeyRefIdentifier";
  public static final String DELEGATE_SELECTOR = "delegate_selector_group";

  public static final String HOST_NAME = "1.1.1.1";
  public static final String HOST_WITH_PORT = "1.1.1.1:8080";
  public static final String HOST_NAME_1 = "hostName1";
  public static final String HOST_NAME_2 = "hostName2";
  public static final String ATTRIBUTE_TYPE_1 = "attributeType1";
  public static final String ATTRIBUTE_NAME_1 = "attributeName1";
  public static final String ATTRIBUTE_TYPE_2 = "attributeType2";
  public static final String ATTRIBUTE_NAME_2 = "attributeName2";
}
