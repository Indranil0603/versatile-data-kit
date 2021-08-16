/*
 * Copyright (c) 2021 VMware, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.vmware.taurus.properties.controller;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.vmware.taurus.controlplane.model.api.DataJobsPropertiesApi;
import com.vmware.taurus.properties.service.PropertiesService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@ComponentScan(basePackages = "com.vmware.taurus.properties")
public class DataJobsPropertiesController implements DataJobsPropertiesApi {
   static Logger log = LoggerFactory.getLogger(DataJobsPropertiesController.class);

   private final PropertiesService propertiesService;

   public DataJobsPropertiesController(PropertiesService propertiesService) {
      this.propertiesService = propertiesService;
   }

   @Override
   @Deprecated
   public ResponseEntity<Map<String, Object>> dataJobPropertiesReadDeprecated(final String teamName,
                                                                              final String jobName,
                                                                              final String deploymentId) {
      return dataJobPropertiesRead(teamName, jobName, deploymentId);
   }

   @Override
   @Deprecated
   public ResponseEntity<Void> dataJobPropertiesUpdateDeprecated(final String teamName,
                                                                 final String jobName,
                                                                 final String deploymentId,
                                                                 final Map<String, Object> requestBody) {
      return dataJobPropertiesUpdate(teamName, jobName, deploymentId, requestBody);
   }

   @Override
   public ResponseEntity<Void> dataJobPropertiesUpdate(String teamName, String jobName, String deploymentId, Map<String, Object> requestBody) {
      log.debug("Updating properties for job: {}", jobName);

      propertiesService.updateJobProperties(jobName, requestBody);
      return ResponseEntity.noContent().build();
   }

   @Override
   public ResponseEntity<Map<String, Object>> dataJobPropertiesRead(String teamName, String jobName, String deploymentId) {
      log.debug("Reading properties for job: {}", jobName);

      try {
         return ResponseEntity.ok(propertiesService.readJobProperties(jobName));
      } catch (JsonProcessingException e) {
         log.error("Error while parsing properties for job: " + jobName, e);

         throw new ResponseStatusException(
               HttpStatus.INTERNAL_SERVER_ERROR, "Error while parsing properties for job: " + jobName);
      }
   }

}