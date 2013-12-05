# Deblox.Deployer

A simple vertx app which subscribes to module deploy / undeploy events. It will download modules from your configure m2 repository and pass configuration from the message to the module.

## Overview
Upon startup, Deployer subscribes to some queues:

* `address`.deploy
* `address`.undeploy
* `address`.audit

And publishes all results to:
* `address`.reports

If the message containes a reply-address, a response is sent to that address.

## Configuration
Deployer only needs to know the address prefix to subscribe to. Defaults is `deblox.deployer`

```
// Example Deployer config file conf.json
// passed to Deployer via vertx command line flag -conf

{
  "address": "deblox"
}
```

### Hazelcast
See Hazelcast's documentation on configuring your cluster. Make sure your cluster.xml is placed within $VERTX_HOME/conf eg: `/opt/vertx-2.0.0-final/conf`

## Deployment Request
Deployment requests are processed off the `address`.deploy queue. In order to request Deployer to deploy a module from a Maven repository, simply send a message like this:

```
JsonObject jo = new JsonObject()
                    .putString("moduleName", "mod-auth-mgr")
                    .putString("moduleVersion", "2.1.0-SNAPSHOT")
                    .putString("moduleOwner", "io.vertx")
                    .putObject("moduleConfig", new JsonObject());
```

Example dealing with responses:

```
JsonObject jo = new JsonObject()
                    .putString("moduleName", "foo")
                    .putString("moduleVersion", "1.0.0-final")
                    .putString("moduleOwner", "com.deblox")
                    .putObject("moduleConfig", new JsonObject());

vertx.eventBus().send("deblox.deployer.deploy", jo, new Handler<Message<JsonObject>>() {
  @Override
  public void handle(Message<JsonObject> reply) {
    assertEquals("ok", reply.body().getString("status"));
  }
});

```

All deployment requests are replied to if they were sent instead of published to the queue. All deploy / undeploy results are published to the `address`.reports queue.

## Messages

### Deployment

#### Request Deploy

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
#### Request Upgrade/Downgrade - xgrade
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


