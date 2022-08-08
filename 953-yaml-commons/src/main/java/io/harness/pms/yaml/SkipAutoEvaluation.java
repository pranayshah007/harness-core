/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.yaml;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * SkipAutoEvaluation is used on fields of type {@link ParameterField} inside step parameter classes. It is intended to
 * be used on fields which we don't want the pipeline service to automatically evaluate.
 *
 * For example: assertion field in Http step parameters. Here, we want customers to use expressions based on the http
 * response. But this response in not available till the step completes the Http delegate task. So, we use this
 * annotation to skip automatic expression evaluation by the pipeline service and evaluate this condition manually in
 * the Http step after the delegate task completes.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface SkipAutoEvaluation {}
