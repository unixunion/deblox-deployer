package com.deblox.deployer.test.integration.java;/*
 * Copyright 2013 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */

import org.junit.Test;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.testtools.TestVerticle;
import org.vertx.testtools.VertxAssert;
import org.vertx.java.core.json.JsonObject;

import static org.vertx.testtools.VertxAssert.*;

/**
 * Example Java integration test that deploys the module that this project builds.
 *
 * Quite often in integration tests you want to deploy the same module for all tests and you don't want tests
 * to start before the module has been deployed.
 *
 * This test demonstrates how to do that.
 */
public class ModuleIntegrationTest extends TestVerticle {

  @Test
  public void testDeployBadCommand() {
    container.logger().info("in testDeployBadCommand()");
    vertx.eventBus().send("deblox.deployer.deploy", new JsonObject().putString("hi", "there"), new Handler<Message<JsonObject>>() {
      @Override
      public void handle(Message<JsonObject> reply) {
        System.out.println("Response: " + reply.body());
        assertEquals("error", reply.body().getString("status"));

        /*
        If we get here, the test is complete
        You must always call `testComplete()` at the end. Remember that testing is *asynchronous* so
        we cannot assume the test is complete by the time the test method has finished executing like
        in standard synchronous tests
        */
        testComplete();
      }
    });
  }

 @Test
 public void testDeployNonExistentModule() {
    container.logger().info("in testDeployNonExistentModule()");

    JsonObject jo = new JsonObject()
                        .putString("moduleName", "foo")
                        .putString("moduleVersion", "1.0.0-final")
                        .putString("moduleOwner", "com.deblox")
                        .putObject("moduleConfig", new JsonObject().putString("someconfig", "someval"));

    vertx.eventBus().send("deblox.deployer.deploy", jo, new Handler<Message<JsonObject>>() {
      @Override
      public void handle(Message<JsonObject> reply) {
        System.out.println("Response: " + reply.body());
        assertEquals("error", reply.body().getString("status"));
        testComplete();
      }
    });
  }

  @Test
  public void testUndeployNonExistentModule() {
    container.logger().info("in testUndeployNonExistentModule()");

    JsonObject jo = new JsonObject()
                        .putString("moduleName", "foo")
                        .putString("moduleVersion", "1.0.0-final")
                        .putString("moduleOwner", "com.deblox");

    vertx.eventBus().send("deblox.deployer.undeploy", jo, new Handler<Message<JsonObject>>() {
      @Override
      public void handle(Message<JsonObject> reply) {
        System.out.println("Response: " + reply.body());
        assertEquals("error", reply.body().getString("status"));
        testComplete();
      }
    });
  }


  @Test
  public void testUndeployNonExistentModuleNoReturn() {
    container.logger().info("in testUndeployNonExistentModuleNoReturn()");

    JsonObject jo = new JsonObject()
                        .putString("moduleName", "foo")
                        .putString("moduleVersion", "1.0.0-final")
                        .putString("moduleOwner", "com.deblox");

    vertx.eventBus().publish("deblox.deployer.undeploy", jo);
    testComplete();
  }

  @Test
  public void testUndeployModuleNoReturn() {
    container.logger().info("in testUndeployModuleNoReturn()");

    JsonObject jo = new JsonObject()
                        .putString("moduleName", "foo")
                        .putString("moduleVersion", "1.0.0-final")
                        .putString("moduleOwner", "com.deblox")
                        .putObject("moduleConfig", new JsonObject());

    vertx.eventBus().publish("deblox.deployer.deploy", jo);
    testComplete();
  }


  @Test
  public void testDeployModule() {
    container.logger().info("in testDeployModule()");

    JsonObject jo = new JsonObject()
                        .putString("moduleName", "mod-auth-mgr")
                        .putString("moduleVersion", "2.0.0-final")
                        .putString("moduleOwner", "io.vertx")
                        .putObject("moduleConfig", new JsonObject().putString("someconfig", "someval"));

    vertx.eventBus().send("deblox.deployer.deploy", jo, new Handler<Message<JsonObject>>() {
      @Override
      public void handle(Message<JsonObject> reply) {
        System.out.println("Response: " + reply.body());
        assertEquals("ok", reply.body().getString("status"));
        testComplete();
      }
    });
  }

