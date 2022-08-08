/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static io.harness.annotations.dev.HarnessModule._957_CG_BEANS;
import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.data.validator.Trimmed;
import io.harness.expression.Expression;
import io.harness.yaml.BaseYaml;
import io.harness.yaml.YamlKeepEmptyAsIs;

import java.util.ArrayList;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.hibernate.validator.constraints.NotEmpty;

/**
 * Generic Name Value pair
 * @author rktummala on 10/27/17
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TargetModule(_957_CG_BEANS)
@OwnedBy(CDC)
@FieldNameConstants(innerTypeName = "NameValuePairKeys")
public class NameValuePair {
  @NotEmpty @Trimmed private String name;
  /*
    Value can only be of type String or in encrypted format
  */
  @YamlKeepEmptyAsIs @NotNull @Expression(ALLOW_SECRETS) private String value;

  /*
   Could be TEXT / ENCRYPTED_TEXT
   TODO: Why is this not an enum? @swagat
  */
  private String valueType;

  @Data
  @NoArgsConstructor
  @EqualsAndHashCode(callSuper = true)
  public abstract static class AbstractYaml extends BaseYaml {
    private String name;
    private String value;
    @EqualsAndHashCode.Exclude private String valueType;
    @EqualsAndHashCode.Exclude private List<AllowedValueYaml> allowedList = new ArrayList<>();

    public AbstractYaml(String name, String value, String valueType, List<AllowedValueYaml> allowedList) {
      this.name = name;
      this.value = value;
      this.valueType = valueType;
      this.allowedList = allowedList;
    }
  }

  @Data
  @NoArgsConstructor
  @EqualsAndHashCode(callSuper = true)
  public static class Yaml extends AbstractYaml {
    @Builder
    public Yaml(String name, String value, String valueType, List<AllowedValueYaml> allowedValueYamlList) {
      super(name, value, valueType, allowedValueYamlList);
    }
  }
}
