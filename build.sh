#!/bin/bash
mvn clean
mvn package
jar --list -f target/jaxt-1.0.jar | grep jaxt/
