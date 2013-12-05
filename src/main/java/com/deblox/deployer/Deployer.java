/*

Copyright 2013 Deblox
by Kegan Holtzhausen

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

 */
package com.deblox.deployer;

import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.busmods.BusModBase;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.platform.PlatformManagerException;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;


/*
 * This is a simple Module which receives `deploy` and `undeploy` messages on the event bus and reacts accordingly.
 */
public class Deployer extends BusModBase {

  private Handler<Message<JsonObject>> deployHandler;
  private Handler<Message<JsonObject>> undeployHandler;
  private Handler<Message<JsonObject>> auditHandler;

  // We hold a map of current known deployments, we can only know of deployments made via ourself. 
  protected final Map<String, DeploymentInfo> deployments = new HashMap<>();

  private String address; // main address space to subscribe to, all other queues based on this.
  private String deployAddress;
  private String undeployAddress;
  private String reportAddress;
  private String auditAddress; // where we listen for interrogations

  private static final class DeploymentInfo {
    final String deploymentID;
    final String moduleName;
    final String moduleOwner;
    final String moduleVersion;
    final JsonObject moduleConfig;

    private DeploymentInfo(String deploymentID, String moduleName, String moduleOwner, String moduleVersion, JsonObject moduleConfig ) {
      this.deploymentID = deploymentID;
      this.moduleName = moduleName;
      this.moduleOwner = moduleOwner;
      this.moduleVersion = moduleVersion;
      this.moduleConfig = moduleConfig;
    }

    // returns a fully qualified module name ( for reporting  only at this stage )
    public String getFullName() {
      return this.moduleOwner + "~" + this.moduleName + "~" + this.moduleVersion;
    }

    }

  public void start() {
    super.start();

    final Logger logger = container.logger();

    this.address = getOptionalStringConfig("address", "deblox.deployer");
    this.deployAddress = address + ".deploy";
    this.undeployAddress = address + ".undeploy";
    this.reportAddress = address + ".reports";
    this.auditAddress = address + ".audit";

    // Deploy Handler
    deployHandler = new Handler<Message<JsonObject>>() {
      public void handle(Message<JsonObject> message) {
        doDeploy(message);
      }
    };
    eb.registerHandler(deployAddress, deployHandler);

    // Undeploy Handler
    undeployHandler = new Handler<Message<JsonObject>>() {
      public void handle(Message<JsonObject> message) {
        doUndeploy(message);
      }
    };
    eb.registerHandler(undeployAddress, undeployHandler);

    // Audit Handler
    auditHandler = new Handler<Message<JsonObject>>() {
      public void handle(Message<JsonObject> message) {
        doAudit(message);
      }
    };
    eb.registerHandler(auditAddress, auditHandler);

  }

  private void doDeploy(final Message<JsonObject> message) {

    //System.out.println("Got Deploy Message: " + message.body());

    // Get mandatory fields from message
    final String moduleName = getMandatoryString("moduleName", message);
    if (moduleName == null) {
      return;
    }

    final String moduleOwner = getMandatoryString("moduleOwner", message);
    if (moduleOwner == null) {
      return;
    }

    final String moduleVersion = getMandatoryString("moduleVersion", message);
    if (moduleVersion == null) {
      return;
    }

    final JsonObject moduleConfig = getMandatoryObject("moduleConfig", message);
    if (moduleConfig == null) {
      return;
    }

    // If upgrade / downgrade is allowed or not, if this is true it WILL undeploy
    // whatever version you are running of the module before deploying the specified one.
    final boolean moduleXgrade = message.body().getBoolean("xgrade", false);


    // modules use a fully qualified name, so lets build that up.
    final String module = moduleOwner + "~" + moduleName + "~" + moduleVersion;

    // Build up a new deploymentInfo object for making things a bit easier for `this` deployment call!
    final DeploymentInfo deploymentInfo = new DeploymentInfo("", moduleName, moduleOwner, moduleVersion, moduleConfig);

    // Check if we have a deployment like this already
    if ( deployments.containsKey(moduleOwner + "~" + moduleName) && !moduleXgrade ) {

      eb.publish(reportAddress, new JsonObject()
                                    .putString("action", "deploy")
                                    .putString("module", module)
                                    .putString("status", "error")
                                    .putBoolean("xgrade", moduleXgrade)
                                    .putString("detail", "already-deployed version: " + deployments.get(moduleOwner + "~" + moduleName).moduleVersion ));

      sendError(message, "already-deployed version: " + deployments.get(moduleOwner + "~" + moduleName).moduleVersion);

    } else {
      if (moduleXgrade) {
        logger.info("Undeploying since Xgrade is true!");

        try {
          container.undeployModule( deployments.get(moduleOwner + "~" + moduleName).deploymentID);
        } catch (NullPointerException e) {
          logger.error("No deployments in this container");
        } catch (PlatformManagerException e) {
          logger.error("Undeploy error, possibly not deployed");
          e.printStackTrace();
        }

      }

      //System.out.println("Attempting deploy of module " + module );
      container.deployModule(module, moduleConfig, new AsyncResultHandler<String>() { 
        public void handle(AsyncResult<String> asyncResult) {
            if (asyncResult.succeeded()) {

                DeploymentInfo deploymentInfoConfig = new DeploymentInfo(asyncResult.result(), moduleName, moduleOwner, moduleVersion, moduleConfig);

                // update the deployments map
                deployments.put(moduleOwner + "~" + moduleName, deploymentInfoConfig);

                JsonObject jsonReply = new JsonObject()
                                                    .putString("module", moduleName)
                                                    .putString("action", "deploy")
                                                    .putString("status", "ok")
                                                    .putBoolean("xgrade", moduleXgrade)
                                                    .putObject("detail", new JsonObject()
                                                        .putString("deploymentID" ,asyncResult.result())
                                                        .putObject("config", moduleConfig));
                eb.publish(reportAddress, jsonReply);
                
                sendOK(message, new JsonObject().putString("message", asyncResult.result()));
                
            } else {

                asyncResult.cause().printStackTrace();

                // Notify the reportBus of this catastrophe
                JsonObject jsonReply = new JsonObject()
                                    .putString("module", moduleName)
                                    .putString("action", "deploy")
                                    .putString("status", "error")
                                    .putBoolean("xgrade", moduleXgrade)
                                    .putString("detail", asyncResult.cause().toString());
                eb.publish(reportAddress, jsonReply);

                // Use BusMod sendError to notify the sender
                sendError(message, asyncResult.cause().toString());
            }

        }
      });
    }
  }

