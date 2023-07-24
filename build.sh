#!/bin/bash
#mvn clean
#sleep 2
mvn package
jar --list -f target/jaxt-1.0.jar | grep jaxt/
