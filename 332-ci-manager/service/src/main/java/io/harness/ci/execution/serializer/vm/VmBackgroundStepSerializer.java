/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.serializer.vm;

import static io.harness.beans.serializer.RunTimeInputHandler.resolveIntegerParameter;
import static io.harness.beans.serializer.RunTimeInputHandler.resolveMapParameterV2;
import static io.harness.ci.commonconstants.CIExecutionConstants.NULL_STR;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.beans.serializer.RunTimeInputHandler;
import io.harness.beans.steps.CIRegistry;
import io.harness.beans.steps.stepinfo.BackgroundStepInfo;
import io.harness.beans.yaml.extended.reports.JUnitTestReport;
import io.harness.beans.yaml.extended.reports.UnitTestReportType;
import io.harness.ci.buildstate.ConnectorUtils;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.ci.serializer.SerializerUtils;
import io.harness.ci.utils.CIStepInfoUtils;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.vm.steps.VmBackgroundStep;
import io.harness.delegate.beans.ci.vm.steps.VmBackgroundStep.VmBackgroundStepBuilder;
import io.harness.delegate.beans.ci.vm.steps.VmJunitTestReport;
import io.harness.ng.core.NGAccess;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.yaml.ParameterField;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

@Singleton
public class VmBackgroundStepSerializer {
  @Inject ConnectorUtils connectorUtils;
  @Inject CIStepInfoUtils ciStepInfoUtils;
  @Inject CIFeatureFlagService featureFlagService;

  public VmBackgroundStep serialize(BackgroundStepInfo backgroundStepInfo, Ambiance ambiance, String identifier,
      List<CIRegistry> registries, String delegateId) {
    String command = RunTimeInputHandler.resolveStringParameter(
        "Command", "Background", identifier, backgroundStepInfo.getCommand(), false);
    String image = RunTimeInputHandler.resolveStringParameter(
        "Image", "Background", identifier, backgroundStepInfo.getImage(), false);
    if (isNotEmpty(image) && image.equals(NULL_STR)) {
      image = "";
    }
    String connectorIdentifier;
    if (isNotEmpty(registries)) {
      connectorIdentifier = ciStepInfoUtils.resolveConnectorFromRegistries(registries, image).orElse(null);
    } else {
      connectorIdentifier = RunTimeInputHandler.resolveStringParameter(
          "connectorRef", "Background", identifier, backgroundStepInfo.getConnectorRef(), false);
    }
    Map<String, String> portBindings;
    if (ParameterField.isNotNull(backgroundStepInfo.getPorts())) {
      portBindings = SerializerUtils.getPortBindingMap(backgroundStepInfo.getPorts().getValue());
    } else {
      portBindings = RunTimeInputHandler.resolveMapParameter("portBindings", "Background",
          backgroundStepInfo.getIdentifier(), backgroundStepInfo.getPortBindings(), false);
    }
    List<String> entrypoint = RunTimeInputHandler.resolveListParameter(
        "entrypoint", "Background", identifier, backgroundStepInfo.getEntrypoint(), false);
    String imagePullPolicy = RunTimeInputHandler.resolveImagePullPolicy(backgroundStepInfo.getImagePullPolicy());
    boolean privileged = RunTimeInputHandler.resolveBooleanParameter(backgroundStepInfo.getPrivileged(), false);

    if (!isEmpty(command) && isEmpty(entrypoint)) {
      entrypoint = SerializerUtils.getEntrypoint(backgroundStepInfo.getShell());
    } else {
      command = null;
    }

    Map<String, String> envVars =
        resolveMapParameterV2("envVariables", "Background", identifier, backgroundStepInfo.getEnvVariables(), false);
    if (StringUtils.isNotEmpty(delegateId)) {
      if (isEmpty(envVars)) {
        envVars = new HashMap<>();
      }
      envVars.put("HARNESS_DELEGATE_ID", delegateId);
    }
    envVars = CIStepInfoUtils.injectAndResolveLoopingVariables(
        ambiance, AmbianceUtils.getAccountId(ambiance), featureFlagService, envVars);

    if (!isEmpty(command)) {
      String earlyExitCommand = SerializerUtils.getEarlyExitCommand(backgroundStepInfo.getShell());
      command = earlyExitCommand + command;
    }

    ConnectorDetails connectorDetails = null;
    if (!StringUtils.isEmpty(image) && !StringUtils.isEmpty(connectorIdentifier)) {
      NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
      connectorDetails = connectorUtils.getConnectorDetails(ngAccess, connectorIdentifier);
    }

    VmBackgroundStepBuilder backgroundStepBuilder = VmBackgroundStep.builder()
                                                        .identifier(backgroundStepInfo.getIdentifier())
                                                        .name(backgroundStepInfo.getName())
                                                        .image(image)
                                                        .imageConnector(connectorDetails)
                                                        .envVariables(envVars)
                                                        .command(command)
                                                        .entrypoint(entrypoint)
                                                        .portBindings(portBindings)
                                                        .pullPolicy(imagePullPolicy)
                                                        .privileged(privileged);
    if (backgroundStepInfo.getReports().getValue() != null) {
      if (backgroundStepInfo.getReports().getValue().getType() == UnitTestReportType.JUNIT) {
        JUnitTestReport junitTestReport = (JUnitTestReport) backgroundStepInfo.getReports().getValue().getSpec();
        List<String> resolvedReport =
            RunTimeInputHandler.resolveListParameter("paths", "run", identifier, junitTestReport.getPaths(), false);
        backgroundStepBuilder.unitTestReport(VmJunitTestReport.builder().paths(resolvedReport).build());
      }
    }

    Integer runAsUser = resolveIntegerParameter(backgroundStepInfo.getRunAsUser(), null);
    if (runAsUser != null) {
      backgroundStepBuilder.runAsUser(runAsUser.toString());
    }

    return backgroundStepBuilder.build();
  }
}
