/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.connector;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.utils.ConstraintViolationHandlerUtils;

import software.wings.beans.CGConstants;
import software.wings.beans.SettingAttribute;
import software.wings.graphql.datafetcher.BaseMutatorDataFetcher;
import software.wings.graphql.datafetcher.MutationContext;
import software.wings.graphql.datafetcher.connector.types.Connector;
import software.wings.graphql.datafetcher.connector.types.ConnectorFactory;
import software.wings.graphql.datafetcher.secrets.UsageScopeController;
import software.wings.graphql.schema.mutation.connector.input.QLConnectorInput;
import software.wings.graphql.schema.mutation.connector.payload.QLCreateConnectorPayload;
import software.wings.graphql.schema.mutation.connector.payload.QLCreateConnectorPayload.QLCreateConnectorPayloadBuilder;
import software.wings.graphql.schema.type.connector.QLConnectorBuilder;
import software.wings.security.PermissionAttribute;
import software.wings.security.annotations.AuthRule;
import software.wings.service.impl.SettingServiceHelper;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;

import com.google.inject.Inject;
import javax.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class CreateConnectorDataFetcher extends BaseMutatorDataFetcher<QLConnectorInput, QLCreateConnectorPayload> {
  @Inject private SettingsService settingsService;
  @Inject private SettingServiceHelper settingServiceHelper;
  @Inject private ConnectorsController connectorsController;
  @Inject private SecretManager secretManager;
  @Inject private UsageScopeController usageScopeController;
  @Inject private FeatureFlagService featureFlagService;

  public CreateConnectorDataFetcher() {
    super(QLConnectorInput.class, QLCreateConnectorPayload.class);
  }

  @Override
  @AuthRule(permissionType = PermissionAttribute.PermissionType.MANAGE_CONNECTORS)
  protected QLCreateConnectorPayload mutateAndFetch(QLConnectorInput input, MutationContext mutationContext) {
    String accountId = mutationContext.getAccountId();

    QLCreateConnectorPayloadBuilder builder =
        QLCreateConnectorPayload.builder().clientMutationId(input.getClientMutationId());

    if (input.getConnectorType() == null) {
      throw new InvalidRequestException("Invalid connector type provided");
    }

    Connector connector = ConnectorFactory.getConnector(input.getConnectorType(), connectorsController, secretManager,
        settingsService, usageScopeController, featureFlagService);
    connector.checkInputExists(input);
    connector.checkSecrets(input, accountId);
    SettingAttribute settingAttribute = connector.getSettingAttribute(input, accountId);

    try {
      settingAttribute = settingsService.saveWithPruning(settingAttribute, CGConstants.GLOBAL_APP_ID, accountId);
    } catch (ConstraintViolationException exception) {
      String errorMessages = String.join(", ", ConstraintViolationHandlerUtils.getErrorMessages(exception));
      throw new InvalidRequestException(errorMessages, exception);
    }

    settingServiceHelper.updateSettingAttributeBeforeResponse(settingAttribute, false);

    QLConnectorBuilder qlConnectorBuilder = connectorsController.getConnectorBuilder(settingAttribute);
    return builder.connector(connectorsController.populateConnector(settingAttribute, qlConnectorBuilder).build())
        .build();
  }
}
