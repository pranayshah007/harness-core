// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package callgraph_old

import (
	"bytes"
	"compress/gzip"
	"crypto/sha256"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"
	"time"
)

func bindataRead(data []byte, name string) ([]byte, error) {
	gz, err := gzip.NewReader(bytes.NewBuffer(data))
	if err != nil {
		return nil, fmt.Errorf("read %q: %w", name, err)
	}

	var buf bytes.Buffer
	_, err = io.Copy(&buf, gz)
	clErr := gz.Close()

	if err != nil {
		return nil, fmt.Errorf("read %q: %w", name, err)
	}
	if clErr != nil {
		return nil, err
	}

	return buf.Bytes(), nil
}

type asset struct {
	bytes  []byte
	info   os.FileInfo
	digest [sha256.Size]byte
}

type bindataFileInfo struct {
	name    string
	size    int64
	mode    os.FileMode
	modTime time.Time
}

func (fi bindataFileInfo) Name() string {
	return fi.name
}
func (fi bindataFileInfo) Size() int64 {
	return fi.size
}
func (fi bindataFileInfo) Mode() os.FileMode {
	return fi.mode
}
func (fi bindataFileInfo) ModTime() time.Time {
	return fi.modTime
}
func (fi bindataFileInfo) IsDir() bool {
	return false
}
func (fi bindataFileInfo) Sys() interface{} {
	return nil
}

var _callgraphAvsc = []byte("\x1f\x8b\x08\x00\x00\x00\x00\x00\x00\xff\xe4\x53\xbd\x6e\xfb\x20\x10\xdf\xf3\x14\xe8\xe6\x28\x0f\xe0\xf5\x3f\xfd\xb7\x2a\x6b\x95\xe1\x6a\x2e\xf6\xa9\x18\x2c\x8e\x56\x8a\xac\xbc\x7b\x85\x1d\x52\x63\x7b\x88\xa8\xda\xa1\x65\x82\xe3\xf7\x71\x1f\x30\xec\x94\x52\x0a\x2c\x76\x04\x95\x82\x27\xf4\x64\x03\xec\xa7\x68\xb8\xf4\x04\x15\x78\xaa\x9d\xd7\x29\x18\xa1\xd2\x63\x4d\xa0\x2a\x05\x81\x0f\x2d\x7a\x4b\x22\x07\x76\x09\x72\x66\x32\x5a\xa0\x7a\x1e\x8f\x71\x0d\xf7\xdd\xcc\x0e\xac\xd3\x24\x37\xd2\xfd\x6e\x32\xcd\x09\xb3\x0b\x05\xe8\x3d\x5e\x60\xaf\xd4\x1a\xc2\x81\x3a\xd9\x22\xcf\x5d\xff\xb5\x6c\xf4\xc2\x75\x61\x92\x97\xbc\x02\xad\xeb\x5b\xae\x61\x32\x8b\x1d\xea\x28\xb4\x4e\xc3\xfe\xa6\x1d\x43\x12\x3c\xdb\x06\xae\xdb\xf2\x0b\x7e\x8f\xf5\x2b\x36\x54\x2e\xc0\xb9\x39\xdb\xf0\x20\xb1\x36\x28\xf2\xbf\x94\xdd\xa3\xc7\x4e\xca\xd3\x1e\xdd\xcb\xe9\x23\xab\x88\x1d\xbd\xd1\x18\x39\xd2\xd9\x50\x1d\xd8\xd9\x4c\xe7\xc5\x39\x43\x68\x1f\x13\x3a\xb3\xd9\xce\x62\x93\x7b\x5a\x45\x73\xdc\xe7\x69\x66\xbe\xfd\xb5\x02\x49\x38\x92\xc1\x98\xfe\xaf\xff\x62\xe2\xde\x7c\x4d\x85\xef\x34\x76\x2a\x7b\x67\x43\xca\x30\x75\x21\xd5\x3c\x89\x7e\xfb\xe8\xde\x59\x1a\x8f\x7d\xfb\xe5\xf1\xfd\x81\xe1\x69\x92\xc0\x36\xb5\xe9\xe7\x66\x38\xee\x4e\x6a\x77\xdd\x7d\x04\x00\x00\xff\xff\xb2\x83\xa4\xfd\x41\x07\x00\x00")

func callgraphAvscBytes() ([]byte, error) {
	return bindataRead(
		_callgraphAvsc,
		"callgraph.avsc",
	)
}

