/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.command;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.expression.Expression.ALLOW_SECRETS;

import static software.wings.beans.command.CommandUnitType.EXEC;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.expression.Expression;
import io.harness.expression.ExpressionEvaluator;
import io.harness.expression.ExpressionReflectionUtils.NestedAnnotationResolver;
import io.harness.logging.CommandExecutionStatus;
import io.harness.shell.ScriptType;

import software.wings.stencils.DefaultValue;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.github.reinert.jjschema.Attributes;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.base.MoreObjects;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Transient;

/**
 * Created by anubhaw on 5/25/16.
 */
@JsonTypeName("EXEC")
@OwnedBy(CDC)
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class ExecCommandUnit extends SshCommandUnit implements NestedAnnotationResolver {
  @Expression(ALLOW_SECRETS) @Attributes(title = "Working Directory") @NotEmpty private String commandPath;

  @NotEmpty @Getter @Setter @DefaultValue("BASH") @Attributes(title = "Script Type") private ScriptType scriptType;

  @Expression(ALLOW_SECRETS) @Attributes(title = "Command") @NotEmpty private String commandString;

  @Expression(ALLOW_SECRETS) @Attributes(title = "Files and Patterns") private List<TailFilePatternEntry> tailPatterns;

  @Transient @SchemaIgnore private String preparedCommand;

  /**
   * Instantiates a new exec command unit.
   */
  public ExecCommandUnit() {
    super(EXEC);
    if (scriptType == null) {
      scriptType = ScriptType.BASH;
    }
  }

  @Override
  @SchemaIgnore
  public boolean isArtifactNeeded() {
    Set<String> serviceArtifactVariableNames = new HashSet<>();
    updateServiceArtifactVariableNames(serviceArtifactVariableNames);
    return isNotEmpty(serviceArtifactVariableNames);
  }

  @SchemaIgnore
  @Override
  public void updateServiceArtifactVariableNames(Set<String> serviceArtifactVariableNames) {
    ExpressionEvaluator.updateServiceArtifactVariableNames(commandString, serviceArtifactVariableNames);
  }

  @Override
  public int hashCode() {
    return Objects.hash(commandPath, scriptType, commandString, tailPatterns);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    final ExecCommandUnit other = (ExecCommandUnit) obj;
    return Objects.equals(this.commandPath, other.commandPath) && this.scriptType == other.scriptType
        && Objects.equals(this.commandString, other.commandString)
        && Objects.equals(this.tailPatterns, other.tailPatterns);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("commandPath", commandPath)
        .add("scriptType", scriptType)
        .add("commandString", commandString)
        .add("tailPatterns", tailPatterns)
        .add("preparedCommand", preparedCommand)
        .toString();
  }

  @Override
  protected CommandExecutionStatus executeInternal(ShellCommandExecutionContext context) {
    return context.executeCommandString(preparedCommand);
  }

  /**
   * Getter for property 'commandPath'.
   *
   * @return Value for property 'commandPath'.
   */
  public String getCommandPath() {
    return commandPath;
  }

  /**
   * Setter for property 'commandPath'.
   *
   * @param commandPath Value to set for property 'commandPath'.
   */
  public void setCommandPath(String commandPath) {
    this.commandPath = commandPath;
  }

  /**
   * Gets command string.
   *
   * @return the command string
   */
  public String getCommandString() {
    return commandString;
  }

  /**
   * Sets command string.
   *
   * @param commandString the command string
   */
  public void setCommandString(String commandString) {
    this.commandString = commandString;
  }

  /**
   * Getter for property 'preparedCommand'.
   *
   * @return Value for property 'preparedCommand'.
   */
  public String getPreparedCommand() {
    return preparedCommand;
  }

  /**
   * Setter for property 'preparedCommand'.
   *
   * @param preparedCommand Value to set for property 'preparedCommand'.
   */
  public void setPreparedCommand(String preparedCommand) {
    this.preparedCommand = preparedCommand;
  }

  /**
   * Gets tail patterns.
   *
   * @return the tail patterns
   */
  public List<TailFilePatternEntry> getTailPatterns() {
    return tailPatterns;
  }

  /**
   * Sets tail patterns.
   *
   * @param tailPatterns the tail patterns
   */
  public void setTailPatterns(List<TailFilePatternEntry> tailPatterns) {
    this.tailPatterns = tailPatterns;
  }

  /**
   * The type Builder.
   */
  public static final class Builder {
    private String name;
    private String commandPath;
    private ScriptType scriptType;
    private String commandString;
    private List<TailFilePatternEntry> tailPatterns;

    private Builder() {}

    /**
     * An exec command unit builder.
     *
     * @return the builder
     */
    public static Builder anExecCommandUnit() {
      return new Builder();
    }

    /**
     * With name builder.
     *
     * @param name the name
     * @return the builder
     */
    public Builder withName(String name) {
      this.name = name;
      return this;
    }

    /**
     * With command path builder.
     *
     * @param commandPath the command path
     * @return the builder
     */
    public Builder withCommandPath(String commandPath) {
      this.commandPath = commandPath;
      return this;
    }

    /**
     * With command string builder.
     *
     * @param scriptType the command string
     * @return the builder
     */
    public Builder withScriptType(ScriptType scriptType) {
      this.scriptType = scriptType;
      return this;
    }

    /**
     * With command string builder.
     *
     * @param commandString the command string
     * @return the builder
     */
    public Builder withCommandString(String commandString) {
      this.commandString = commandString;
      return this;
    }

    /**
     * With tail patterns builder.
     *
     * @param tailPatterns the tail patterns
     * @return the builder
     */
    public Builder withTailPatterns(List<TailFilePatternEntry> tailPatterns) {
      this.tailPatterns = tailPatterns;
      return this;
    }

    /**
     * But builder.
     *
     * @return the builder
     */
    public Builder but() {
      return anExecCommandUnit()
          .withName(name)
          .withCommandPath(commandPath)
          .withScriptType(scriptType)
          .withCommandString(commandString)
          .withTailPatterns(tailPatterns);
    }

    /**
     * Build exec command unit.
     *
     * @return the exec command unit
     */
    public ExecCommandUnit build() {
      ExecCommandUnit execCommandUnit = new ExecCommandUnit();
      execCommandUnit.setName(name);
      execCommandUnit.setCommandPath(commandPath);
      execCommandUnit.setScriptType(scriptType);
      execCommandUnit.setCommandString(commandString);
      execCommandUnit.setTailPatterns(tailPatterns);
      return execCommandUnit;
    }
  }

  @Data
  @EqualsAndHashCode(callSuper = true)
  @JsonTypeName("EXEC")
  public static class Yaml extends ExecCommandUnitAbstractYaml {
    public Yaml() {
      super(CommandUnitType.EXEC.name());
    }

    @lombok.Builder
    public Yaml(String name, String deploymentType, String workingDirectory, String scriptType, String command,
        List<TailFilePatternEntry.Yaml> filePatternEntryList) {
      super(name, CommandUnitType.EXEC.name(), deploymentType, workingDirectory, scriptType, command,
          filePatternEntryList);
    }
  }
}
