/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.servicenow.create;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.servicenow.ServiceNowActionNG.CREATE_TICKET;
import static io.harness.servicenow.ServiceNowActionNG.CREATE_TICKET_USING_STANDARD_TEMPLATE;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.servicenow.ServiceNowActionNG;
import io.harness.steps.servicenow.beans.ServiceNowCreateType;

import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(CDC)
@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@TypeAlias("serviceNowCreateSpecParameters")
@RecasterAlias("io.harness.steps.servicenow.create.ServiceNowCreateSpecParameters")
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_APPROVALS})
public class ServiceNowCreateSpecParameters implements SpecParameters {
  @NotNull ParameterField<String> connectorRef;
  @NotNull ParameterField<String> ticketType;

  Map<String, ParameterField<String>> fields;

  ParameterField<List<TaskSelectorYaml>> delegateSelectors;

  // template fields
  ParameterField<String> templateName;
  ParameterField<Boolean> useServiceNowTemplate;

  ServiceNowCreateType createType;

  public static ServiceNowActionNG getAction(ServiceNowCreateSpecParameters specParameters) {
    if (specParameters.getCreateType() != null && ServiceNowCreateType.STANDARD == specParameters.getCreateType()) {
      return CREATE_TICKET_USING_STANDARD_TEMPLATE;
    }

    return CREATE_TICKET;
  }

  public static boolean getUseServiceNowTemplate(ServiceNowCreateSpecParameters specParameters) {
    if (!ParameterField.isBlank(specParameters.getUseServiceNowTemplate())) {
      return specParameters.getUseServiceNowTemplate().getValue();
    }

    if (specParameters.getCreateType() != null) {
      if (ServiceNowCreateType.STANDARD == specParameters.getCreateType()
          || ServiceNowCreateType.FORM == specParameters.getCreateType()) {
        return true;
      }

      if (ServiceNowCreateType.NORMAL == specParameters.getCreateType()) {
        return false;
      }
    }

    return false;
  }
}
