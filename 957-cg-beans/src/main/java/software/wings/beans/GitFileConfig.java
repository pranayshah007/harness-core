/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.validator.Trimmed;
import io.harness.expression.Expression;
import io.harness.reflection.ExpressionReflectionUtils.NestedAnnotationResolver;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import java.util.List;
import javax.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Transient;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldNameConstants(innerTypeName = "GitFileConfigKeys")
@OwnedBy(CDP)
public class GitFileConfig implements NestedAnnotationResolver {
  @Trimmed private String connectorId;
  @Trimmed private String commitId;
  @Trimmed private String branch;
  @Expression(ALLOW_SECRETS) @Trimmed private String filePath;
  @Trimmed @Nullable private String repoName;
  @Expression(ALLOW_SECRETS) private List<String> filePathList;
  @Trimmed @Nullable private String serviceSpecFilePath;
  @Trimmed @Nullable private String taskSpecFilePath;
  private boolean useBranch;
  private boolean useInlineServiceDefinition;
  @Transient @JsonInclude(Include.NON_EMPTY) private String connectorName;
}
