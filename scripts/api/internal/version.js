/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

const yaml = require('js-yaml');
const fs = require('fs');
var words = yaml.safeLoad(fs.readFileSync(process.argv[2], 'utf8'));
func()
function func() {
  return console.log(words['info'].version);
}