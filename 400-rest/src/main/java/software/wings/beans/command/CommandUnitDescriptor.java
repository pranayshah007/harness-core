/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.command;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static org.joor.Reflect.on;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.WingsException;
import io.harness.serializer.JsonUtils;

import software.wings.stencils.OverridingStencil;
import software.wings.stencils.StencilCategory;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Created by peeyushaggarwal on 6/27/16.
 */
@OwnedBy(CDP)
public class CommandUnitDescriptor implements CommandUnitStencil {
  // Store CommandUnitDescriptors for all CommandUnitTypes as singleton in map.
  private static final Map<CommandUnitType, CommandUnitDescriptor> commandUnitDescriptorsMap;
  static {
    Map<CommandUnitType, CommandUnitDescriptor> map = new HashMap<>();
    map.put(CommandUnitType.EXEC,
        new CommandUnitDescriptor(
            ExecCommandUnit.class, CommandUnitType.EXEC, StencilCategory.SCRIPTS, DEFAULT_DISPLAY_ORDER));
    map.put(CommandUnitType.SCP,
        new CommandUnitDescriptor(
            ScpCommandUnit.class, CommandUnitType.SCP, StencilCategory.COPY, DEFAULT_DISPLAY_ORDER));
    map.put(CommandUnitType.COPY_CONFIGS,
        new CommandUnitDescriptor(
            CopyConfigCommandUnit.class, CommandUnitType.COPY_CONFIGS, StencilCategory.COPY, DEFAULT_DISPLAY_ORDER));
    map.put(CommandUnitType.COMMAND,
        new CommandUnitDescriptor(
            Command.class, CommandUnitType.COMMAND, StencilCategory.COMMANDS, DEFAULT_DISPLAY_ORDER));
    map.put(CommandUnitType.SETUP_ENV,
        new CommandUnitDescriptor(
            SetupEnvCommandUnit.class, CommandUnitType.SETUP_ENV, StencilCategory.SCRIPTS, DEFAULT_DISPLAY_ORDER));
    map.put(CommandUnitType.DOCKER_START,
        new CommandUnitDescriptor(DockerStartCommandUnit.class, CommandUnitType.DOCKER_START, StencilCategory.SCRIPTS,
            DEFAULT_DISPLAY_ORDER));
    map.put(CommandUnitType.DOCKER_STOP,
        new CommandUnitDescriptor(
            DockerStopCommandUnit.class, CommandUnitType.DOCKER_STOP, StencilCategory.SCRIPTS, DEFAULT_DISPLAY_ORDER));
    map.put(CommandUnitType.PROCESS_CHECK_RUNNING,
        new CommandUnitDescriptor(ProcessCheckRunningCommandUnit.class, CommandUnitType.PROCESS_CHECK_RUNNING,
            StencilCategory.VERIFICATIONS, DEFAULT_DISPLAY_ORDER));
    map.put(CommandUnitType.PROCESS_CHECK_STOPPED,
        new CommandUnitDescriptor(ProcessCheckStoppedCommandUnit.class, CommandUnitType.PROCESS_CHECK_STOPPED,
            StencilCategory.VERIFICATIONS, DEFAULT_DISPLAY_ORDER));
    map.put(CommandUnitType.PORT_CHECK_CLEARED,
        new CommandUnitDescriptor(PortCheckClearedCommandUnit.class, CommandUnitType.PORT_CHECK_CLEARED,
            StencilCategory.VERIFICATIONS, DEFAULT_DISPLAY_ORDER));
    map.put(CommandUnitType.PORT_CHECK_LISTENING,
        new CommandUnitDescriptor(PortCheckListeningCommandUnit.class, CommandUnitType.PORT_CHECK_LISTENING,
            StencilCategory.VERIFICATIONS, DEFAULT_DISPLAY_ORDER));
    map.put(CommandUnitType.CODE_DEPLOY,
        new CommandUnitDescriptor(
            CodeDeployCommandUnit.class, CommandUnitType.CODE_DEPLOY, StencilCategory.COMMANDS, DEFAULT_DISPLAY_ORDER));
    map.put(CommandUnitType.AWS_LAMBDA,
        new CommandUnitDescriptor(
            AwsLambdaCommandUnit.class, CommandUnitType.AWS_LAMBDA, StencilCategory.COMMANDS, DEFAULT_DISPLAY_ORDER));
    map.put(CommandUnitType.AWS_AMI,
        new CommandUnitDescriptor(
            AmiCommandUnit.class, CommandUnitType.AWS_AMI, StencilCategory.COMMANDS, DEFAULT_DISPLAY_ORDER));
    map.put(CommandUnitType.ECS_SETUP,
        new CommandUnitDescriptor(
            EcsSetupCommandUnit.class, CommandUnitType.ECS_SETUP, StencilCategory.CONTAINERS, DEFAULT_DISPLAY_ORDER));
    map.put(CommandUnitType.ECS_SETUP_DAEMON_SCHEDULING_TYPE,
        new CommandUnitDescriptor(EcsSetupCommandUnit.class, CommandUnitType.ECS_SETUP_DAEMON_SCHEDULING_TYPE,
            StencilCategory.CONTAINERS, DEFAULT_DISPLAY_ORDER));
    map.put(CommandUnitType.KUBERNETES_SETUP,
        new CommandUnitDescriptor(KubernetesSetupCommandUnit.class, CommandUnitType.KUBERNETES_SETUP,
            StencilCategory.CONTAINERS, DEFAULT_DISPLAY_ORDER));
    map.put(CommandUnitType.RESIZE,
        new CommandUnitDescriptor(
            ResizeCommandUnit.class, CommandUnitType.RESIZE, StencilCategory.CONTAINERS, DEFAULT_DISPLAY_ORDER));
    map.put(CommandUnitType.RESIZE_KUBERNETES,
        new CommandUnitDescriptor(KubernetesResizeCommandUnit.class, CommandUnitType.RESIZE_KUBERNETES,
            StencilCategory.CONTAINERS, DEFAULT_DISPLAY_ORDER));
    map.put(CommandUnitType.DOWNLOAD_ARTIFACT,
        new CommandUnitDescriptor(DownloadArtifactCommandUnit.class, CommandUnitType.DOWNLOAD_ARTIFACT,
            StencilCategory.SCRIPTS, DEFAULT_DISPLAY_ORDER));
    map.put(CommandUnitType.K8S_DUMMY,
        new CommandUnitDescriptor(
            K8sDummyCommandUnit.class, CommandUnitType.K8S_DUMMY, StencilCategory.CONTAINERS, DEFAULT_DISPLAY_ORDER));
    map.put(CommandUnitType.RANCHER_DUMMY,
        new CommandUnitDescriptor(RancherDummyCommandUnit.class, CommandUnitType.RANCHER_DUMMY,
            StencilCategory.CONTAINERS, DEFAULT_DISPLAY_ORDER));
    map.put(CommandUnitType.SPOTINST_DUMMY,
        new CommandUnitDescriptor(SpotinstDummyCommandUnit.class, CommandUnitType.SPOTINST_DUMMY,
            StencilCategory.SPOTINST, DEFAULT_DISPLAY_ORDER));
    map.put(CommandUnitType.HELM_DUMMY,
        new CommandUnitDescriptor(
            HelmDummyCommandUnit.class, CommandUnitType.HELM_DUMMY, StencilCategory.CONTAINERS, DEFAULT_DISPLAY_ORDER));
    map.put(CommandUnitType.PCF_DUMMY,
        new CommandUnitDescriptor(
            PcfDummyCommandUnit.class, CommandUnitType.PCF_DUMMY, StencilCategory.CONTAINERS, DEFAULT_DISPLAY_ORDER));
    map.put(CommandUnitType.AZURE_VMSS_DUMMY,
        new CommandUnitDescriptor(AzureVMSSDummyCommandUnit.class, CommandUnitType.AZURE_VMSS_DUMMY,
            StencilCategory.AZURE_VMSS, DEFAULT_DISPLAY_ORDER));
    map.put(CommandUnitType.AZURE_WEBAPP,
        new CommandUnitDescriptor(AzureWebAppCommandUnit.class, CommandUnitType.AZURE_WEBAPP,
            StencilCategory.AZURE_WEBAPP, DEFAULT_DISPLAY_ORDER));
    map.put(CommandUnitType.FETCH_INSTANCES_DUMMY,
        new CommandUnitDescriptor(FetchInstancesCommandUnit.class, CommandUnitType.FETCH_INSTANCES_DUMMY,
            StencilCategory.COMMANDS, DEFAULT_DISPLAY_ORDER));
    map.put(CommandUnitType.AZURE_ARM,
        new CommandUnitDescriptor(
            AzureARMCommandUnit.class, CommandUnitType.AZURE_ARM, StencilCategory.AZURE_ARM, DEFAULT_DISPLAY_ORDER));
    map.put(CommandUnitType.TERRAGRUNT_PROVISION,
        new CommandUnitDescriptor(TerragruntDummyCommandUnit.class, CommandUnitType.TERRAGRUNT_PROVISION,
            StencilCategory.TERRAGRUNT_PROVISION, DEFAULT_DISPLAY_ORDER));

    commandUnitDescriptorsMap = Collections.unmodifiableMap(map);
  }

