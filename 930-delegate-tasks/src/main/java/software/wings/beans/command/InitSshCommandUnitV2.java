/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.command;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.utils.Utils.escapifyString;

import static freemarker.template.Configuration.VERSION_2_3_23;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.logging.CommandExecutionStatus;

import software.wings.beans.dto.Command;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import freemarker.cache.StringTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.TemplateException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.mongodb.morphia.annotations.Transient;

@Slf4j
public class InitSshCommandUnitV2 extends SshCommandUnit {
  @Inject @Transient private transient CommandUnitHelper commandUnitHelper;
  /**
   * The constant INITIALIZE_UNIT.
   */
  private static final transient String INITIALIZE_UNIT = "Initialize";
  private static final Configuration cfg = new Configuration(VERSION_2_3_23);
  static {
    try {
      StringTemplateLoader stringLoader = new StringTemplateLoader();

      InputStream execLauncherInputStream =
          InitSshCommandUnitV2.class.getClassLoader().getResourceAsStream("commandtemplates/execlauncherv2.sh.ftl");
      if (execLauncherInputStream == null) {
        throw new RuntimeException("execlauncherv2.sh.ftl file is missing.");
      }

      stringLoader.putTemplate("execlauncherv2.sh.ftl",
          convertToUnixStyleLineEndings(IOUtils.toString(execLauncherInputStream, StandardCharsets.UTF_8)));

      InputStream tailWrapperInputStream =
          InitSshCommandUnitV2.class.getClassLoader().getResourceAsStream("commandtemplates/tailwrapperv2.sh.ftl");
      if (tailWrapperInputStream == null) {
        throw new RuntimeException("tailwrapperv2.sh.ftl file is missing.");
      }

      stringLoader.putTemplate("tailwrapperv2.sh.ftl",
          convertToUnixStyleLineEndings(IOUtils.toString(tailWrapperInputStream, StandardCharsets.UTF_8)));
      cfg.setTemplateLoader(stringLoader);
    } catch (IOException e) {
      throw new RuntimeException("Failed to load and parse commandtemplates ", e);
    }
  }

  @JsonIgnore @SchemaIgnore @Transient private Command command;

  @JsonIgnore @SchemaIgnore private String activityId;

  @JsonIgnore @Transient @SchemaIgnore private Map<String, String> envVariables = new HashMap<>();

  @JsonIgnore @Transient @SchemaIgnore private Map<String, String> safeDisplayEnvVariables = new HashMap<>();

  @JsonIgnore @Transient @SchemaIgnore private String preInitCommand;

  @JsonIgnore @Transient @SchemaIgnore private String executionStagingDir;

  public InitSshCommandUnitV2() {
    super(CommandUnitType.EXEC);
    setName(INITIALIZE_UNIT);
  }

  private static String convertToUnixStyleLineEndings(String input) {
    return input.replace("\r\n", "\n");
  }

  @Override
  protected CommandExecutionStatus executeInternal(ShellCommandExecutionContext context) {
    activityId = context.getActivityId();
    executionStagingDir = "/tmp/" + activityId;
    preInitCommand = "mkdir -p " + executionStagingDir;
    CommandExecutionStatus commandExecutionStatus = context.executeCommandString(preInitCommand);

    notNullCheck("Service Variables", context.getServiceVariables());
    for (Map.Entry<String, String> entry : context.getServiceVariables().entrySet()) {
      envVariables.put(entry.getKey(), escapifyString(entry.getValue()));
    }
    envVariables.put("WINGS_STAGING_PATH", context.getStagingPath());
    envVariables.put("WINGS_RUNTIME_PATH", context.getRuntimePath());
    envVariables.put("WINGS_BACKUP_PATH", context.getBackupPath());

    commandUnitHelper.addArtifactFileNameToEnv(envVariables, context);

    notNullCheck("Safe Display Service Variables", context.getSafeDisplayServiceVariables());
    for (Map.Entry<String, String> entry : context.getSafeDisplayServiceVariables().entrySet()) {
      safeDisplayEnvVariables.put(entry.getKey(), escapifyString(entry.getValue()));
    }
    StringBuffer envVariablesFromHost = new StringBuffer();
    commandExecutionStatus = commandExecutionStatus == CommandExecutionStatus.SUCCESS
        ? context.executeCommandString("printenv", envVariablesFromHost)
        : commandExecutionStatus;
    Properties properties = new Properties();
    try {
      properties.load(new StringReader(envVariablesFromHost.toString().replaceAll("\\\\", "\\\\\\\\")));
      context.addEnvVariables(
          properties.entrySet().stream().collect(toMap(o -> o.getKey().toString(), o -> o.getValue().toString())));
    } catch (IOException e) {
      log.error("Error in InitCommandUnit", e);
      commandExecutionStatus = CommandExecutionStatus.FAILURE;
    }
    try {
      createPreparedCommands(command);
    } catch (IOException | TemplateException e) {
      log.error("Failed in preparing commands ", e);
    }
    context.addEnvVariables(envVariables);
    return commandExecutionStatus;
  }

  private String getInitCommand(String scriptWorkingDirectory, boolean includeTailFunctions)
      throws IOException, TemplateException {
    try (StringWriter stringWriter = new StringWriter()) {
      Map<String, Object> templateParams = ImmutableMap.<String, Object>builder()
                                               .put("executionId", activityId)
                                               .put("executionStagingDir", executionStagingDir)
                                               .put("envVariables", envVariables)
                                               .put("safeEnvVariables", safeDisplayEnvVariables)
                                               .put("scriptWorkingDirectory", scriptWorkingDirectory)
                                               .put("includeTailFunctions", includeTailFunctions)
                                               .build();
      cfg.getTemplate("execlauncherv2.sh.ftl").process(templateParams, stringWriter);
      return stringWriter.toString();
    }
  }

  private void createPreparedCommands(Command command) throws IOException, TemplateException {
    for (CommandUnit unit : command.getCommandUnits()) {
      if (unit instanceof Command) {
        createPreparedCommands((Command) unit);
      } else {
        if (unit instanceof ExecCommandUnit) {
          ExecCommandUnit execCommandUnit = (ExecCommandUnit) unit;
          String commandDir =
              isNotBlank(execCommandUnit.getCommandPath()) ? "'" + execCommandUnit.getCommandPath().trim() + "'" : "";
          String commandString = execCommandUnit.getCommandString();
          boolean includeTailFunctions = isNotEmpty(execCommandUnit.getTailPatterns())
              || StringUtils.contains(commandString, "harness_utils_start_tail_log_verification")
              || StringUtils.contains(commandString, "harness_utils_wait_for_tail_log_verification");
          StringBuilder preparedCommand = new StringBuilder(getInitCommand(commandDir, includeTailFunctions));
          if (isEmpty(execCommandUnit.getTailPatterns())) {
            preparedCommand.append(commandString);
          } else {
            try (StringWriter stringWriter = new StringWriter()) {
              Map<String, Object> templateParams = ImmutableMap.<String, Object>builder()
                                                       .put("tailPatterns", execCommandUnit.getTailPatterns())
                                                       .put("executionId", activityId)
                                                       .put("executionStagingDir", executionStagingDir)
                                                       .put("commandString", commandString)
                                                       .build();
              cfg.getTemplate("tailwrapperv2.sh.ftl").process(templateParams, stringWriter);
              preparedCommand.append(' ').append(stringWriter.toString());
            }
          }

          execCommandUnit.setPreparedCommand(preparedCommand.toString());
        }
      }
    }
  }

  /**
   * Sets command.
   *
   * @param command the command
   */
  public void setCommand(Command command) {
    this.command = command;
  }
}
