package junit

import (
	"encoding/xml"
	"fmt"
	"io"
	"os"
	"path"

	"github.com/bmatcuk/doublestar"
)

// FIXME: Remove them after adding a logger
func printMsg(msg string, args ...interface{}) {
	if len(args) == 0 {
		fmt.Fprint(os.Stderr, msg)
	} else {
		fmt.Fprintf(os.Stderr, msg, args...)
	}
}

func fatalMsg(msg string, args ...interface{}) {
	printMsg(msg, args...)
	os.Exit(1)
}

type junitXML struct {
	TestCases []struct {
		File string  `xml:"file,attr"`
		Time float64 `xml:"time,attr"`
	} `xml:"testcase"`
}

//FIXME: Use gojunit package from addon code.
func loadJUnitXML(reader io.Reader) *junitXML {
	var junitXML junitXML

	decoder := xml.NewDecoder(reader)
	err := decoder.Decode(&junitXML)
	if err != nil {
		fatalMsg("failed to parse junit xml: %v\n", err)
	}

	return &junitXML
}

func addFileTimesFromIOReader(fileTimes map[string]float64, reader io.Reader) {
	junitXML := loadJUnitXML(reader)
	for _, testCase := range junitXML.TestCases {
		filePath := path.Clean(testCase.File)
		fileTimes[filePath] += testCase.Time
	}
}

func GetFileTimesFromJUnitXML(junitXMLPath string, fileTimes map[string]float64) {
	if junitXMLPath != "" {
		filenames, err := doublestar.Glob(junitXMLPath)
		if err != nil {
			fatalMsg("failed to match jUnit filename pattern: %v", err)
		}
		for _, junitFilename := range filenames {
			file, err := os.Open(junitFilename)
			if err != nil {
				fatalMsg("failed to open junit xml: %v\n", err)
			}
			printMsg("using test times from JUnit report %s\n", junitFilename)
			addFileTimesFromIOReader(fileTimes, file)
			file.Close()
		}
	} else {
		printMsg("using test times from JUnit report at stdin\n")
		addFileTimesFromIOReader(fileTimes, os.Stdin)
	}
}
