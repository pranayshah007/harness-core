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
	"errors"
	"fmt"
	"os"
	"path/filepath"
)

// WriteHelperFile writes the rspec helper file needed to attach agent.
// If no rspec helper file fond in this pattern or any error happens,
// will print a message ask for manual writeand continue
func WriteHelperFile(repoPath string) error {
	pattern := "**/*spec_helper*.rb"

	matches, err := filepath.Glob(pattern)
	if err != nil {
		return err
	}
	if len(matches) == 0 {
		return errors.New("cannot find rspec helper file. Please make change manually to enable TI")
	}

	f, err := os.OpenFile(findRootMostPath(matches), os.O_APPEND|os.O_WRONLY, 0644)
	if err != nil {
		return err
	}
	defer f.Close()
	scriptPath := filepath.Join(repoPath, "test_intelligence.rb")
	_, err = f.WriteString(fmt.Sprintf("\nrequire '%s'", scriptPath))
	if err != nil {
		return err
	}
	return nil
}

// WriteGemFile writes the Ruby Gem file needed to attach agent.
// If no Gemfile found we will creat the Gemfile
func WriteGemFile(repoPath string) error {
	f, err := os.OpenFile("Gemfile", os.O_APPEND|os.O_CREATE|os.O_WRONLY, 0644)
	if err != nil {
		return err
	}
	defer f.Close()
	_, err = f.WriteString(fmt.Sprintf("\ngem 'harness_ruby_agent', path: '%s'", repoPath))
	if err != nil {
		return err
	}
	return nil
}

// findRootMostPath helper funtion to find shortest file path
func findRootMostPath(paths []string) string {
	rootmost := paths[0]
	for _, path := range paths[1:] {
		if len(path) < len(rootmost) {
			rootmost = path
		}
	}
	return rootmost
}
