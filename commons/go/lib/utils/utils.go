// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package utils

import (
	"fmt"
	"path/filepath"
	"regexp"
	"strings"
	"time"

	"github.com/harness/harness-core/product/ci/common/external"
	"github.com/harness/ti-client/types"
	zglob "github.com/mattn/go-zglob"
)

type NodeType int32

const (
	NodeType_SOURCE   NodeType = 0
	NodeType_TEST     NodeType = 1
	NodeType_CONF     NodeType = 2
	NodeType_RESOURCE NodeType = 3
	NodeType_OTHER    NodeType = 4
)

type LangType int32

const (
	LangType_JAVA    LangType = 0
	LangType_CSHARP  LangType = 1
	LangType_PYTHON  LangType = 2
	LangType_UNKNOWN LangType = 3
	LangType_RUBY    LangType = 4
)

const (
	JAVA_SRC_PATH            = "src/main/java/"
	JAVA_TEST_PATH           = "src/test/java/"
	JAVA_RESOURCE_PATH       = "src/test/resources/"
	SCALA_TEST_PATH          = "src/test/scala/"
	KOTLIN_TEST_PATH         = "src/test/kotlin/"
	HarnessDefaultReportPath = "harness_test_results.xml"
)

var (
	getWorkspace        = external.GetWrkspcPath
	javaSourceRegex     = fmt.Sprintf("^.*%s", JAVA_SRC_PATH)
	javaTestRegex       = fmt.Sprintf("^.*%s", JAVA_TEST_PATH)
	scalaTestRegex      = fmt.Sprintf("^.*%s", SCALA_TEST_PATH)
	kotlinTestRegex     = fmt.Sprintf("^.*%s", KOTLIN_TEST_PATH)
	PYTHON_TEST_PATTERN = []string{"test_*.py", "*_test.py"}
	RUBY_TEST_PATTERN   = []string{"*_spec.rb"}
	extensionMapping    = map[string]LangTypeAndTestPattern{
		".py": LangTypeAndTestPattern{
			LangType:    LangType_PYTHON,
			TestPattern: PYTHON_TEST_PATTERN,
		},
		".rb": LangTypeAndTestPattern{
			LangType:    LangType_RUBY,
			TestPattern: RUBY_TEST_PATTERN,
		},
	}
)

//Node holds data about a source code
type Node struct {
	Pkg    string
	Class  string
	Method string
	File   string
	Lang   LangType
	Type   NodeType
}

type LangTypeAndTestPattern struct {
	LangType    LangType
	TestPattern []string
}

//TimeSince returns the number of milliseconds that have passed since the given time
func TimeSince(t time.Time) float64 {
	return Ms(time.Since(t))
}

//Ms returns the duration in millisecond
func Ms(d time.Duration) float64 {
	return float64(d) / float64(time.Millisecond)
}

//NoOp is a basic NoOp function
func NoOp() error {
	return nil
}

// IsTest checks whether the parsed node is of a test type or not.
func IsTest(node Node) bool {
	return node.Type == NodeType_TEST
}

// IsSupported checks whether we can perform an action for the node type or not.
func IsSupported(node Node) bool {
	return node.Type == NodeType_TEST || node.Type == NodeType_SOURCE || node.Type == NodeType_RESOURCE
}

// GetFiles gets list of all file paths matching a provided regex
func GetFiles(path string) ([]string, error) {
	fmt.Println("path: ", path)
	matches, err := zglob.Glob(path)
	if err != nil {
		return []string{}, err
	}
	return matches, err
}

// ParseCsharpNode extracts the class name from a Dotnet file path
// e.g., src/abc/def/A.cs
// will return class = A
func ParseCsharpNode(file types.File, testGlobs []string) (*Node, error) {
	var node Node
	node.Pkg = ""
	node.Class = ""
	node.Lang = LangType_UNKNOWN
	node.Type = NodeType_OTHER

	filename := strings.TrimSpace(file.Name)
	if !strings.HasSuffix(filename, ".cs") {
		return &node, nil
	}
	node.Lang = LangType_CSHARP
	node.Type = NodeType_SOURCE

	for _, glob := range testGlobs {
		if matched, _ := zglob.Match(glob, filename); !matched {
			continue
		}
		node.Type = NodeType_TEST
	}
	f := strings.TrimSuffix(filename, ".cs")
	parts := strings.Split(f, "/")
	node.Class = parts[len(parts)-1]
	return &node, nil
}

// ParsePythonNode extracts the file name from a file path
// e.g., src/abc/def/A.py
// will return class = src/abc/def/A.py file = src/abc/def/A.py
func ParsePythonNode(file types.File, testGlobs []string) *Node {
	return ParsePathBasedNode(file, testGlobs, LangType_PYTHON)
}

// ParseRubyNode extracts the file name from a file path
// e.g., src/abc/def/A.rb
// will return class = src/abc/def/A.rb file = src/abc/def/A.rb
func ParseRubyNode(file types.File, testGlobs []string) *Node {
	return ParsePathBasedNode(file, testGlobs, LangType_RUBY)
}

