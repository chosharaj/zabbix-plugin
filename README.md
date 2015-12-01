# Zabbix plugin

Zabbix plugin is an open source project providing a reference implementation of two interfaces of the VIM, based on the ETSI [NFV MANO] specification.

The two interfaces are:
-   VirtualisedResourceFaultManagement
-   VirtualisedResourcePerformanceManagement

A detailed description of the interfaces is in the last ETSI Draft [IFA005_Or-Vi_ref_point_Spec].

**Important note**: since the two *detailed* interfaces come from a *Draft*, it is not to be intended as a official standard yet.

## General Description

The Zabbix plugin is an implementation of two interfaces of the VIM, in order to be able to use Zabbix in the NFV architecture.

![Zabbix plugin architecture][zabbix-plugin-architecture]

With the Zabbix plugin you can create/delete items, trigger and action on-demand. But this is what Zabbix server already provide...
So **the utlity of the plugin is to perform these actions using standard ETSI interfaces**.

### VirtualisedResourcePerformanceManagement interface

| Methods             | Description
| ------------------- | --------------
| CREATE PM JOB       |  Create one or more items to be monitored in one or more hosts.
| DELETE PM JOB       |  Delete a PM job.
| QUERY PM JOB        |  Get item values from one or more host. Fast method since the item values are cached.
| SUBSCRIBE           |  Subscribe to a pm job or a threshold in order to be notified.
| NOTIFY              |  Notification method invoked by zabbix plugin, the customer must not invoke directly this method.
| CREATE THRESHOLD    |  Create trigger on a specific item for one or more hosts
| DELETE THRESHOLD    |  Delete a threshold.
| QUERY THRESHOLD     |  Get information about the status of the thresholds

### VirtualisedResourceFaultManagement interface

| Methods             | Description
| ------------------- | --------------
| SUBSCRIBE           |  Subscribe for alarm coming from an host
| NOTIFY              |  Notification method invoked by zabbix plugin, the customer must not invoke directly this method.
| GET ALARM LIST      |  Get alarms and relative status

## Beneficts

1) Make the consumers (NFVO, VNFM) indipendent to the monitoring system.  
2) The communication between the consumers and zabbix-plugin is JSON based, so the customers can be written in any languages.  
3) The values of the items are cached and updated periodically in order to avoid the zabbix server latency.
4) If your consumer is written in java, we provide a simple class MonitoringPluginCaller which handle the communication via RabbitMQ.

## Prerequisites

The prerequisites are:  

- Zabbix server installed and running  
- RabbitMQ server installed and running  
- Git installed
- Gradle installed
- Create a configuration file called zabbix-plugin.conf in the path /etc/openbaton/plugins/ and fill it with the
configuration parameter explained in the following section.

## Configuration

| Parameter           | Description     | Default
| ------------------- | --------------  | ----------
| zabbix-ip                             |  IP of the Zabbix Server      | 
| zabbix-port                           |  Port of the Zabbix Server    |
| type                                  |  The type of the plugin       | zabbix-plugin
| user-zbx                              |  User of the Zabbix Server    | 
| password-zbx                          |  Password of Zabbix Server    |
| client-request-frequency              |  Update cache period (Basically each time t, the zabbix plugin ask to every items value for all hosts and fill the local cache)   | 15 (seconds)
| history-length                        |  How long is the history. If the client-request-frequency is 10 seconds and history-length 100, we have available the value of the items of the previous 1000 seconds. | 250
| notification-receiver-server-context  |  Context where the zabbix-plugin receive the notifications by the zabbix server. (this function will be documented soon) | /zabbixplugin/notifications 
| notification-receiver-server-port     |  Port where the zabbix-plugin receive the notifications by the zabbix server. | 8010
| external-properties-file              |  Full path of the configuration file.  | /etc/openbaton/plugins/zabbix-plugin.conf

The configuration file should look like the one below:

```bash  

zabbix-ip = xxx.xxx.xxx.xxx
zabbix-ip = xxxxx
type = zabbix-plugin
user-zbx = zabbixUSer
password-zbx = zabbixPassword
client-request-frequency = 10
history-length = 250

notification-receiver-server-context = /zabbixplugin/notifications
notification-receiver-server-port = 8010

external-properties-file=/etc/openbaton/plugins/zabbix-plugin.conf

```

## Getting Started

Once the prerequisites are met, you can clone the following project from git, compile it using gradle and launch it:  

```bash  

git clone link_of_zabbix-plugin
cd zabbix-plugin
git checkout develop
./gradlew build -x test
java -jar build/lib/zabbix-agent-<version>.jar

```

