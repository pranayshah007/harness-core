package main

import (
	"bufio"
	"encoding/json"
	"io/ioutil"
	"log"
	"os"
	"sort"
	"strings"
)

type ClassStruct struct {
	Name string `json:"name"`
}

func readLines(path string) ([]ClassStruct, error) {
	file, err := os.Open(path)
	if err != nil {
		return nil, err
	}
	defer file.Close()

	var classes []ClassStruct
	scanner := bufio.NewScanner(file)
	for scanner.Scan() {
		classes = append(classes, ClassStruct{strings.Split(scanner.Text(), " ")[1]})
	}

	//sort it so it's easier to select blocks of related in json
	sort.Slice(classes, func(i, j int) bool {
		return classes[i].Name < classes[j].Name
	})
	return classes, scanner.Err()
}

func toJson(lines []ClassStruct, outputPath string) error {
	file, _ := json.MarshalIndent(lines, "", "  ")
	return ioutil.WriteFile(outputPath, file, 0644)
}

func main() {
	lines, err := readLines("classloaded.txt")
	if err != nil {
		log.Fatalf("readLines: %s", err)
	}

	toJson(lines, "classloaded.json")
}
