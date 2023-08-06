// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package java

import (
	"bufio"
	"fmt"
	"io"
	"path/filepath"
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
		pkg, err := ReadJavaPkg(log, fs, f, excludeList, 2)
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

// ReadJavaPkg read java file and return it's package name
func ReadJavaPkg(log *zap.SugaredLogger, fs filesystem.FileSystem, f string, excludeList []string, packageLen int) (string, error) {
	absPath, err := filepath.Abs(f)
	result := ""
	if !strings.HasSuffix(absPath, ".java") && !strings.HasSuffix(absPath, ".scala") && !strings.HasSuffix(absPath, ".kt") {
		return result, nil
	}
	if err != nil {
		log.Errorw("could not get absolute path", "file_name", f, err)
		return "", err
	}
	// TODO: (Vistaar)
	// This doesn't handle some special cases right now such as when there is a package
	// present in a multiline comment with multiple opening and closing comments.
	// We will require to read all the lines together to handle this.
	err = fs.ReadFile(absPath, func(fr io.Reader) error {
		scanner := bufio.NewScanner(fr)
		commentOpen := false
		for scanner.Scan() {
			l := strings.TrimSpace(scanner.Text())
			if strings.Contains(l, "/*") {
				commentOpen = true
			}
			if strings.Contains(l, "*/") {
				commentOpen = false
				continue
			}
			if commentOpen || strings.HasPrefix(l, "//") {
				continue
			}
			prev := ""
			pkg := ""
			for _, token := range strings.Fields(l) {
				if prev == "package" {
					pkg = token
					break
				}
				prev = token
			}
			if pkg != "" {
				pkg = strings.TrimSuffix(pkg, ";")
				tokens := strings.Split(pkg, ".")
				for _, exclude := range excludeList {
					if strings.HasPrefix(pkg, exclude) {
						log.Infow(fmt.Sprintf("Found package: %s having same package prefix as: %s. Excluding this package from the list...", pkg, exclude))
						return nil
					}
				}
				pkg = tokens[0]
				if packageLen == -1 {
					for i, token := range tokens {
						if i == 0 {
							continue
						}
						pkg = pkg + "." + strings.TrimSpace(token)
					}
					result = pkg
					return nil
				}
				for i := 1; i < packageLen && i < len(tokens); i++ {
					pkg = pkg + "." + strings.TrimSpace(tokens[i])
				}
				if pkg == "" {
					continue
				}
				result = pkg
				return nil
			}
		}
		if err := scanner.Err(); err != nil {
			log.Errorw(fmt.Sprintf("could not scan all the files. Error: %s", err))
			return err
		}
		return nil
	})
	if err != nil {
		log.Errorw("had issues while trying to auto detect java packages", err)
	}
	return result, nil
}


// ReadPkgs reads and populates java packages for all input files
func ReadPkgs(log *zap.SugaredLogger, fs filesystem.FileSystem, files []types.File) []types.File {
	for i, file := range files {
		if file.Status != types.FileDeleted {
			pkg, err := ReadJavaPkg(log, fs, file.Name, make([]string, 0), -1)
			if err != nil {
				log.Errorw("something went wrong when parsing package, using file path as package", zap.Error(err))
			}
			files[i].Package = pkg
		}
	}
	return files
}
