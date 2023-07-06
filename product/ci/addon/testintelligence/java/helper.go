// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package java

import (
	"fmt"
	"strings"

	"github.com/harness/harness-core/commons/go/lib/filesystem"
	"github.com/harness/harness-core/commons/go/lib/utils"
	"github.com/harness/harness-core/product/ci/common/external"
	"github.com/harness/ti-client/types"
	"go.uber.org/zap"
)

var (
	getFiles     = utils.GetFiles
	getWorkspace = external.GetWrkspcPath
	javaAgentArg = "-javaagent:/addon/bin/java-agent.jar=%s"
)

// GetJavaTests returns list of RunnableTests in the workspace with java extension.
// In case of errors, return empty list
func GetJavaTests(testGlobs []string) ([]types.RunnableTest, error) {
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
		node, _ := utils.ParseJavaNodeFromPath(path, testGlobs)
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
func GetScalaTests(testGlobs []string) ([]types.RunnableTest, error) {
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
		node, _ := utils.ParseJavaNodeFromPath(path, testGlobs)
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
func GetKotlinTests(testGlobs []string) ([]types.RunnableTest, error) {
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
		node, _ := utils.ParseJavaNodeFromPath(path, testGlobs)
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

func parseBazelTestRule(r string) (types.RunnableTest, error) {
	// r = //module:package.class
	if r == "" {
		return types.RunnableTest{}, fmt.Errorf("empty rule")
	}
	n := 2
	if !strings.Contains(r, ":") || len(strings.Split(r, ":")) < n {
		return types.RunnableTest{}, fmt.Errorf(fmt.Sprintf("rule does not follow the default format: %s", r))
	}
	// fullPkg = package.class
	fullPkg := strings.Split(r, ":")[1]
	for _, s := range bazelRuleSepList {
		fullPkg = strings.Replace(fullPkg, s, ".", -1)
	}
	pkgList := strings.Split(fullPkg, ".")
	if len(pkgList) < n {
		return types.RunnableTest{}, fmt.Errorf(fmt.Sprintf("rule does not follow the default format: %s", r))
	}
	cls := pkgList[len(pkgList)-1]
	pkg := strings.TrimSuffix(fullPkg, "."+cls)
	test := types.RunnableTest{Pkg: pkg, Class: cls}
	test.Autodetect.Rule = r
	return test, nil
}
