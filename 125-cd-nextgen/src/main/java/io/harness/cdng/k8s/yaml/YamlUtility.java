/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.k8s.yaml;

import static io.harness.cdng.k8s.yaml.YamlScannerContext.COMMENT_TOKEN;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.CDP)
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
public class YamlUtility {
  public static final String REDACTED_BY_HARNESS = "__REDACTED_BY_HARNESS__";

  public String removeComments(String file) {
    if (isEmpty(file)) {
      return file;
    }

    YamlScannerContext scannerContext = new YamlScannerContext(file);
    while (!scannerContext.isEof()) {
      // forward any empty tokens to calculate the right indentation value
      if (scannerContext.isEmpty()) {
        scannerContext.forwardEmpty();
        continue;
      }

      // if value is quoted then it can't contain comments
      if (scannerContext.isQuotedScalarToken()) {
        forwardQuotedScalar(scannerContext);
        continue;
      }

      // any comments (values that contains comment token #) are part of the literal value and not an actual comment
      if (scannerContext.isLiteralScalarToken()) {
        forwardLiteralScalar(scannerContext);
        continue;
      }

      if (scannerContext.isComment()) {
        forwardComment(scannerContext);
        continue;
      }

      // process this token
      scannerContext.forwardToken();
    }

    return scannerContext.getOutputYaml();
  }

  private void forwardQuotedScalar(YamlScannerContext context) {
    int startToken = context.getToken();
    // Add start token
    context.forwardToken();

    // read till current token doesn't match start token (' or ")
    while (context.getToken() != startToken && !context.isEof()) {
      context.forwardToken();
    }

    // Add end token
    context.forwardToken();
  }

  private void forwardLiteralScalar(YamlScannerContext context) {
    int scalarIndent = -1;
    // get the current value indent, for example for line '....key: value' the indent will be 4
    // this is required for indentation mark used with | or > as is relative to the current key indentation
    int blockIndent = context.getIndent();

    // Add literal token
    context.forwardToken();

    // First character after | or > can be the indentation level for literal value. Value can be only 0 to 9
    if (Character.isDigit(context.getToken())) {
      String indentAsString = String.valueOf(Character.toChars(context.getToken()));
      scalarIndent = Integer.parseInt(indentAsString);
      context.forwardToken();
    }

    while (!context.isLineBreak()) {
      // Since the previous token must be | or > then it does mean that it's a comment even if there is
      // no whitespace token
      if (context.isComment(0)) {
        forwardComment(context);
        continue;
      }

      // forward everything else
      context.forwardToken();
    }

    if (context.isLineBreak()) {
      context.forwardLineBreak();
    }

    int literalIndent = -1;
    while (!context.isEof()) {
      // forward all empty tokens, this will compute the indentation for line
      if (context.isEmpty()) {
        context.forwardEmpty();
        continue;
      }

      // if indentation mark is present for literal token then calculate literal indentation based on key indentation
      // and the value of indentation mark
      if (scalarIndent != -1 && literalIndent == -1) {
        literalIndent = Math.max(blockIndent + scalarIndent, context.getIndent());
      } else if (literalIndent == -1) {
        // if no mark indentation is present then use current indentation as min literal indentation
        literalIndent = context.getIndent();
      }

      // if the current indentation (it will be calculated as part of the forwardEmpty) is less than min indentation
      // then this is the end of literal value
      if (context.getIndent() < literalIndent) {
        return;
      }

      // forward everything till line break or stream end
      while (!context.isLineBreak()) {
        context.forwardToken();
      }

      context.forwardLineBreak();
    }
  }

  private void forwardComment(YamlScannerContext context) {
    // the current requirement is to not render comments in yaml
    // instead of completely remove the comment we're replacing it with a constant value
    context.write(COMMENT_TOKEN);
    context.write(' ');
    context.write(REDACTED_BY_HARNESS);
    // ignoring everything in stream till reaching stream end or new line
    while (!context.isLineBreak()) {
      context.forward();
    }
  }
}