// ParsePathBasedNode extracts the file name from a file path
// e.g., src/abc/def/A.py
// will return class = src/abc/def/A.py file = src/abc/def/A.py
func ParsePathBasedNode(file types.File, testGlobs []string, langType LangType) *Node {
	var node Node
	node.Pkg = ""
	node.Lang = langType
	node.Type = NodeType_SOURCE

	filename := strings.TrimSpace(file.Name)

	for _, glob := range testGlobs {
		if matched, _ := zglob.Match(glob, filename); !matched {
			continue
		}
		node.Type = NodeType_TEST
	}
	node.File = filename
	node.Class = filename
	return &node
}

// ParsePathBasedNodeAndType extracts the file name from a file path of unknown language
// e.g., src/abc/def/A.py
// will return class = src/abc/def/A.py file = src/abc/def/A.py
func ParsePathBasedNodeAndType(file types.File, testGlobs []string) (*Node, error) {
	filename := strings.TrimSpace(file.Name)
	for extension, langAndPattern := range extensionMapping {
		if strings.HasSuffix(filename, extension) {
			LangType := langAndPattern.LangType
			if len(testGlobs) == 0 {
				testGlobs = langAndPattern.TestPattern
			}
			return ParsePathBasedNode(file, testGlobs, LangType), nil
		}
	}
	// If not any of the extension, it might be a java resource
	return ParseJavaNode(file)
}

// GetTestsFromLocal creates list of RunnableTest within file system on given langauge and extension
// e.g., extension: py
// will return all files with .py, formatted to python node
func GetTestsFromLocal(testGlobs []string, extension string, langType LangType) ([]types.RunnableTest, error) {
	tests := make([]types.RunnableTest, 0)
	wp, err := getWorkspace()
	if err != nil {
		return tests, err
	}

	files, _ := GetFiles(fmt.Sprintf("%s/**/*.%s", wp, extension))
	for _, path := range files {
		if path == "" {
			continue
		}
		f := types.File{Name: path}
		node := ParsePathBasedNode(f, testGlobs, langType)
		if node.Type != NodeType_TEST {
			continue
		}
		test := types.RunnableTest{
			Class: node.File,
		}
		tests = append(tests, test)
	}
	return tests, nil
}

//ParseJavaNodeFromPath extracts the pkg and class names from a Java file path
// e.g., 320-ci-execution/src/main/java/io/harness/stateutils/buildstate/ConnectorUtils.java
// will return pkg = io.harness.stateutils.buildstate, class = ConnectorUtils
func ParseJavaNodeFromPath(file string, testGlobs []string) (*Node, error) {
	return ParseJavaNode(types.File{
		Name: file,
	})
}

//ParseJavaNode extracts the pkg and class names from a Java file
// if the node already contains package from addon it will use it
// e.g. if package not provided, 320-ci-execution/src/main/java/io/harness/stateutils/buildstate/ConnectorUtils.java
// will return pkg = io.harness.stateutils.buildstate, class = ConnectorUtils
func ParseJavaNode(file types.File) (*Node, error) {
	var node Node
	node.Pkg = ""
	node.Class = ""
	node.Lang = LangType_UNKNOWN
	node.Type = NodeType_OTHER

	filename := strings.TrimSpace(file.Name)

	var r *regexp.Regexp
	if strings.Contains(filename, JAVA_SRC_PATH) && strings.HasSuffix(filename, ".java") {
		r = regexp.MustCompile(javaSourceRegex)
		node.Type = NodeType_SOURCE
		rr := r.ReplaceAllString(filename, "${1}") // extract the 2nd part after matching the src/main/java prefix
		rr = strings.TrimSuffix(rr, ".java")

		parts := strings.Split(rr, "/")
		p := parts[:len(parts)-1]
		node.Class = parts[len(parts)-1]
		node.Lang = LangType_JAVA
		if file.Package != "" {
			node.Pkg = file.Package
		} else {
			node.Pkg = strings.Join(p, ".")
		}
	} else if strings.Contains(filename, JAVA_TEST_PATH) && strings.HasSuffix(filename, ".java") {
		r = regexp.MustCompile(javaTestRegex)
		node.Type = NodeType_TEST
		rr := r.ReplaceAllString(filename, "${1}") // extract the 2nd part after matching the src/test/java prefix
		rr = strings.TrimSuffix(rr, ".java")

		parts := strings.Split(rr, "/")
		p := parts[:len(parts)-1]
		node.Class = parts[len(parts)-1]
		node.Lang = LangType_JAVA
		if file.Package != "" {
			node.Pkg = file.Package
		} else {
			node.Pkg = strings.Join(p, ".")
		}
	} else if strings.Contains(filename, JAVA_RESOURCE_PATH) {
		node.Type = NodeType_RESOURCE
		parts := strings.Split(filename, "/")
		node.File = parts[len(parts)-1]
		node.Lang = LangType_JAVA
	} else if strings.HasSuffix(filename, ".scala") {
		// If the scala filepath does not match any of the test paths below, return generic source node
		node.Type = NodeType_SOURCE
		node.Lang = LangType_JAVA
		f := strings.TrimSuffix(filename, ".scala")
		parts := strings.Split(f, "/")
		node.Class = parts[len(parts)-1]
		// Check for Test Node
		if strings.Contains(filename, SCALA_TEST_PATH) {
			r = regexp.MustCompile(scalaTestRegex)
			node.Type = NodeType_TEST
			rr := r.ReplaceAllString(f, "${1}")
			parts = strings.Split(rr, "/")
			p := parts[:len(parts)-1]
			node.Pkg = strings.Join(p, ".")
		} else if strings.Contains(filename, JAVA_TEST_PATH) {
			r = regexp.MustCompile(javaTestRegex)
			node.Type = NodeType_TEST
			rr := r.ReplaceAllString(f, "${1}")
			parts = strings.Split(rr, "/")
			p := parts[:len(parts)-1]
			node.Pkg = strings.Join(p, ".")
		}
		if file.Package != "" {
			node.Pkg = file.Package
		}
	} else if strings.HasSuffix(filename, ".kt") {
		// If the kotlin filepath does not match any of the test paths below, return generic source node
		node.Type = NodeType_SOURCE
		node.Lang = LangType_JAVA
		f := strings.TrimSuffix(filename, ".kt")
		parts := strings.Split(f, "/")
		node.Class = parts[len(parts)-1]
		// Check for Test Node
		if strings.Contains(filename, KOTLIN_TEST_PATH) {
			r = regexp.MustCompile(kotlinTestRegex)
			node.Type = NodeType_TEST
			rr := r.ReplaceAllString(f, "${1}")

			parts = strings.Split(rr, "/")
			p := parts[:len(parts)-1]
			node.Pkg = strings.Join(p, ".")
		} else if strings.Contains(filename, JAVA_TEST_PATH) {
			r = regexp.MustCompile(javaTestRegex)
			node.Type = NodeType_TEST
			rr := r.ReplaceAllString(f, "${1}")

			parts = strings.Split(rr, "/")
			p := parts[:len(parts)-1]
			node.Pkg = strings.Join(p, ".")
		}
		if file.Package != "" {
			node.Pkg = file.Package
		}
	} else {
		return &node, nil
	}

	return &node, nil
}

