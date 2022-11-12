// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package schema

import (
	"bytes"
	"compress/gzip"
	"fmt"
	"io"
	"strings"
)

func bindata_read(data []byte, name string) ([]byte, error) {
	gz, err := gzip.NewReader(bytes.NewBuffer(data))
	if err != nil {
		return nil, fmt.Errorf("Read %q: %v", name, err)
	}

	var buf bytes.Buffer
	_, err = io.Copy(&buf, gz)
	gz.Close()

	if err != nil {
		return nil, fmt.Errorf("Read %q: %v", name, err)
	}

	return buf.Bytes(), nil
}

var _callgraph_avsc = []byte("\x1f\x8b\x08\x00\x00\x00\x00\x00\x00\xff\xe4\x53\xbd\x6e\xf3\x30\x0c\xdc\xf3\x14\x04\x67\x23\x0f\xe0\xf5\x9b\xbe\xad\xc8\x5a\x64\x60\x2d\x3a\x16\x2a\x4b\x86\xc8\xb4\x08\x8c\xbc\x7b\x21\x3b\x4e\xfd\x37\x04\x2e\xda\xa1\xd5\x24\x51\xbc\x3b\xf1\x28\xb6\x3b\x00\x00\xf4\x54\x33\xe6\x80\x4f\x14\xd9\x2b\x66\x7d\x54\x2f\x0d\x63\x8e\x91\x8b\x10\xcd\x10\x4c\xa9\xd2\x50\xc1\x08\x39\xa0\xda\x7d\x45\xd1\xb3\xc8\xde\x86\x21\xa5\xb4\xec\x8c\x60\xfe\xdc\x1d\xd3\x6a\xef\xbb\x91\x1c\xfa\x60\x58\x6e\xa0\xfb\x5d\x2f\x3a\x05\x8c\x2e\x00\x29\x46\xba\x60\x06\xb0\x4c\xb1\xca\xb5\xac\x81\xc7\xaa\xff\x2a\xeb\xcc\x4c\x75\x26\x32\x2d\x79\x91\xb4\xac\x6f\xbe\xda\x5e\x2c\x39\x54\xb3\x56\xc1\x60\x76\xe3\x4e\x21\xd1\x68\xfd\x09\xaf\xeb\xf4\x33\x7c\x43\xc5\x2b\x9d\x78\x3b\x81\x9d\x8a\x5b\xaf\x0f\x02\x0b\x47\x22\xff\xb7\xa2\x1b\x8a\x54\xcb\xf6\x67\x77\xea\xdb\xe1\x1d\x6a\x13\x3a\x69\x93\x73\x72\xe0\xd2\x71\xa1\x36\xf8\x09\xcf\x4b\x08\x8e\xc9\x3f\x46\x44\xee\x9d\x2e\x72\x38\xaf\x53\x64\x80\x86\x4b\x3a\x3b\xc5\x1c\x4a\x72\xc2\x0f\x91\x96\xd6\xad\x97\xb6\x8a\x3d\x2e\xa2\xd3\xbc\xcf\xd3\x48\x7c\x7d\x5e\x95\x45\x0f\xec\x28\x79\xf2\xeb\xe7\x56\xc2\x39\x16\xbc\xf1\xf3\x27\xa7\x26\x9f\xb7\x1d\x5e\x38\xb8\x30\xd4\xdc\x93\x7e\x7b\xeb\xde\xac\x9c\x22\x35\xd5\x97\xdb\xf7\x07\x9a\x67\x58\xd4\xfa\xc1\xa6\x9f\xeb\x61\xb7\x3b\xc2\xee\xba\xfb\x08\x00\x00\xff\xff\xda\x2d\xdf\x57\x96\x07\x00\x00")

func callgraph_avsc() ([]byte, error) {
	return bindata_read(
		_callgraph_avsc,
		"callgraph.avsc",
	)
}

// Asset loads and returns the asset for the given name.
// It returns an error if the asset could not be found or
// could not be loaded.
func Asset(name string) ([]byte, error) {
	cannonicalName := strings.Replace(name, "\\", "/", -1)
	if f, ok := _bindata[cannonicalName]; ok {
		return f()
	}
	return nil, fmt.Errorf("Asset %s not found", name)
}

// AssetNames returns the names of the assets.
func AssetNames() []string {
	names := make([]string, 0, len(_bindata))
	for name := range _bindata {
		names = append(names, name)
	}
	return names
}

// _bindata is a table, holding each asset generator, mapped to its name.
var _bindata = map[string]func() ([]byte, error){
	"callgraph.avsc": callgraph_avsc,
}

// AssetDir returns the file names below a certain
// directory embedded in the file by go-bindata.
// For example if you run go-bindata on data/... and data contains the
// following hierarchy:
//     data/
//       foo.txt
//       img/
//         a.png
//         b.png
// then AssetDir("data") would return []string{"foo.txt", "img"}
// AssetDir("data/img") would return []string{"a.png", "b.png"}
// AssetDir("foo.txt") and AssetDir("notexist") would return an error
// AssetDir("") will return []string{"data"}.
func AssetDir(name string) ([]string, error) {
	node := _bintree
	if len(name) != 0 {
		cannonicalName := strings.Replace(name, "\\", "/", -1)
		pathList := strings.Split(cannonicalName, "/")
		for _, p := range pathList {
			node = node.Children[p]
			if node == nil {
				return nil, fmt.Errorf("Asset %s not found", name)
			}
		}
	}
	if node.Func != nil {
		return nil, fmt.Errorf("Asset %s not found", name)
	}
	rv := make([]string, 0, len(node.Children))
	for name := range node.Children {
		rv = append(rv, name)
	}
	return rv, nil
}

type _bintree_t struct {
	Func     func() ([]byte, error)
	Children map[string]*_bintree_t
}

var _bintree = &_bintree_t{nil, map[string]*_bintree_t{
	"callgraph.avsc": &_bintree_t{callgraph_avsc, map[string]*_bintree_t{}},
}}
