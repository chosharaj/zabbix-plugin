/*
 * Copyright (c) 2015-2016 Fraunhofer FOKUS
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openbaton.monitoring.agent;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mashape.unirest.http.HttpMethod;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.openbaton.exceptions.MonitoringException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Created by mob on 18.11.15. */
public class ZabbixSender implements RestSender {

  private Logger log = LoggerFactory.getLogger(this.getClass());
  private Gson mapper = new GsonBuilder().setPrettyPrinting().create();
  private String TOKEN;
  private String zabbixHost;
  private String zabbixPort;
  private String zabbixURL;
  private String username;
  private String password;
  protected boolean isAvailable;
  private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);

  public ZabbixSender(
      String zabbixHost, String zabbixPort, Boolean zabbixSsl, String username, String password) {
    this.zabbixHost = zabbixHost;
    this.username = username;
    this.password = password;
    String protocol = zabbixSsl ? "https://" : "http://";

    if (zabbixPort == null || zabbixPort.equals("")) {
      zabbixURL = protocol + zabbixHost + "/zabbix/api_jsonrpc.php";
    } else {
      zabbixURL = protocol + zabbixHost + ":" + zabbixPort + "/zabbix/api_jsonrpc.php";
      this.zabbixPort = zabbixPort;
    }
  }

  @Override
  public synchronized HttpResponse<String> doRestCallWithJson(
      String url, String json, HttpMethod method, String contentType) throws UnirestException {
    HttpResponse<String> response = null;
    switch (method) {
      case PUT:
        response =
            Unirest.put(url)
                .header("Content-type", contentType)
                .header("KeepAliveTimeout", "5000")
                .body(json)
                .asString();
        break;
      case POST:
        response =
            Unirest.post(url)
                .header("Content-type", contentType)
                .header("KeepAliveTimeout", "5000")
                .body(json)
                .asString();
        break;
      case GET:
        response = Unirest.get(url).asString();
        break;
    }
    return response;
  }

  public JsonObject callPost(String content, String method) throws MonitoringException {
    if (!isAvailable) throw new MonitoringException("Zabbix Server is not reachable");
    HttpResponse<String> jsonResponse = null;

    String body = prepareJson(content, method);
    try {
      jsonResponse = doRestCallWithJson(zabbixURL, body, HttpMethod.POST, "application/json-rpc");
      if (checkAuthorization(jsonResponse.getBody())) {
        this.TOKEN = null;
        /*
         * authenticate again, because the last token is expired
         */
        authenticate(zabbixHost, username, password);
        body = prepareJson(content, method);
        jsonResponse = doRestCallWithJson(zabbixURL, body, HttpMethod.POST, "application/json-rpc");
      }
      //log.debug("Response received: " + jsonResponse);
    } catch (UnirestException e) {
      log.error("Post on the Zabbix server failed", e);
      throw new MonitoringException(e.getMessage(), e);
    }

    JsonElement responseEl = null;
    try {
      responseEl = mapper.fromJson(jsonResponse.getBody(), JsonElement.class);
    } catch (Exception e) {
      log.error("Could not map the Zabbix server's response to JsonElement", e);
      throw new MonitoringException("Could not map the Zabbix server's response to JsonElement", e);
    }
    if (responseEl == null || !responseEl.isJsonObject())
      throw new MonitoringException(
          "The json received from Zabbix Server is not a JsonObject or null");
    JsonObject responseObj = responseEl.getAsJsonObject();

    if (responseObj.get("error") != null) {
      JsonObject errorObj = (JsonObject) responseObj.get("error");
      throw new MonitoringException(
          errorObj.get("message").getAsString() + " " + errorObj.get("data").getAsString());
    }
    return responseObj;
  }

  private String prepareJson(String content, String method) {

    String s = "{'params': " + content + "}";

    JsonObject jsonContent = mapper.fromJson(s, JsonObject.class);
    JsonObject jsonObject = jsonContent.getAsJsonObject();

    jsonObject.addProperty("jsonrpc", "2.0");
    jsonObject.addProperty("method", method);

    if (TOKEN != null) jsonObject.addProperty("auth", TOKEN);
    jsonObject.addProperty("id", 1);

    //log.debug("Json for zabbix:\n" + mapper.toJson(jsonObject));
    return mapper.toJson(jsonObject);
  }

  private boolean checkAuthorization(String body) {
    boolean isAuthorized = false;
    JsonElement error;
    JsonElement data = null;
    JsonObject responseOb;

    responseOb = mapper.fromJson(body, JsonObject.class);

    if (responseOb == null) {
      return isAuthorized;
    }

    //log.debug("Check authorization in this response:" + responseOb);

    error = responseOb.get("error");
    if (error == null) {
      return isAuthorized;
    }
    //log.debug("AUTHENTICATION ERROR  ----->   "+error + " ---> Retrying");

    if (error.isJsonObject()) data = ((JsonObject) error).get("data");
    if (data.getAsString().equals("Not authorised")) {
      isAuthorized = true;
      return isAuthorized;
    }

    return false;
  }

  public void authenticate(String zabbixHost, String username, String password)
      throws MonitoringException {
    this.zabbixHost = zabbixHost;
    this.username = username;
    this.password = password;
    this.authenticateToZabbix();
  }

  private void startWatcher() {
    Whatcher whatcher = new Whatcher();
    executorService.scheduleAtFixedRate(whatcher, 0, 5, TimeUnit.SECONDS);
  }

  public void destroy() {
    executorService.shutdown();
  }

  public void authenticate() {
    startWatcher();
  }

  protected void authenticateToZabbix() throws MonitoringException {
    String params = "{'user':'" + username + "','password':'" + password + "'}";

    JsonObject responseObj = callPost(params, "user.login");
    JsonElement result = responseObj.get("result");
    if (result == null) {
      throw new MonitoringException("problem during the authentication");
    }
    this.TOKEN = result.getAsString();

    log.debug("Authenticated to Zabbix Server " + zabbixURL + " with TOKEN " + TOKEN);
  }

  private class Whatcher implements Runnable {
    @Override
    public void run() {
      String jsonRequest =
          "{\"jsonrpc\":\"2.0\",\"method\":\"apiinfo.version\",\"id\":1,\"auth\":null,\"params\":{}}";
      String zabbixVersion = null;
      try {
        if (!isAvailable) {
          log.info("");
          log.info("Trying to connect to Zabbix at url: " + zabbixURL + "...");
        }
        HttpResponse<String> response =
            doRestCallWithJson(zabbixURL, jsonRequest, HttpMethod.POST, "application/json-rpc");
        JsonElement responseEl = mapper.fromJson(response.getBody(), JsonElement.class);
        JsonObject responseObj = responseEl.getAsJsonObject();
        zabbixVersion = responseObj.get("result").getAsString();
      } catch (Exception e) {
        log.error("Zabbix Server not reachable at this url: " + zabbixURL);
        log.debug(e.getMessage());
        isAvailable = false;
        return;
      }
      if (!isAvailable) {
        isAvailable = true;
        log.info("Connected to Zabbix " + zabbixVersion + " at url: " + zabbixURL);
        log.debug("trying autentication..");
        try {
          authenticateToZabbix();
        } catch (Exception e) {
          log.error(e.getMessage());
          isAvailable = false;
          return;
        }
      }
      isAvailable = true;
    }
  }
}
