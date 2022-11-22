// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package avro

import (
	"fmt"
	"github.com/linkedin/goavro/v2"
	"github.com/pkg/errors"

	cg "github.com/harness/harness-core/product/ci/common/avro/schema/callgraph"
	cgold "github.com/harness/harness-core/product/ci/common/avro/schema/callgraph_old"
)

//Serialzer is the interface for encoding and decoding structs
type Serialzer interface {
	// Serialize given struct and return the binary value
	Serialize(datum interface{}) ([]byte, error)
	// Deserialize given struct and return the decoded interface{}
	Deserialize(buf []byte) (interface{}, error)
}

// CgphSerialzer struct implementing NewCgphSer interface
type CgphSerialzer struct {
	codec *goavro.Codec
}

const (
	cgType    = "callgraph"
	vgType    = "visgraph"
	cgSrcFile = "callgraph.avsc"
	vgSrcFile = "visgraph.avsc"
)

// NewCgphSerialzer returns new CgphSerialzer object with the codec
// based on the schema received in the input
func NewCgphSerialzer(typ string, useOld bool) (*CgphSerialzer, error) {
	var schema []byte
	var err error
	switch typ {
	case cgType:
		if !useOld {
			schema, err = cg.Asset(cgSrcFile)
		} else {
			schema, err = cgold.Asset(cgSrcFile)
		}
	default:
		return nil, fmt.Errorf("type %s is not supported", typ)
	}
	if err != nil {
		return nil, errors.Wrap(err, "failed to read schema file")
	}

	codec, err := goavro.NewCodec(string(schema))
	if err != nil {
		panic(err)
	}

	return &CgphSerialzer{
		codec: codec,
	}, nil
}

//Serialize a given struct interface and return byte array and error
func (c *CgphSerialzer) Serialize(datum interface{}) ([]byte, error) {
	bin, err := c.codec.BinaryFromNative(nil, datum)
	if err != nil {
		return nil, errors.Wrap(err, "failed to encode the data")
	}
	return bin, nil
}

//Deserialize a interface and return a Byte array which can be converted into corresponding struct
func (c *CgphSerialzer) Deserialize(buf []byte) (interface{}, error) {
	native, _, err := c.codec.NativeFromBinary(buf)
	if err != nil {
		return nil, err
	}
	return native, nil
}
