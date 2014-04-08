package com.deblox.deployer.test.integration.examples;

import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.platform.Verticle;

public class Simpledeploy extends Verticle {

	public void start() {
//        long timerID = vertx.setPeriodic(1000, new Handler<Long>() {
//            public void handle(Long timerID) {
//                System.out.println("Deploying");
//                deploy();
//            }
//        });

        // Setup a handler to receive reports from
        Handler<Message<JsonObject>> myHandler = new Handler<Message<JsonObject>>() {
            public void handle(Message<JsonObject> message) {
                System.out.println("Report received: " + message.body());
            }
        };

        vertx.eventBus().registerHandler("deblox.deployer.reports", myHandler);


        // after 100ms issue a undeploy event
        long timerID1 = vertx.setTimer(100, new Handler<Long>() {
            public void handle(Long timerID) {
                undeploy();
            }
        });

        // after 1000ms issue a deploy event
        long timerID2 = vertx.setTimer(1000, new Handler<Long>() {
            public void handle(Long timerID) {
                deploy();
            }
        });


	}

    public void undeploy() {
        JsonObject jo = new JsonObject()
                .putString("moduleName", "mod-auth-mgr")
                .putString("moduleVersion", "2.0.0-final")
                .putString("moduleOwner", "io.vertx");

        vertx.eventBus().publish("deblox.deployer.undeploy", jo);
    }

    public void deploy() {
        JsonObject jo = new JsonObject()
                .putString("moduleName", "mod-auth-mgr")
                .putString("moduleVersion", "2.0.0-final")
                .putString("moduleOwner", "io.vertx")
                .putObject("moduleConfig", new JsonObject().putString("someconfig", "someval"));

        vertx.eventBus().publish("deblox.deployer.deploy", jo);
    }
}

//vertx.eventBus().send("deblox.deployer.deploy", jo, new Handler<Message<JsonObject>>() {
//@Override
//public void handle(Message<JsonObject> reply) {
//        System.out.println("Response: " + reply.body());
//        }
//        });