## Using it via MonitoringPluginCaller

In order to use the MonitorPluginCaller you need to import the relative plugin-sdk, coming from [Openbaton][openbaton-website] project.
To import the plugin-sdk, please add in your gradle file the following dependencies:

```
repositories {
       maven { url "http://get.openbaton.org:8081/nexus/content/groups/public/" }
}

dependencies {
    compile 'org.openbaton:plugin-sdk:0.15-SNAPSHOT'
}

```

Then in your main, obtain the MonitoringPluginCaller as follow:

```java
MonitoringPluginCaller monitoringPluginCaller=null;
try {
      monitoringPluginCaller = new MonitoringPluginCaller("zabbix");
} catch (TimeoutException e) {
    e.printStackTrace();
} catch (NotFoundException e) {
    e.printStackTrace();
}
```

## Description of methods
### VirtualisedResourcePerformanceManagement interface
#### Create PM Job

```java
String createPMJob(ObjectSelection selector, List<String> performanceMetrics, List<String> performanceMetricGroup, Integer collectionPeriod,Integer reportingPeriod) throws MonitoringException;
```
This method create one or more items to be monitored in one or more hosts.

**selector**: object to select the hosts in which we want to add the items.

**performanceMetrics**: List of items. We can create items which are available in the [Zabbix documentation 2.2][zabbix-doc-2.2].

**performanceMetricGroup**: pre-defined list of metrics. (NOT YET IMPLEMENTED, please pass an empty list of string).

**collectionPeriod**: Update interval of the item/s in seconds.

**reportingPeriod**: Specifies the periodicity at which the VIM will report to the customers about performance information  . (NOT YET IMPLEMENTED, please pass an integer >= 0 ).

In the following example we create two items ('net.tcp.listen[8080]' and 'agent.ping') for two hosts ('host-1' and 'host-2'). As a return value we get the ID of the PMJob.
```java
ObjectSelection objectSelection = getObjectSelector("host-1","host-2");
List<String> performanceMetrics = getPerformanceMetrics("net.tcp.listen[8080]","agent.ping");
String pmJobId = zabbixMonitoringAgent.createPMJob(objectSelection, performanceMetrics, new ArrayList<String>(),60, 0);
```

#### Delete Pm Job
```java
List<String> deletePMJob(List<String> pmJobIdsToDelete) throws MonitoringException;
```
This method delete an existing PmJob. We can get the id of the PmJob after the creation with createPMJob. As a return value we get the ID of the PMJobs effectively deleted.

### Query Pm Job

```java
List<Item> queryPMJob(List<String> hostnames, List<String> performanceMetrics, String period) throws MonitoringException;
```
This method get item values from one or more host. As a return value we get the list of items.

**hostnames**: list of hostnames which we want to know items values.

**performanceMetrics**: List of items. We can get items which are available in: the [Zabbix documentation 2.2][zabbix-doc-2.2] and in the **hostnames**.

**period**: period in seconds. If period is 0 than you get the last available value of the item. If > 0 you get the average of the values inside that period.
    Remember than the zabbix-plugin read all value of the all hosts every **client-request-frequency** (see the configuration section) and keep them in the history.
    So if **client-request-frequency** is 15 seconds and the period 30 seconds you get the average of the previous 2 values of the item.

In the following example we ask for the last value of two items ('net.tcp.listen[8080]' and 'agent.ping') for the hosts 'host-1'.

```java
ArrayList<String> hostnames = getHostnames("host-1");
ArrayList<String> performanceMetrics = getPerformanceMetrics("net.tcp.listen[8080]","agent.ping");
List<Item> items = monitoringPluginCaller.queryPMJob(hostnames,performanceMetrics,"0");
```

**items**: list of items. An item is a simple object of openbaton-libs which contains properties like: metric, hostname, lastValue, value.

### Subscribe & notifyInfo

NOT YET IMPLEMENTED

### Create Threshold

```java
String createThreshold(ObjectSelection selector, String performanceMetric, ThresholdType thresholdType, ThresholdDetails thresholdDetails) throws MonitoringException;
```
This method create a trigger on a specific item for one or more hosts. As a return value we get the id of the threshold.

**selector**: object to select the hosts which will be part of the trigger.

**performanceMetric**: item to include in the trigger. The item need to be already present in the hosts specified in the *selector*.

**thresholdType**: defines the type of threshold . (NOT YET IMPLEMENTED, please pass ThresholdType.SINGLE for the moment).

