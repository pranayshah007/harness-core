/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.connector;

import io.harness.connector.heartbeat.ConnectorValidationParamsProvider;
import io.harness.connector.mappers.ConnectorDTOToEntityMapper;
import io.harness.connector.mappers.ConnectorEntityToDTOMapper;
import io.harness.connector.task.ConnectorValidationHandler;
import io.harness.connector.validator.ConnectionValidator;

import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor
//@Builder -- do not add this
public class ConnectorRegistrar {
  /**
   * Category of the ConnectorDisconnectHandler.
   */
  ConnectorCategory connectorCategory;
  /**
   * The ConnectorDisconnectHandler validator which will be executed during ConnectorDisconnectHandler creation to check if ConnectorDisconnectHandler is able to connect
   * or not.
   */
  Class<? extends ConnectionValidator> connectorValidator;
  /**
   * The ConnectorDisconnectHandler Validation Params provider helps in perpetual task based validation of connectors every 10 minutes on
   * delegate agent to see if ConnectorDisconnectHandler is able to connect.
   * {@link ConnectorValidationHandler} needs to be implemented to execute heartbeat on delegate.
   */
  Class<? extends ConnectorValidationParamsProvider> connectorValidationParams;
  /**
   * Rest model to db model mapper.
   */
  Class<? extends ConnectorDTOToEntityMapper<?, ?>> connectorDTOToEntityMapper;
  /**
   * Db model to rest model mapper.
   */
  Class<? extends ConnectorEntityToDTOMapper<?, ?>> connectorEntityToDTOMapper;
  /**
   * The ConnectorDisconnectHandler validation handler which will be executed during validation of ConnectorDisconnectHandler to see if ConnectorDisconnectHandler is able
   * to connect or not.
   */
  Class<? extends ConnectorValidationHandler> connectorValidationHandler;
}
