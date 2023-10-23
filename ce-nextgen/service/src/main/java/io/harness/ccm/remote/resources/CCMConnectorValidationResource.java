/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.remote.resources;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.commons.entities.CCMConnectorDetails;
import io.harness.ccm.connectors.AbstractCEConnectorValidator;
import io.harness.ccm.connectors.CEConnectorValidatorFactory;
import io.harness.ccm.service.intf.CCMConnectorDetailsService;
import io.harness.ccm.utils.LogAccountIdentifier;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.ConnectorValidationResult;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.security.annotations.NextGenManagerAuth;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Api("testconnection")
@Path("/testconnection")
@Produces("application/json")
@NextGenManagerAuth
@Slf4j
@Service
@OwnedBy(CE)
public class CCMConnectorValidationResource {
  @Inject CEConnectorValidatorFactory ceConnectorValidatorFactory;
  @Inject CCMConnectorDetailsService connectorDetailsService;

  @POST
  @Timed
  @ExceptionMetered
  @LogAccountIdentifier
  @ApiOperation(value = "Validate ConnectorDisconnectHandler", nickname = "validate ConnectorDisconnectHandler")
  public ResponseDTO<ConnectorValidationResult> testConnection(
      @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId, ConnectorResponseDTO connectorResponseDTO) {
    // Implement validation methods for each ConnectorDisconnectHandler type
    log.info("Connector dto from ngmanager in request {}", connectorResponseDTO.toString());
    ConnectorType connectorType = connectorResponseDTO.getConnector().getConnectorType();
    AbstractCEConnectorValidator ceConnectorValidator = ceConnectorValidatorFactory.getValidator(connectorType);
    if (ceConnectorValidator != null) {
      return ResponseDTO.newResponse(ceConnectorValidator.validate(connectorResponseDTO, accountId));
    } else {
      return ResponseDTO.newResponse();
    }
  }

  @GET
  @Path("firstConnector")
  @Timed
  @ExceptionMetered
  @LogAccountIdentifier
  @ApiOperation(value = "Get ConnectorDisconnectHandler details", nickname = "get ConnectorDisconnectHandler details")
  public ResponseDTO<CCMConnectorDetails> getConnectorDetails(
      @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId) {
    CCMConnectorDetails firstConnectorDetails = connectorDetailsService.getFirstConnectorDetails(accountId);
    if (firstConnectorDetails != null) {
      return ResponseDTO.newResponse(firstConnectorDetails);
    } else {
      return ResponseDTO.newResponse();
    }
  }
}
