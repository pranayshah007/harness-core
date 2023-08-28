/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.scorecard.datapointsdata.dsldataprovider;

public class DslConstants {
  public static final String CI_ACCOUNT_IDENTIFIER_KEY = "ciAccountIdentifier";

  public static final String CI_ORG_IDENTIFIER_KEY = "ciOrgIdentifier";

  public static final String CI_PROJECT_IDENTIFIER_KEY = "ciProjectIdentifier";
  public static final String CI_PIPELINE_IDENTIFIER_KEY = "ciPipelineIdentifier";

  public static final String CD_ACCOUNT_IDENTIFIER_KEY = "ciAccountIdentifier";
  public static final String CD_ORG_IDENTIFIER_KEY = "cdOrgIdentifier";
  public static final String CD_PROJECT_IDENTIFIER_KEY = "cdProjectIdentifier";
  public static final String CD_SERVICE_IDENTIFIER_KEY = "cdServiceIdentifier";

  public static final long THIRTY_DAYS_IN_MILLIS = 30L * 24 * 60 * 60 * 1000;

  public static final long SEVEN_DAYS_IN_MILLIS = 7L * 24 * 60 * 60 * 1000;
}
