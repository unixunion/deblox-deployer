# deblox.Deployer

A simple vertx module which subscribes to deploy / undeploy events. It will download modules from your configured m2 repository and pass configuration from the message to the module. 

## Features

* Encrypted cluster communication
* Segementable clusters on name / passcode e.g: dev / prod
* Clusterwide deployment of modules
* Clusterwide and node auditing of modules
* Reporting of deployment events to EventBus

## Overview

Deployer is supposed to be a very simple module deployment mechanism for Vertx modules, The idea is to use deployer to roll out your more complex modules like cattle, and cull them like cattle if need be. All modules should be designed with that in mind! 

Upon startup, Deployer uses the configured `address` to seed the following queues which it will subscriber to:

* `address`.deploy
* `address`.undeploy
* `address`.audit

All events are published to the following reporting queue:
* `address`.reports

Any message from the bus which containes a reply-address will be sent the result of the command.

## Building
deblox.Deployer is built with gradle. see tasks with:

```
./gradlew tasks
```

## Configuration
deblox.Deployer only needs to know the prefix or address-space to subscribe to. Default is `deblox.deployer`

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

Deployer uses vertx's default module searche mechanism, which searches maven repos for modules. see `repos.txt` in the resources directory.

### Hazelcast
See Hazelcast's documentation on configuring your cluster. Make sure your cluster.xml is placed within $VERTX_HOME/conf eg: `/opt/vertx-2.0.0-final/conf`

### Running
See the VertX manual on running modules. Normally something down the line of:

```
vertx runmod com.deblox~deployer~1.0.0-final -cluster -conf conf.json
```

## Messages
Deployment requests are processed off the `address`.deploy queue. In order deploy a module from a Maven repository, simply send a message like:

```
JsonObject jo = new JsonObject()
                    .putString("moduleName", "mod-auth-mgr")
                    .putString("moduleVersion", "2.1.0-SNAPSHOT")
                    .putString("moduleOwner", "io.vertx")
                    .putObject("moduleConfig", new JsonObject());
vertx.eventBus().send("deblox.deployer.deploy", jo, myHandler);
```


All deployment requests are replied to if they were sent instead of published to the queue. All deploy / undeploy results are published to the `address`.reports queue.

### Deployment Requests

#### Request
A deployment request must be sent to the `address`.deploy queue. The message MUST contain the following attributes:

* moduleConfig: {}
* moduleName: String
* moduleOwner: String
* moduleVersion: String
* xgrade: bool

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

#### Request Upgrade/Downgrade/Redeploy - xgrade
`xgrade` tells Deployer to do downgrades / upgrades and redeploys. Deployer's default behavior is to reject deployment requests for any module which is already deployed, regardless of `moduleVersion`

In order to do upgrades or downgrades without specifically asking instances to undeploy specified versions of modules, set `xgrade` to true. This tells the instance to undeploy any version of the module it has deployed and deploy the specified version.

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
Undeploy messages are sent to `address`.undeploy and look identical to deploy messages, though the moduleConfig node can be skipped since it is ignored.

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