func callgraphAvsc() (*asset, error) {
	bytes, err := callgraphAvscBytes()
	if err != nil {
		return nil, err
	}

	info := bindataFileInfo{name: "callgraph.avsc", size: 1924, mode: os.FileMode(0644), modTime: time.Unix(1668278736, 0)}
	a := &asset{bytes: bytes, info: info, digest: [32]uint8{0xd, 0xb1, 0xa9, 0x32, 0x37, 0x7e, 0x56, 0xf2, 0xcf, 0xa8, 0x7e, 0x83, 0xe7, 0x45, 0x31, 0x76, 0x51, 0x98, 0x96, 0x8c, 0x0, 0xf2, 0x98, 0x4f, 0xdb, 0xd4, 0x2f, 0x7d, 0x48, 0x31, 0x50, 0xbb}}
	return a, nil
}

// Asset loads and returns the asset for the given name.
// It returns an error if the asset could not be found or
// could not be loaded.
func Asset(name string) ([]byte, error) {
	canonicalName := strings.Replace(name, "\\", "/", -1)
	if f, ok := _bindata[canonicalName]; ok {
		a, err := f()
		if err != nil {
			return nil, fmt.Errorf("Asset %s can't read by error: %v", name, err)
		}
		return a.bytes, nil
	}
	return nil, fmt.Errorf("Asset %s not found", name)
}

// AssetString returns the asset contents as a string (instead of a []byte).
func AssetString(name string) (string, error) {
	data, err := Asset(name)
	return string(data), err
}

// MustAsset is like Asset but panics when Asset would return an error.
// It simplifies safe initialization of global variables.
func MustAsset(name string) []byte {
	a, err := Asset(name)
	if err != nil {
		panic("asset: Asset(" + name + "): " + err.Error())
	}

	return a
}

// MustAssetString is like AssetString but panics when Asset would return an
// error. It simplifies safe initialization of global variables.
func MustAssetString(name string) string {
	return string(MustAsset(name))
}

// AssetInfo loads and returns the asset info for the given name.
// It returns an error if the asset could not be found or
// could not be loaded.
func AssetInfo(name string) (os.FileInfo, error) {
	canonicalName := strings.Replace(name, "\\", "/", -1)
	if f, ok := _bindata[canonicalName]; ok {
		a, err := f()
		if err != nil {
			return nil, fmt.Errorf("AssetInfo %s can't read by error: %v", name, err)
		}
		return a.info, nil
	}
	return nil, fmt.Errorf("AssetInfo %s not found", name)
}

// AssetDigest returns the digest of the file with the given name. It returns an
// error if the asset could not be found or the digest could not be loaded.
func AssetDigest(name string) ([sha256.Size]byte, error) {
	canonicalName := strings.Replace(name, "\\", "/", -1)
	if f, ok := _bindata[canonicalName]; ok {
		a, err := f()
		if err != nil {
			return [sha256.Size]byte{}, fmt.Errorf("AssetDigest %s can't read by error: %v", name, err)
		}
		return a.digest, nil
	}
	return [sha256.Size]byte{}, fmt.Errorf("AssetDigest %s not found", name)
}

// Digests returns a map of all known files and their checksums.
func Digests() (map[string][sha256.Size]byte, error) {
	mp := make(map[string][sha256.Size]byte, len(_bindata))
	for name := range _bindata {
		a, err := _bindata[name]()
		if err != nil {
			return nil, err
		}
		mp[name] = a.digest
	}
	return mp, nil
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
var _bindata = map[string]func() (*asset, error){
	"callgraph.avsc": callgraphAvsc,
}

// AssetDebug is true if the assets were built with the debug flag enabled.
const AssetDebug = false

// AssetDir returns the file names below a certain
// directory embedded in the file by go-bindata.
// For example if you run go-bindata on data/... and data contains the
// following hierarchy:
//
//	data/
//	  foo.txt
//	  img/
//	    a.png
//	    b.png
//
// then AssetDir("data") would return []string{"foo.txt", "img"},
// AssetDir("data/img") would return []string{"a.png", "b.png"},
// AssetDir("foo.txt") and AssetDir("notexist") would return an error, and
// AssetDir("") will return []string{"data"}.
func AssetDir(name string) ([]string, error) {
	node := _bintree
	if len(name) != 0 {
		canonicalName := strings.Replace(name, "\\", "/", -1)
		pathList := strings.Split(canonicalName, "/")
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
	for childName := range node.Children {
		rv = append(rv, childName)
	}
	return rv, nil
}

type bintree struct {
	Func     func() (*asset, error)
	Children map[string]*bintree
}

var _bintree = &bintree{nil, map[string]*bintree{
	"callgraph.avsc": {callgraphAvsc, map[string]*bintree{}},
}}

func _filePath(dir, name string) string {
	canonicalName := strings.Replace(name, "\\", "/", -1)
	return filepath.Join(append([]string{dir}, strings.Split(canonicalName, "/")...)...)
}
