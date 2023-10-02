/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.k8s.yaml;

import static org.yaml.snakeyaml.scanner.Constant.LINEBR;
import static org.yaml.snakeyaml.scanner.Constant.NULL_BL_T;
import static org.yaml.snakeyaml.scanner.Constant.NULL_OR_LINEBR;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;

import java.io.StringReader;
import java.io.StringWriter;
import lombok.Data;
import org.yaml.snakeyaml.reader.StreamReader;

@Data
@OwnedBy(HarnessTeam.CDP)
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
public class YamlScannerContext {
  public static final int COMMENT_TOKEN = '#';
  public static final int DOUBLE_SCALAR_TOKEN = '"';
  public static final int SINGLE_SCALAR_TOKEN = '\'';
  public static final int LITERAL_SCALAR_TOKEN = '|';
  public static final int FOLDED_SCALAR_TOKEN = '>';

  private int token;
  private int prevToken;
  private int prevNonEmptyToken;
  private int indent = -1;

  private StreamReader streamReader;
  private StringWriter output;

  public YamlScannerContext(String yamlFile) {
    this.streamReader = new StreamReader(new StringReader(yamlFile));
    this.output = new StringWriter();
    this.token = streamReader.peek();
  }

  public String getOutputYaml() {
    return output.toString();
  }

  public void forward() {
    prevToken = streamReader.peek();
    if (NULL_BL_T.hasNo(prevToken)) {
      this.prevNonEmptyToken = prevToken;
    }

    if (LINEBR.has(token)) {
      indent = 0;
      prevNonEmptyToken = 0;
    }

    streamReader.forward();
    this.token = streamReader.peek();
  }

  public void forwardToken() {
    write(streamReader.peek());
    forward();
  }

  public void forwardLineBreak() {
    while (LINEBR.has(streamReader.peek())) {
      indent = 0;
      prevNonEmptyToken = 0;
      forwardToken();
    }
  }

  public void forwardEmpty() {
    while (NULL_BL_T.has(streamReader.peek()) && streamReader.peek() != 0) {
      if (prevNonEmptyToken == 0) {
        indent++;
      }
      forwardToken();
    }
  }

  public void write(String content) {
    output.write(content);
  }

  public void write(int character) {
    output.write(character);
  }

  public boolean isComment() {
    return isComment(this.prevToken);
  }

  public boolean isComment(int prevToken) {
    return token == COMMENT_TOKEN && (NULL_BL_T.has(prevToken) || LINEBR.has(prevToken));
  }

  public boolean isQuotedScalarToken() {
    return DOUBLE_SCALAR_TOKEN == token || SINGLE_SCALAR_TOKEN == token;
  }

  public boolean isLiteralScalarToken() {
    if (prevNonEmptyToken != ':') {
      return false;
    }

    return LITERAL_SCALAR_TOKEN == token || FOLDED_SCALAR_TOKEN == token;
  }

  public boolean isEmpty() {
    return NULL_BL_T.has(token);
  }

  public boolean isLineBreak() {
    return NULL_OR_LINEBR.has(token);
  }

  public boolean isEof() {
    return token == 0;
  }
}