  private void doUndeploy(final Message<JsonObject> message) {

    // Get mandatory fields from message
    final String moduleName = getMandatoryString("moduleName", message);
    if (moduleName == null) {
      return;
    }

    final String moduleOwner = getMandatoryString("moduleOwner", message);
    if (moduleOwner == null) {
      return;
    }

    final String moduleVersion = getMandatoryString("moduleVersion", message);
    if (moduleVersion == null) {
      return;
    }

    // we dont need config for undeploy, so lets just stub it!
    final JsonObject moduleConfig = new JsonObject();

    // modules use a fully qualified name, so lets build that up.
    final String module = moduleOwner + "~" + moduleName + "~" + moduleVersion;

    if ( deployments.containsKey(moduleOwner + "~" + moduleName) ) {
      if ( deployments.get(moduleOwner + "~" + moduleName).moduleVersion.equals(moduleVersion) ) {

        container.undeployModule( deployments.get(moduleOwner + "~" + moduleName).deploymentID, new AsyncResultHandler<Void>() {        
        
        public void handle(AsyncResult<Void> asyncResult) {
          if (asyncResult.succeeded()) { 
            eb.publish(reportAddress, new JsonObject()
                                          .putString("action", "undeploy")
                                          .putString("module", module)
                                          .putString("status", "ok")
                                          .putString("detail", "Undeployed " + module));

            deployments.remove(moduleOwner + "~" + moduleName);
            sendOK(message);
          }
        }

        });

      } else {
  
        eb.publish(reportAddress, new JsonObject()
                                        .putString("action", "undeploy")
                                        .putString("module", module)
                                        .putString("status", "error")
                                        .putString("detail", "A different version is currently deployed"));
        sendError(message, "version mismatch");    
      }

    } else {
      eb.publish(reportAddress, new JsonObject()
                                        .putString("action", "undeploy")
                                        .putString("module", module)
                                        .putString("status", "error")
                                        .putString("detail", "no such module: " + module + " deployed in this container"));
      sendError(message, "no such module: " + module + " deployed in this container");
    }}

  private void doAudit(final Message<JsonObject> message) { 
    // Reply to inqueries as to deployed Modules
    // Perdiodically announce my modules to the cluster
    logger.info("Audit: " + message.body());
    final String action = getMandatoryString("action", message);

    if (!action.equals("audit")) {
      logger.info("bailing out");
      return;
    }

    // Module stack to hold the contents of our deployments in Json format
    JsonObject modulesJson = new JsonObject();

    // Go through the deployments and place the FullName onto the module stack
    for (String key : deployments.keySet()) {
        modulesJson.putString("name", deployments.get(key).getFullName());
    }

    // Build up a new report object
    JsonObject jsonReport = new JsonObject()
                    .putString("action", "report")
                    .putObject("modules", modulesJson);

    eb.publish(reportAddress, jsonReport);

    sendStatus("ok", message, jsonReport);

  }

}
