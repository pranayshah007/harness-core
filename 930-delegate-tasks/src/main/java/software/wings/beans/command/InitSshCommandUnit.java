/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.command;

import static io.harness.annotations.dev.HarnessModule._930_DELEGATE_TASKS;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.expression.ExpressionEvaluator.containsVariablePattern;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.utils.Utils.escapifyString;

import static com.google.common.collect.ImmutableMap.of;
import static freemarker.template.Configuration.VERSION_2_3_23;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.logging.CommandExecutionStatus;

import software.wings.beans.dto.Command;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.io.CharStreams;
import com.google.inject.Inject;
import freemarker.cache.StringTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.TemplateException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.mongodb.morphia.annotations.Transient;

/**
 * Created by peeyushaggarwal on 7/26/16.
 */
@Slf4j
@JsonTypeName("INIT")
@OwnedBy(CDP)
@TargetModule(_930_DELEGATE_TASKS)
public class InitSshCommandUnit extends SshCommandUnit {
  @Inject @Transient private transient CommandUnitHelper commandUnitHelper;
  /**
   * The constant INITIALIZE_UNIT.
   */
  public static final transient String INITIALIZE_UNIT = "Initialize";
  private static final Configuration cfg = new Configuration(VERSION_2_3_23);
  static {
    try {
      StringTemplateLoader stringLoader = new StringTemplateLoader();

      InputStream execLauncherInputStream =
          InitSshCommandUnit.class.getClassLoader().getResourceAsStream("commandtemplates/execlauncher.ftl");
      if (execLauncherInputStream == null) {
        throw new RuntimeException("execlauncher.ftl file is missing.");
      }

      stringLoader.putTemplate("execlauncher.ftl",
          convertToUnixStyleLineEndings(IOUtils.toString(execLauncherInputStream, StandardCharsets.UTF_8)));

      InputStream tailWrapperInputStream =
          InitSshCommandUnit.class.getClassLoader().getResourceAsStream("commandtemplates/tailwrapper.ftl");
      if (tailWrapperInputStream == null) {
        throw new RuntimeException("tailwrapper.ftl file is missing.");
      }

      stringLoader.putTemplate("tailwrapper.ftl",
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

  @JsonIgnore private String launcherScriptFileName;

  /**
   * Instantiates a new Init command unit.
   */
  public InitSshCommandUnit() {
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
    notNullCheck("Service Variables", context.getServiceVariables());
    for (Map.Entry<String, String> entry : context.getServiceVariables().entrySet()) {
      envVariables.put(entry.getKey(), escapifyString(entry.getValue()));
    }

    validateEnvVariables(envVariables, context);

    commandUnitHelper.addArtifactFileNameToEnv(envVariables, context);

    notNullCheck("Safe Display Service Variables", context.getSafeDisplayServiceVariables());
    for (Map.Entry<String, String> entry : context.getSafeDisplayServiceVariables().entrySet()) {
      safeDisplayEnvVariables.put(entry.getKey(), escapifyString(entry.getValue()));
    }

    launcherScriptFileName = "harnesslauncher" + activityId + ".sh";

    CommandExecutionStatus commandExecutionStatus = context.executeCommandString(preInitCommand);
    String launcherFile = null;
    try {
      launcherFile = getLauncherFile();
      commandExecutionStatus = commandExecutionStatus == CommandExecutionStatus.SUCCESS
          ? context.copyFiles(executionStagingDir, Collections.singletonList(launcherFile))
          : commandExecutionStatus;
    } catch (IOException | TemplateException e) {
      log.error("Error in InitCommandUnit", e);
    } finally {
      if (launcherFile != null) {
        File file = new File(launcherFile);
        if (!file.delete()) {
          log.warn("Unable to delete file {} from delegate", launcherFile);
        }
      }
    }

    List<String> commandUnitFiles = null;
    try {
      commandUnitFiles = getCommandUnitFiles();
      if (isNotEmpty(commandUnitFiles)) {
        commandExecutionStatus = commandExecutionStatus == CommandExecutionStatus.SUCCESS
            ? context.copyFiles(executionStagingDir, commandUnitFiles)
            : commandExecutionStatus;
      }
    } catch (IOException | TemplateException e) {
      commandExecutionStatus = CommandExecutionStatus.FAILURE;
      log.error("Error in InitCommandUnit", e);
    } finally {
      if (commandUnitFiles != null) {
        for (String commandUnitFile : commandUnitFiles) {
          File file = new File(commandUnitFile);
          if (!file.delete()) {
            log.warn("Unable to delete file {} from delegate", commandUnitFile);
          }
        }
      }
    }

    commandExecutionStatus = commandExecutionStatus == CommandExecutionStatus.SUCCESS
        ? context.executeCommandString("chmod 0700 " + executionStagingDir + "/*")
        : commandExecutionStatus;
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
    context.addEnvVariables(envVariables);
    return commandExecutionStatus;
  }

  private void validateEnvVariables(Map<String, String> envVariables, ShellCommandExecutionContext context) {
    if (!containsVariablePattern(context.getStagingPath())) {
      envVariables.put("WINGS_STAGING_PATH", context.getStagingPath());
    }
    if (!containsVariablePattern(context.getRuntimePath())) {
      envVariables.put("WINGS_RUNTIME_PATH", context.getRuntimePath());
    }
    if (!containsVariablePattern(context.getBackupPath())) {
      envVariables.put("WINGS_BACKUP_PATH", context.getBackupPath());
    }
    envVariables.put("WINGS_SCRIPT_DIR", executionStagingDir);
  }

  /**
   * Gets pre init command.
   *
   * @return the pre init command
   */
  public String getPreInitCommand() {
    return preInitCommand;
  }

  /**
   * Gets launcher file.
   *
   * @return the launcher file
   * @throws IOException       the io exception
   * @throws TemplateException the template exception
   */
  private String getLauncherFile() throws IOException, TemplateException {
    String launcherScript = new File(System.getProperty("java.io.tmpdir"), launcherScriptFileName).getAbsolutePath();
    try (OutputStreamWriter fileWriter =
             new OutputStreamWriter(new FileOutputStream(launcherScript), StandardCharsets.UTF_8)) {
      cfg.getTemplate("execlauncher.ftl")
          .process(of("envVariables", envVariables, "safeEnvVariables", safeDisplayEnvVariables), fileWriter);
    }
    return launcherScript;
  }

  /**
   * Gets command unit files.
   *
   * @return the command unit files
   * @throws IOException the io exception
   */
  private List<String> getCommandUnitFiles() throws IOException, TemplateException {
    return createScripts(command);
  }

  private List<String> createScripts(Command command) throws IOException, TemplateException {
    return createScripts(command, "");
  }

  private List<String> createScripts(Command command, String prefix) throws IOException, TemplateException {
    List<String> files = Lists.newArrayList();
    for (CommandUnit unit : command.getCommandUnits()) {
      if (unit instanceof Command) {
        files.addAll(createScripts((Command) unit, prefix + unit.getName()));
      } else {
        files.addAll(prepare(unit, activityId, executionStagingDir, launcherScriptFileName, prefix));
      }
    }
    return files;
  }

  private static List<String> prepare(CommandUnit commandUnit, String activityId, String executionStagingDir,
      String launcherScriptFileName, String prefix) throws IOException, TemplateException {
    if (commandUnit instanceof ExecCommandUnit) {
      ExecCommandUnit execCommandUnit = (ExecCommandUnit) commandUnit;

      String commandFileName = "harness" + DigestUtils.md5Hex(prefix + execCommandUnit.getName() + activityId);
      String commandFile = new File(System.getProperty("java.io.tmpdir"), commandFileName).getAbsolutePath();
      String commandDir =
          isNotBlank(execCommandUnit.getCommandPath()) ? "-w '" + execCommandUnit.getCommandPath().trim() + "'" : "";
      String preparedCommand = "";

      try (OutputStreamWriter fileWriter =
               new OutputStreamWriter(new FileOutputStream(commandFile), StandardCharsets.UTF_8)) {
        CharStreams.asWriter(fileWriter).append(execCommandUnit.getCommandString()).close();
        preparedCommand = executionStagingDir + "/" + launcherScriptFileName + " " + commandDir + " " + commandFileName;
      }

      List<String> returnValue = Lists.newArrayList(commandFile);

      if (isNotEmpty(execCommandUnit.getTailPatterns())) {
        String tailWrapperFileName =
            "harnesstailwrapper" + DigestUtils.md5Hex(prefix + execCommandUnit.getName() + activityId);
        String tailWrapperFile = new File(System.getProperty("java.io.tmpdir"), tailWrapperFileName).getAbsolutePath();
        try (OutputStreamWriter fileWriter =
                 new OutputStreamWriter(new FileOutputStream(tailWrapperFile), StandardCharsets.UTF_8)) {
          cfg.getTemplate("tailwrapper.ftl")
              .process(ImmutableMap.of("tailPatterns", execCommandUnit.getTailPatterns(), "executionId", activityId,
                           "executionStagingDir", executionStagingDir),
                  fileWriter);
        }
        returnValue.add(tailWrapperFile);
        returnValue.add(tailWrapperFile);
        preparedCommand = executionStagingDir + "/" + launcherScriptFileName + " " + commandDir + " "
            + tailWrapperFileName + " " + commandFileName;
      }
      execCommandUnit.setPreparedCommand(preparedCommand);
      return returnValue;
    } else {
      return Collections.emptyList();
    }
  }

  /**
   * Gets execution staging dir.
   *
   * @return the execution staging dir
   */
  public String getExecutionStagingDir() {
    return executionStagingDir;
  }

  /**
   * Sets command.
   *
   * @param command the command
   */
  public void setCommand(Command command) {
    this.command = command;
  }

  public Map<String, String> fetchEnvVariables() {
    return envVariables;
  }
}
