#!/usr/bin/env bash
./gradlew clean assemble
/Users/longpham/Workspace/ForwardSearchInstallationGuide/elasticsearch-5.6.1/bin/elasticsearch-plugin remove elasticsearch-esdocumentsearch
/Users/longpham/Workspace/ForwardSearchInstallationGuide/elasticsearch-5.6.1/bin/elasticsearch-plugin install file:///Users/longpham/Workspace/ForwardSearchInstallationGuide/Entity-Elastic-Search-API-Extension-Plugin/build/distributions/elasticsearch-esdocumentsearch-5.6.1.zip
