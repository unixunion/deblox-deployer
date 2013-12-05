package com.deblox.deployer.test.integration.java;

import org.vertx.java.platform.Verticle;
import org.vertx.testtools.VertxAssert;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonObject;

public class SomeVerticle extends Verticle {

  private Handler<Message<JsonObject>> myHandler;
  

  public void start() {
  	EventBus eb = vertx.eventBus();
    VertxAssert.initialize(vertx);

    // Undeploy Handler
    myHandler = new Handler<Message<JsonObject>>() {
      public void handle(Message<JsonObject> message) {
        System.out.println(message.body());
      }
    };

    eb.registerHandler("deblox.deployer.audit", myHandler);
    VertxAssert.testComplete();
    
  }
}