  public static CommandUnitDescriptor forType(CommandUnitType type) {
    if (!commandUnitDescriptorsMap.containsKey(type)) {
      throw new IllegalArgumentException(String.format("Unsupported CommandType '%s', no desciptor available", type));
    }

    return commandUnitDescriptorsMap.get(type);
  }

  private static final String stencilsPath = "/templates/commandstencils/";
  private static final String uiSchemaSuffix = "-UISchema.json";

  private Object uiSchema;
  private JsonNode jsonSchema;

  @JsonIgnore private Class<? extends CommandUnit> commandUnitClass;
  @JsonIgnore private CommandUnitType commandUnitType;

  private StencilCategory stencilCategory;
  private Integer displayOrder;

  /**
   * Instantiates a new command unit descriptor.
   *
   * @param commandUnitClass the command unit class
   * @param commandUnitType the command unit type
   */
  private CommandUnitDescriptor(Class<? extends CommandUnit> commandUnitClass, CommandUnitType commandUnitType,
      StencilCategory stencilCategory, Integer displayOrder) {
    this.commandUnitClass = commandUnitClass;
    this.commandUnitType = commandUnitType;
    this.stencilCategory = stencilCategory;
    this.displayOrder = displayOrder;
    try {
      uiSchema = readResource(stencilsPath + commandUnitType.name() + uiSchemaSuffix);
    } catch (Exception e) {
      uiSchema = new HashMap<String, String>();
    }
  }