  @Test
  public void testDeployModuleDuplicate() {
    container.logger().info("in testDeployModule()");

    final JsonObject jo = new JsonObject()
                        .putString("moduleName", "mod-auth-mgr")
                        .putString("moduleVersion", "2.0.0-final")
                        .putString("moduleOwner", "io.vertx")
                        .putObject("moduleConfig", new JsonObject().putString("someconfig", "someval"));

    vertx.eventBus().send("deblox.deployer.deploy", jo, new Handler<Message<JsonObject>>() {
      @Override
      public void handle(Message<JsonObject> reply) {
        System.out.println("Response: " + reply.body());
        assertEquals("ok", reply.body().getString("status"));
        vertx.eventBus().send("deblox.deployer.deploy", jo, new Handler<Message<JsonObject>>() {
          @Override
          public void handle(Message<JsonObject> reply) {
            System.out.println("Response: " + reply.body());
            assertEquals("error", reply.body().getString("status"));
            testComplete();
          }
        });
      }
    });
  }

  @Test
  public void testDeployModuleXgrade() {
    container.logger().info("in testDeployModule()");

    final JsonObject jo = new JsonObject()
                        .putString("moduleName", "mod-auth-mgr")
                        .putString("moduleVersion", "2.1.0-SNAPSHOT")
                        .putString("moduleOwner", "io.vertx")
                        .putBoolean("xgrade", true)
                        .putObject("moduleConfig", new JsonObject().putString("someconfig", "someval"));

    vertx.eventBus().send("deblox.deployer.deploy", jo, new Handler<Message<JsonObject>>() {
      @Override
      public void handle(Message<JsonObject> reply) {
        System.out.println("Response: " + reply.body());
        assertEquals("ok", reply.body().getString("status"));
        vertx.eventBus().send("deblox.deployer.deploy", jo, new Handler<Message<JsonObject>>() {
          @Override
          public void handle(Message<JsonObject> reply) {
            System.out.println("Response: " + reply.body());
            assertEquals("ok", reply.body().getString("status"));
            testComplete();
          }
        });
      }
    });
  }


  @Test
  public void testUndeployModule() {
    container.logger().info("in testDeployModule()");

    final JsonObject jo = new JsonObject()
                        .putString("moduleName", "mod-auth-mgr")
                        .putString("moduleVersion", "2.1.0-SNAPSHOT")
                        .putString("moduleOwner", "io.vertx")
                        .putObject("moduleConfig", new JsonObject());

    vertx.eventBus().send("deblox.deployer.deploy", jo, new Handler<Message<JsonObject>>() {
      @Override
      public void handle(Message<JsonObject> reply) {
        // after deploy success, lets do a undeploy
        System.out.println("Response: " + reply.body());
        assertEquals("ok", reply.body().getString("status"));

        vertx.eventBus().send("deblox.deployer.undeploy", jo, new Handler<Message<JsonObject>>() {
          @Override
          public void handle(Message<JsonObject> reply) {
            System.out.println("Response: " + reply.body());
            assertEquals("ok", reply.body().getString("status"));
            testComplete();
          }
        });

      }
    });

  }

  @Test
  public void testDeployModuleAudit() {
    container.logger().info("in testDeployModuleAudit()");

    final JsonObject jo = new JsonObject()
                    .putString("moduleName", "mod-auth-mgr")
                    .putString("moduleVersion", "2.1.0-SNAPSHOT")
                    .putString("moduleOwner", "io.vertx")
                    .putObject("moduleConfig", new JsonObject());

    vertx.eventBus().send("deblox.deployer.deploy", jo, new Handler<Message<JsonObject>>() {
      @Override
      public void handle(Message<JsonObject> reply) {
        System.out.println("Response: " + reply.body());
        assertEquals("ok", reply.body().getString("status"));
          
        vertx.eventBus().send("deblox.deployer.audit", new JsonObject().putString("action", "audit"), new Handler<Message<JsonObject>>() {
          @Override
          public void handle(Message<JsonObject> reply) {
            System.out.println("Response: " + reply.body());
            assertEquals("ok", reply.body().getString("status"));
            testComplete();
          }
        });
      }
    });
  }


  @Override
  public void start() {
    // Make sure we call initialize() - this sets up the assert stuff so assert functionality works correctly
    initialize();
    // Deploy the module - the System property `vertx.modulename` will contain the name of the module so you
    // don't have to hardecode it in your tests
    container.deployModule(System.getProperty("vertx.modulename"), new AsyncResultHandler<String>() {
      @Override
      public void handle(AsyncResult<String> asyncResult) {
      // Deployment is asynchronous and this this handler will be called when it's complete (or failed)
      if (asyncResult.failed()) {
        container.logger().error(asyncResult.cause());
      }
      assertTrue(asyncResult.succeeded());
      assertNotNull("deploymentID should not be null", asyncResult.result());

      // Setup a handler to receive reports from 
      Handler<Message<JsonObject>> myHandler = new Handler<Message<JsonObject>>() {
      public void handle(Message<JsonObject> message) {
        System.out.println("Report received: " + message.body());
        }
      };

      vertx.eventBus().registerHandler("deblox.deployer.reports", myHandler);

      // If deployed correctly then start the tests!
      startTests();
      }
    });
  }

}
