#!/bin/bash
export PATH=$PATH:/opt/vert.x-2.1M2/bin/
vertx runmod com.deblox~deployer~1.0.0-final -cluster -conf conf.json
