// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package java

import (
	"fmt"
	"github.com/harness/harness-core/commons/go/lib/filesystem"
	"github.com/harness/harness-core/commons/go/lib/utils"
	"github.com/harness/harness-core/product/ci/common/external"
	"github.com/harness/harness-core/product/ci/ti-service/types"
	"github.com/mattn/go-zglob"
	"go.uber.org/zap"
)

var (
	getWorkspace = external.GetWrkspcPath
	javaAgentArg = "-javaagent:/addon/bin/java-agent.jar=%s"
)

// get list of all file paths matching a provided regex
func getFiles(path string) ([]string, error) {
	fmt.Println("path: ", path)
	matches, err := zglob.Glob(path)
	if err != nil {
		return []string{}, err
	}
	return matches, err
}

// GetJavaTests returns list of RunnableTests in the workspace with java extension.
// In case of errors, return empty list
func GetJavaTests() ([]types.RunnableTest, error) {
	tests := make([]types.RunnableTest, 0)
	wp, err := getWorkspace()
	if err != nil {
		return tests, err
	}

	files, _ := getFiles(fmt.Sprintf("%s/**/*.java", wp))
	for _, path := range files {
		if path == "" {
			continue
		}
		node, _ := utils.ParseJavaNodeFromPath(path)
		if node.Type != utils.NodeType_TEST {
			continue
		}
		test := types.RunnableTest{
			Pkg:   node.Pkg,
			Class: node.Class,
		}
		tests = append(tests, test)
	}
	return tests, nil
}

// GetScalaTests returns list of RunnableTests in the workspace with scala extension.
// In case of errors, return empty list
func GetScalaTests() ([]types.RunnableTest, error) {
	tests := make([]types.RunnableTest, 0)
	wp, err := getWorkspace()
	if err != nil {
		return tests, err
	}

	files, _ := getFiles(fmt.Sprintf("%s/**/*.scala", wp))
	for _, path := range files {
		if path == "" {
			continue
		}
		node, _ := utils.ParseJavaNodeFromPath(path)
		if node.Type != utils.NodeType_TEST {
			continue
		}
		test := types.RunnableTest{
			Pkg:   node.Pkg,
			Class: node.Class,
		}
		tests = append(tests, test)
	}
	return tests, nil
}

// GetKotlinTests returns list of RunnableTests in the workspace with kotlin extension.
// In case of errors, return empty list
func GetKotlinTests() ([]types.RunnableTest, error) {
	tests := make([]types.RunnableTest, 0)
	wp, err := getWorkspace()
	if err != nil {
		return tests, err
	}

	files, _ := getFiles(fmt.Sprintf("%s/**/*.kt", wp))
	for _, path := range files {
		if path == "" {
			continue
		}
		node, _ := utils.ParseJavaNodeFromPath(path)
		if node.Type != utils.NodeType_TEST {
			continue
		}
		test := types.RunnableTest{
			Pkg:   node.Pkg,
			Class: node.Class,
		}
		tests = append(tests, test)
	}
	return tests, nil
}

// DetectPkgs detects java packages by reading all the files and parsing their package names
func DetectPkgs(log *zap.SugaredLogger, fs filesystem.FileSystem) ([]string, error) {
	plist := []string{}
	excludeList := []string{"com.google"} // exclude any instances of these packages from the package list
	wp, err := getWorkspace()
	if err != nil {
		return plist, err
	}
	files, err := getFiles(fmt.Sprintf("%s/**/*.java", wp))
	if err != nil {
		return plist, err
	}
	kotlinFiles, err := getFiles(fmt.Sprintf("%s/**/*.kt", wp))
	if err != nil {
		return plist, err
	}
	scalaFiles, err := getFiles(fmt.Sprintf("%s/**/*.scala", wp))
	if err != nil {
		return plist, err
	}
	// Create a list with all *.java, *.kt and *.scala file paths
	files = append(files, kotlinFiles...)
	files = append(files, scalaFiles...)
	fmt.Println("files: ", files)
	m := make(map[string]struct{})
	for _, f := range files {
		pkg, err := utils.ReadJavaPkg(log, fs, f, excludeList, 2)
		if err != nil {
			return plist, err
		}
		if _, ok := m[pkg]; !ok && pkg != "" {
			plist = append(plist, pkg)
			m[pkg] = struct{}{}
		}
	}
	return plist, nil
}

