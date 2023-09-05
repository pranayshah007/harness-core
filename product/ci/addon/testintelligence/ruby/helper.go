// Copyright 2023 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

/*
Package python
Any Python application that can run through the rspec CLI
should be able to use this to perform test intelligence.

Test filtering:
rspec test
*/
package ruby

import (
	"fmt"
	"os"
)

func WriteGemFile(repoPath string) error {
	f, err := os.OpenFile("Gemfile", os.O_APPEND|os.O_CREATE|os.O_WRONLY, 0644)
	if err != nil {
		return err
	}
	defer f.Close()
	_, err = f.WriteString(fmt.Sprintf("gem 'harness_ruby_agent', path: '%s'", repoPath))
	if err != nil {
		return err
	}
	return nil
}
