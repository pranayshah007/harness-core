/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A marker annotation for Harness entity classes.
 *
 * If the 'exportable' attribute is true, the account migration process will try exporting records associated with the
 * specified account from the mongo collections associated with this entity. For any entity classes annotated with
 * exportable, it should either have a 'accountId' field or an 'appId' field.
 *
 * If the 'exportable' attribute is false, it won't participate in the account migration. Some entities such as
 * deployment history, instance stats, ephemeral lock, delegate tasks etc. should fall into this category.
 *
 * @author marklu on 9/27/19
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface HarnessEntity {
  boolean exportable();
}
