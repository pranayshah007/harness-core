/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ConnectorConstants {
  public static final String CONNECTOR_DECORATOR_SERVICE = "connectorDecoratorService";
  public static final String CONNECTIVITY_STATUS = "connectivityStatus";
  public static final String CONNECTOR_IDENTIFIER = "connectorIdentifier";
  public static final String CONNECTOR_TYPES = "type";
  public static final String INHERIT_FROM_DELEGATE_TYPE_ERROR_MSG =
      "Delegate Selector cannot be null for inherit from delegate credential type";
  public static final String CONNECTOR = "Connector Details.";
  public static final String CREATED_AT = "This is the time at which the Connector was created.";
  public static final String LAST_MODIFIED_AT = "This is the time at which the Connector was last modified.";
  public static final String CONNECTOR_ACTIVITY_DETAILS =
      "This contains details of any kind of activities corresponding to the Connector.";
  public static final String STATUS_DETAILS = "Details of the connectivity status of the Connector.";
  public static final String ACTIVITY_TIME = "This specifies the time of the most recent activity on the Connector.";
  public static final String HARNESS_MANAGED =
      "This indicates if this Connector is managed by Harness or not. If True, Harness can manage and modify this Connector.";
  public static final String CONNECTOR_NAME_LIST =
      "This is the list of the Connector names on which the filter will be applied.";
  public static final String CONNECTOR_IDENTIFIER_LIST =
      "This is the list of the Connector identifiers on which the filter will be applied.";
  public static final String CONNECTOR_NAME = "Name of the Connector.";
  public static final String FILTER_DESCRIPTION = "Description of filter created.";
  public static final String CONNECTOR_TYPE_LIST =
      "This is the list of the Connector types on which the filter will be applied.";
  public static final String CONNECTOR_STATUS_LIST =
      "This is the list of the Connector status on which the filter will be applied.";
  public static final String CONNECTOR_CATEGORY_LIST =
      "This is the list of the Connector category on which the filter will be applied.";
  public static final String INHERIT_FROM_DELEGATE =
      "Boolean value to indicate if the Connector is using credentials from the Delegate to connect.";

  public static final String CONNECTOR_CATEGORY = "Category of this Connector.";
  public static final String CONNECTOR_TYPE_LIST_BY_CATEGORY =
      "List of Connector types corresponding to a specific category.";
  public static final String CONNECTOR_CATALOGUE_LIST =
      "List of Connector category and Connector types corresponding to a specific category.";
  public static final String STATUS = "Connectivity status of a Connector.";
  public static final String ERRORS = "List of errors and their details.";
  public static final String ERROR_SUMMARY = "Summary of errors.";
  public static final String TESTED_AT = "Time at which Test Connection was completed ";
  public static final String DELEGATE_ID = "ID of Delegate on which Test Connection is executed.";
  public static final String CONNECTOR_IDENTIFIER_MSG = "Identifier of the Connector.";
  public static final String LAST_CONNECTED_AT =
      "This is the last time at which the Connector was successfully connected.";
  public static final String CONNECTOR_TYPE = "Type of the Connector.";
  public static final String CONNECTOR_TYPE_STATS = "Count of Connectors grouped by type.";
  public static final String CONNECTOR_STATUS_STATS = "Count of Connectors grouped by status.";
  public static final String CONNECTIVITY_MODE = "Connector connectivity mode on which the filter is applied";
}
