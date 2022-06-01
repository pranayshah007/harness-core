/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.command;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.annotations.dev.OwnedBy;
import io.harness.expression.Expression;
import io.harness.expression.ExpressionReflectionUtils.NestedAnnotationResolver;
import io.harness.yaml.BaseYaml;

import software.wings.stencils.DefaultValue;

import com.github.reinert.jjschema.Attributes;
import com.google.common.base.MoreObjects;
import java.util.Objects;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Created by peeyushaggarwal on 8/3/16.
 */
@OwnedBy(CDC)
public class TailFilePatternEntry implements NestedAnnotationResolver {
  @DefaultValue("\"$WINGS_RUNTIME_PATH\"/")
  @Attributes(title = "File to tail")
  @Expression(ALLOW_SECRETS)
  private String filePath;
  @Attributes(title = "Pattern to search") @Expression(ALLOW_SECRETS) private String pattern;

  /**
   * Gets file path.
   *
   * @return the file path
   */
  public String getFilePath() {
    return filePath;
  }

  /**
   * Sets file path.
   *
   * @param filePath the file path
   */
  public void setFilePath(String filePath) {
    this.filePath = filePath;
  }

  /**
   * Gets pattern.
   *
   * @return the pattern
   */
  public String getPattern() {
    return pattern;
  }

  /**
   * Sets pattern.
   *
   * @param pattern the pattern
   */
  public void setPattern(String pattern) {
    this.pattern = pattern;
  }

  @Override
  public int hashCode() {
    return Objects.hash(filePath, pattern);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    final TailFilePatternEntry other = (TailFilePatternEntry) obj;
    return Objects.equals(this.filePath, other.filePath) && Objects.equals(this.pattern, other.pattern);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("filePath", filePath).add("pattern", pattern).toString();
  }

  /**
   * The type Builder.
   */
  public static final class Builder {
    private String filePath;
    private String pattern;

    private Builder() {}

    /**
     * A tail file pattern entry builder.
     *
     * @return the builder
     */
    public static Builder aTailFilePatternEntry() {
      return new Builder();
    }

    /**
     * With file path builder.
     *
     * @param filePath the file path
     * @return the builder
     */
    public Builder withFilePath(String filePath) {
      this.filePath = filePath;
      return this;
    }

    /**
     * With pattern builder.
     *
     * @param pattern the pattern
     * @return the builder
     */
    public Builder withPattern(String pattern) {
      this.pattern = pattern;
      return this;
    }

    /**
     * But builder.
     *
     * @return the builder
     */
    public Builder but() {
      return aTailFilePatternEntry().withFilePath(filePath).withPattern(pattern);
    }

    /**
     * Build tail file pattern entry.
     *
     * @return the tail file pattern entry
     */
    public TailFilePatternEntry build() {
      TailFilePatternEntry tailFilePatternEntry = new TailFilePatternEntry();
      tailFilePatternEntry.setFilePath(filePath);
      tailFilePatternEntry.setPattern(pattern);
      return tailFilePatternEntry;
    }
  }

  @Data
  @NoArgsConstructor
  @EqualsAndHashCode(callSuper = true)
  public static class Yaml extends BaseYaml {
    private String filePath;
    // maps to pattern
    private String searchPattern;

    public static final class Builder {
      private String filePath;
      // maps to pattern
      private String searchPattern;

      private Builder() {}

      public static Builder anYaml() {
        return new Builder();
      }

      public Builder withFilePath(String filePath) {
        this.filePath = filePath;
        return this;
      }

      public Builder withSearchPattern(String searchPattern) {
        this.searchPattern = searchPattern;
        return this;
      }

      public Builder but() {
        return anYaml().withFilePath(filePath).withSearchPattern(searchPattern);
      }

      public Yaml build() {
        Yaml yaml = new Yaml();
        yaml.setFilePath(filePath);
        yaml.setSearchPattern(searchPattern);
        return yaml;
      }
    }
  }
}
