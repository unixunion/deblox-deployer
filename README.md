# deblox.Deployer

A simple vertx module which subscribes to deploy / undeploy events. It will download modules from your configured m2 repository and pass configuration from the message to the module. 

## Features

* Segementable clusters on name / passcode e.g: dev / prod
* Clusterwide deployment of modules
* Clusterwide and node auditing of modules
* Reporting of deployment events to EventBus

## Overview

Deployer is supposed to be a very simple module deployment mechanism for Vertx modules, The idea is to use deployer to roll out your more complex modules like cattle, and cull them like cattle if need be. All modules should be designed with that in mind! 

Upon startup, Deployer uses the configured `address` to seed the following queues which it will subscriber to, defaults are:

* deblox.deploy
* deblox.undeploy
* deblox.audit

All events are published to the following reporting queue:
* deblox.reports

Any message from the bus which containes a reply-address will be sent the result of the command.

## Blueprint

![Blueprint](https://raw.github.com/unixunion/deblox-deployer/master/deployer-schematic.png)

## Dependancies
* Vertx.IO 2.1RC3
* VERTX_HOME set to where you unzipped vertx.io eg: /opt/vertx

You will want to add the VERTX_HOME/libs path to your project classpath in IntelliJ or similar.


## Building
deblox.Deployer is built with gradle. see gradlew tasks

```
./gradlew build
```

## Testing
Tests the deploy / undeploy and audit methods.

```
./gradlew test
```

If you have issues downloading dependencies like mod-auth-mgr, check your resources/repos.txt or VERTX_HOME/conf/repos.txt

You can run a simple script against the clustered message queue `vertx run src/test/java/com/deblox/deployer/test/integration/examples/Simpledeploy.java -cluster`

## Running a small Cluster
* copy resources/cluster.xml to VERTX_HOME/conf
* copy resources/logging.properties to VERTX_HOME/conf

Choose one of the methods below and run in two or more terminal windows.

runZip

```
vertx runzip build/libs/deployer-1.0.0-final.zip -cluster
```

runMod can be used if you installed the package to your local ~/.m2 repo

```
vertx runmod com.deblox~deployer~1.0.0-final -cluster -conf conf.json
```

The result should be something like this

```
keghol #> vertx runzip build/libs/deployer-1.0.0-final.zip -cluster
Starting clustering... 
[127.0.0.1]:5701 [dev] 

Members [2] {
	Member [127.0.0.1]:5703
	Member [127.0.0.1]:5701 this
}
 
[127.0.0.1]:5701 [dev] Address[127.0.0.1]:5701 is STARTED 
Succeeded in deploying module from zip 

```


## Clustering
All clustering is done on the loopback interfaces if you use the config from src/main/resources. Make sure cluster.xml is in the VERTX_HOME/conf directory!

## Logging
VertX uses JUL, so place the resources/logging.properties file in VERTX_HOME/conf.

## Configuration
deblox.Deployer only needs to know the prefix or address-space to subscribe to. Default is `deblox`

```
// Example Deployer config file conf.json
// passed to Deployer via vertx command line flag -conf

{
  "address": "mycluster"
}
```

The resulting subscription endpoints would be:

* mycluster.deploy
* mycluster.undeploy
* mycluster.audit
* mycluster.reports

Deployer uses vertx's default module search mechanisms, which search maven and maven like repos for modules. see `repos.txt` in the resources directory.

## Messaging API
Requests are processed off the various deblox.* queues. Messages are in JSON format. example:

```
// JAVA
JsonObject jo = new JsonObject()
                    .putString("moduleName", "mod-auth-mgr")
                    .putString("moduleVersion", "2.1.0-SNAPSHOT")
                    .putString("moduleOwner", "io.vertx")
                    .putObject("moduleConfig", new JsonObject());
vertx.eventBus().send("deblox.deployer.deploy", jo, myHandler);
```

Requests can be 'sent' in which case ONLY one node in the cluster will process that message, OR they can be 'published' to the entire cluster in which case ALL nodes will recieve the message.

All nodes report any event results back to the reports queue regardless if they are sent/published.

### Deployment Requests

#### Request
A deployment request must be sent to the deblox.deploy queue. The message MUST contain the following attributes:

* moduleConfig: {}
* moduleName: String
* moduleOwner: String
* moduleVersion: String
* xgrade: bool

Example:

```
{
    "moduleConfig": {
        "someconfig": "someval"
    }, 
    "moduleName": "mod-auth-mgr", 
    "moduleOwner": "io.vertx", 
    "moduleVersion": "2.1.0-SNAPSHOT",
    "xgrade": false
}
```

#### Crossgrade to Request Upgrade/Downgrade/Redeploy/Undeploy skipping version checks
*xgrade* tells Deployer to do downgrades / upgrades and redeploys. Deployer's default behavior is to reject deployment requests for any module which is already deployed, regardless of `moduleVersion`

In order to do upgrades or downgrades without specifically asking instances to undeploy specified versions of modules, set *xgrade* to true. This tells the instance to undeploy any version of the module it has deployed and deploy the specified version.

```
{
    "moduleConfig": {
        "someconfig": "someval"
    }, 
    "moduleName": "mod-auth-mgr", 
    "moduleOwner": "io.vertx", 
    "moduleVersion": "2.1.0-SNAPSHOT",
    "xgrade": true
}
```

#### Response Error - Missing Info
```
{
    "message": "moduleName must be specified", 
    "status": "error"
}
```

#### Response Error - Exception during Deployment
```
{
    "message": "org.vertx.java.platform.PlatformManagerException: Module com.deblox~foo~1.0.0-final not found in any repositories", 
    "status": "error"
}
```

#### Response Error - Already deployed a version
```
{
    "message":"already-deployed version: 2.1.0-SNAPSHOT",
    "status":"error",
}
```

#### Response Success
```
{
    "message": "deployment-c160f1da-e12b-4b50-812d-5018293baa15", 
    "status": "ok"
}
```

#### Report - Exception during Deployment
```
{
    "action": "deploy", 
    "detail": "org.vertx.java.platform.PlatformManagerException: Module com.deblox~foo~1.0.0-final not found in any repositories", 
    "module": "foo", 
    "status": "error",
    "xgrade": true
}
```

#### Report - Already have a version deployed
```
{
    "action": "deploy", 
    "detail": "already-deployed version: 2.1.0-SNAPSHOT", 
    "module": "io.vertx~mod-auth-mgr~2.1.0-SNAPSHOT", 
    "status": "error",
    "xgrade": false    
}
```

#### Report - Success
```
{
    "action": "deploy", 
    "detail": {
        "config": {
            "someconfig": "someval"
        }, 
        "deploymentID": "deployment-7a578e95-3a4d-4095-b52e-6c2c9eb8319c"
    }, 
    "module": "mod-auth-mgr", 
    "status": "ok",
    "xgrade": false
}
```

### Undeploy
Undeploy messages are sent to deblox.undeploy and look identical to deploy messages, though the moduleConfig node can be skipped since it is ignored.

#### Request
```
{
    "moduleName": "mod-auth-mgr", 
    "moduleOwner": "io.vertx", 
    "moduleVersion": "2.1.0-SNAPSHOT"
}
```

#### Response - Success
```
{
    "status":"ok"
}
```

#### Report - Success
```
{
    "action": "undeploy", 
    "detail": "Undeployed io.vertx~mod-auth-mgr~2.1.0-SNAPSHOT", 
    "module": "io.vertx~mod-auth-mgr~2.1.0-SNAPSHOT", 
    "status": "ok"
}
```

#### Response - No such module
```
{
    "message": "no such module", 
    "status": "error"
}
```

#### Report - No such module in deploymentMap
```
{
    "action": "undeploy", 
    "detail": "no such module deployed: com.deblox~foo~1.0.0-final", 
    "module": "com.deblox~foo~1.0.0-final", 
    "status": "error"
}
```

### Audit
Audit triggers the container to report on its deployed modules back to the requestor and publish to the reporting bus.

#### Request
```
{
    "action":"audit"
}
```

#### Response
```
{
    "action": "report", 
    "modules": {
        "name": "io.vertx~mod-auth-mgr~2.1.0-SNAPSHOT"
    }, 
    "status": "ok"
}
```

#### Report
```
{
    "action": "report", 
    "modules": {
        "name": "io.vertx~mod-auth-mgr~2.1.0-SNAPSHOT"
    }
}
```