**thresholdDetails**: details of the threshold. It contains:

- function: refer to [Zabbix trigger function 2.2][zabbix-trigger-function-2.2]  
- value: threshold value to compare with the actual value of the *performanceMetric*.
- triggerOperator: operator
- perceiverSeverity: severity of the trigger.

In the following example we create a trigger for two hosts ('host-1' and 'host-2').

```java
ObjectSelection objectSelector = getObjectSelector("host-1","host-2");
ThresholdDetails thresholdDetails= new ThresholdDetails("last(0)","0","=");
thresholdDetails.setPerceivedSeverity(PerceivedSeverity.CRITICAL);
String thresholdId = zabbixMonitoringAgent.createThreshold(objectSelector,"net.tcp.listen[5001]",null,thresholdDetails);
```
The trigger that will be created has this expression: {host-1:net.tcp.listen[5001].last(0)}=0|{host-2:net.tcp.listen[5001].last(0)}=0.
It means that if host-1 OR host-2 have no more process listening on the port 5001 then create an alarm with severity critical.
Refer to [Zabbix expression 2.2][zabbix-trigger-expression-2.2] to understand better the expression.

**Note**: Actually is available only the OR operator. The AND will be introduced later.

### Delete Threshold
```java
List<String> deleteThreshold(List<String> thresholdIds) throws MonitoringException;
```
This method delete an existing threshold/s. We can get the id of the threshold after the creation with createThreshold. As a return value we get the list of the ID of the threshold effectively deleted.

### Query Threshold

NOT YET IMPLEMENTED

## VirtualisedResourceFaultManagement interface

### Subscribe
```java
String subscribeForFault(AlarmEndpoint filter) throws MonitoringException;
```
Subscribe for alarm generated by thresholds. As a return value we get the id of the subscription.
**filter**: AlarmEndpoint object which contains:
-  name: name of the alarmEndpoint.
-  resourceId: hostname which we want to subscribe.
-  type: REST or JMS.
-  endpoint: endpoint where we want to be notified. It is and url for REST or a queue name for JMS. (actually only rest is supported).
-  perceivedSeverity: define the severity of the alarm we want to get.
    If we specify PerceivedSeverity.WARNING we will able to get notification from alarm with severity equals or higher than WARNING.

In the following example we subscribe for all alarms with severity higher than WARNING coming from the host 'host-1'.

```java
AlarmEndpoint alarmEndpoint = new AlarmEndpoint("fault-manager-of-host-1","host-1",EndpointType.REST,"http://localhost:5555/alarm",PerceivedSeverity.WARNING);
String subscriptionId = monitoringPluginCaller.subscribeForFault(alarmEndpoint);
```
### Unsubscribe

```java
String unsubscribeForFault(String subscriptionId) throws MonitoringException;
```
This method detele the subscription with the ID passed as a argument. As a return value we get the id of the subscription effectively deleted.
We can get the id of the subscription after the creation with subscribeForFault.

### Get alarm list

NOT YET IMPLEMENTED

### Notify

This method cannot be invoked by the customers as specified in the ETSI draft [IFA005_Or-Vi_ref_point_Spec].

## Zabbix severity mapping with ETSI severity

In Zabbix we can specify a severity of a trigger: not classified, information, warning, average, high, disaster.

In the ETSI draft there are different level of severity, called perceived severity: Indeterminate, warning, minor, major, critical.

So the mapping in the zabbix plugin is showed in the following table:

| Zabbix severity       | ETSI perceived severity
| --------------------- | --------------
| Not classified        | Indeterminate
| Information           | Warning
| Warning               | Warning
| Average               | Minor
| High                  | Major
| Disaster              | Critical


[IFA005_Or-Vi_ref_point_Spec]:https://docbox.etsi.org/isg/nfv/open/Drafts/IFA005_Or-Vi_ref_point_Spec/
[NFV MANO]:http://www.etsi.org/deliver/etsi_gs/NFV-MAN/001_099/001/01.01.01_60/gs_nfv-man001v010101p.pdf
[zabbix-plugin-architecture]:img/zabbix-plugin-architecture.png
[zabbix-doc-2.2]:https://www.zabbix.com/documentation/2.2/manual/config/items/itemtypes/zabbix_agent
[zabbix-trigger-function-2.2]:https://www.zabbix.com/documentation/2.2/manual/appendix/triggers/functions
[zabbix-trigger-expression-2.2]:https://www.zabbix.com/documentation/2.2/manual/config/triggers/expression
[openbaton-website]:http://openbaton.github.io