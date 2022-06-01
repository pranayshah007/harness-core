/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.connector;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.SettingAttribute.SettingCategory.CONNECTOR;
import static software.wings.beans.SettingAttribute.SettingCategory.HELM_REPO;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_CONNECTORS;

import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.utils.ConstraintViolationHandlerUtils;

import software.wings.beans.SettingAttribute;
import software.wings.graphql.datafetcher.BaseMutatorDataFetcher;
import software.wings.graphql.datafetcher.MutationContext;
import software.wings.graphql.datafetcher.connector.types.Connector;
import software.wings.graphql.datafetcher.connector.types.ConnectorFactory;
import software.wings.graphql.datafetcher.secrets.UsageScopeController;
import software.wings.graphql.schema.mutation.connector.input.QLUpdateConnectorInput;
import software.wings.graphql.schema.mutation.connector.payload.QLUpdateConnectorPayload;
import software.wings.graphql.schema.mutation.connector.payload.QLUpdateConnectorPayload.QLUpdateConnectorPayloadBuilder;
import software.wings.graphql.schema.type.QLConnectorType;
import software.wings.graphql.schema.type.connector.QLConnectorBuilder;
import software.wings.security.annotations.AuthRule;
import software.wings.service.impl.SettingServiceHelper;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;

import com.google.inject.Inject;
import javax.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class UpdateConnectorDataFetcher
    extends BaseMutatorDataFetcher<QLUpdateConnectorInput, QLUpdateConnectorPayload> {
  @Inject private SettingsService settingsService;
  @Inject private SettingServiceHelper settingServiceHelper;
  @Inject private ConnectorsController connectorsController;
  @Inject private SecretManager secretManager;
  @Inject private UsageScopeController usageScopeController;
  @Inject private FeatureFlagService featureFlagService;

  public UpdateConnectorDataFetcher() {
    super(QLUpdateConnectorInput.class, QLUpdateConnectorPayload.class);
  }

  @Override
  @AuthRule(permissionType = MANAGE_CONNECTORS)
  protected QLUpdateConnectorPayload mutateAndFetch(QLUpdateConnectorInput input, MutationContext mutationContext) {
    String connectorId = input.getConnectorId();
    String accountId = mutationContext.getAccountId();

    if (isBlank(connectorId)) {
      throw new InvalidRequestException("Connector ID is not provided");
    }

    if (input.getConnectorType() == null) {
      throw new InvalidRequestException("Invalid connector type provided");
    }

    SettingAttribute settingAttribute = settingsService.getByAccount(accountId, connectorId);
    if (settingAttribute == null || settingAttribute.getValue() == null
        || (CONNECTOR != settingAttribute.getCategory() && HELM_REPO != settingAttribute.getCategory())) {
      throw new InvalidRequestException(String.format("No connector exists with the connectorId %s", connectorId));
    }

    checkIfConnectorTypeMatchesTheInputType(settingAttribute.getValue().getType(), input.getConnectorType());
    QLUpdateConnectorPayloadBuilder builder =
        QLUpdateConnectorPayload.builder().clientMutationId(input.getClientMutationId());

    Connector connector = ConnectorFactory.getConnector(input.getConnectorType(), connectorsController, secretManager,
        settingsService, usageScopeController, featureFlagService);
    connector.checkInputExists(input);
    connector.checkSecrets(input, settingAttribute);
    connector.updateSettingAttribute(settingAttribute, input);

    try {
      settingAttribute =
          settingsService.updateWithSettingFields(settingAttribute, settingAttribute.getUuid(), GLOBAL_APP_ID);
    } catch (ConstraintViolationException exception) {
      String errorMessages = String.join(", ", ConstraintViolationHandlerUtils.getErrorMessages(exception));
      throw new InvalidRequestException(errorMessages, exception);
    }

    settingServiceHelper.updateSettingAttributeBeforeResponse(settingAttribute, false);

    QLConnectorBuilder qlConnectorBuilder = connectorsController.getConnectorBuilder(settingAttribute);
    return builder.connector(connectorsController.populateConnector(settingAttribute, qlConnectorBuilder).build())
        .build();
  }

  private void checkIfConnectorTypeMatchesTheInputType(String settingVariableType, QLConnectorType connectorType) {
    if (!settingVariableType.equals(connectorType.toString())) {
      throw new InvalidRequestException(
          String.format("The existing connector is of type %s and the update operation inputs a connector of type %s",
              settingVariableType, connectorType));
    }
  }
}
