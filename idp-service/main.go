package main

import (
	"idp-service/router"
)

func main() {
	// create a new echo instance
	e := router.New()

	e.Logger.Fatal(e.Start(":3000"))
}
