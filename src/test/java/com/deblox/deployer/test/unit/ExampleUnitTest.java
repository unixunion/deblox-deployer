package com.deblox.deployer.test.unit;

import com.deblox.deployer.Deployer;
import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.*;

/*
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
public class ExampleUnitTest {

  @Test
  public void testVerticle() {
    Deployer vert = new Deployer();

    // Interrogate your classes directly....

    assertNotNull(vert);
  }
}