//ParseFileNames accepts a list of file names, parses and returns the list of Node
func ParseFileNames(files []types.File, testGlobs []string) ([]Node, error) {

	nodes := make([]Node, 0)
	for _, file := range files {
		path := file.Name
		if len(path) == 0 {
			continue
		}
		var node *Node
		extension := filepath.Ext(path)
		switch extension {
		case ".cs":
			node, _ = ParseCsharpNode(file, testGlobs)
		case ".java", ".kt", ".sc":
			node, _ = ParseJavaNode(file)
		case ".py":
			node = ParsePythonNode(file, testGlobs)
		case ".rb":
			node = ParseRubyNode(file, testGlobs)
		default:
			node, _ = ParsePathBasedNodeAndType(file, testGlobs)
		}
		nodes = append(nodes, *node)
	}
	return nodes, nil
}

// ParseFileNamesWithLanguage extract Node from given file path and language strings
func ParseFileNamesWithLanguage(files []types.File, testGlobs []string, language string) []Node {
	nodes := make([]Node, 0)
	for _, file := range files {
		path := file.Name
		if len(path) == 0 {
			continue
		}
		var node *Node
		var err error
		switch language {
		case "csharp":
			node, err = ParseCsharpNode(file, testGlobs)
		case "java", "scala", "kotlin":
			node, err = ParseJavaNode(file)
		case "python":
			node = ParsePythonNode(file, testGlobs)
		case "ruby":
			node = ParseRubyNode(file, testGlobs)
		default:
			node, err = ParsePathBasedNodeAndType(file, testGlobs)
		}
		if err != nil {
			continue
		}
		nodes = append(nodes, *node)
	}
	return nodes
}

// GetSliceDiff returns the unique element in sIDs which are not present in dIDs
func GetSliceDiff(sIDs []int, dIDs []int) []int {
	mp := make(map[int]bool)
	var ret []int
	for _, id := range dIDs {
		mp[id] = true
	}
	for _, id := range sIDs {
		if _, ok := mp[id]; !ok {
			ret = append(ret, id)
		}
	}
	return ret
}

// GetRepoUrl takes the repo address and appends .git at the end if it doesn't ends with .git
// TODO: Check if this works for SSH access
func GetRepoUrl(repo string) string {
	if !strings.HasSuffix(repo, ".git") {
		repo += ".git"
	}
	return repo
}

// GetUniqueTestStrings extract list of test strings from Class
// It should only work if Class is the only primary identifier of the test selection
func GetUniqueTestStrings(tests []types.RunnableTest) []string {
	set := make(map[types.RunnableTest]interface{})
	ut := []string{}
	for _, t := range tests {
		w := types.RunnableTest{Class: t.Class}
		if _, ok := set[w]; ok {
			// The test has already been added
			continue
		}
		set[w] = struct{}{}
		ut = append(ut, t.Class)
	}
	return ut
}