  @Override
  public String getType() {
    return commandUnitType.name();
  }

  @Override
  public String getName() {
    return commandUnitType.getName();
  }

  @Override
  public Object getUiSchema() {
    return uiSchema;
  }

  @Override
  public JsonNode getJsonSchema() {
    return Optional.ofNullable(jsonSchema).orElse(jsonSchema = JsonUtils.jsonSchema(commandUnitClass)).deepCopy();
  }

  /**
   * Gets command unit class.
   *
   * @return the command unit class
   */
  @Override
  public Class<? extends CommandUnit> getTypeClass() {
    return commandUnitClass;
  }

  @Override
  public OverridingStencil getOverridingStencil() {
    return new OverridingCommandUnitDescriptor(this);
  }

  @Override
  public CommandUnit newInstance(String id) {
    return on(commandUnitClass).create().get();
  }

  private Object readResource(String file) {
    try {
      URL url = this.getClass().getResource(file);
      String json = Resources.toString(url, Charsets.UTF_8);
      return JsonUtils.asObject(json, HashMap.class);
    } catch (Exception exception) {
      throw new WingsException("Error in initializing CommandUnitType-" + file, exception);
    }
  }

  @Override
  public StencilCategory getStencilCategory() {
    return stencilCategory;
  }

  @Override
  public Integer getDisplayOrder() {
    return displayOrder;
  }

  @Override
  public boolean matches(Object context) {
    return true;
  }
}
