/*
 * Copyright (c) 2021 VMware, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.vmware.taurus.datajobs;

import com.vmware.taurus.service.KubernetesService;
import com.vmware.taurus.service.kubernetes.ControlKubernetesService;
import com.vmware.taurus.service.kubernetes.DataJobsKubernetesService;
import com.vmware.taurus.service.model.JobDeployment;
import io.kubernetes.client.ApiException;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.AdditionalAnswers.answer;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Registers a bean with a partially functional mock implementation of a {@link KubernetesService}
 */
@Profile("MockKubernetes")
@Configuration
public class MockKubernetes {

   @Bean
   @Primary
   public DataJobsKubernetesService mockDataJobsKubernetesService() throws ApiException, IOException, InterruptedException {
      DataJobsKubernetesService mock = mock(DataJobsKubernetesService.class);
      mockKubernetesService(mock);
      return mock;
   }

   @Bean
   @Primary
   public ControlKubernetesService mockControlKubernetesService() throws ApiException, IOException, InterruptedException {
      ControlKubernetesService mock = mock(ControlKubernetesService.class);
      mockKubernetesService(mock);
      return mock;
   }

   @Bean
   @Primary
   public TaskExecutor taskExecutor() {
      // Deployment methods are non-blocking (Async) which makes them harder to test.
      // Making them sync for the purposes of this test.
      return new SyncTaskExecutor();
   }

   /**
    * Mocks interactions with KubernetesService as much as necessary for unit testing purpose.
    *
    * NOTES:
    * If job name starts with 'failure-' (e.g failure-my-job) - then Job status will be fail otherwise it's success.
    */
   private void mockKubernetesService(KubernetesService mock) throws ApiException, IOException, InterruptedException {
      // By defautl beans are singleton scoped so we are sure this will be called once
      // hence it's safe to keep the variables here isntead of static.
      final Map<String, Map<String, byte[]>> secrets = new ConcurrentHashMap<>();
      final Map<String, InvocationOnMock> crons = new ConcurrentHashMap<>();
      final Map<String, InvocationOnMock> jobs = new ConcurrentHashMap<>();

      when(mock.getSecretData(any())).thenAnswer(inv -> secrets.getOrDefault(inv.getArgument(0), Collections.emptyMap()));
      doAnswer(answer(secrets::put)).when(mock).saveSecretData(any(), any());
      doAnswer(inv -> secrets.remove(inv.getArgument(0))).when(mock).removeSecretData(any());


      doAnswer(inv -> crons.put(inv.getArgument(0), inv))
              .when(mock).createCronJob(anyString(), anyString(), any(), anyString(), anyBoolean(), any(), any(), any(), any(), any(), any(), any());
      doAnswer(inv -> crons.put(inv.getArgument(0), inv))
              .when(mock).updateCronJob(anyString(), anyString(), any(), anyString(), anyBoolean(), any(), any(), any(), any(), any(), any(), any());
      doAnswer(inv -> crons.keySet()).when(mock).listCronJobs();
      doAnswer(inv -> crons.remove(inv.getArgument(0))).when(mock).deleteCronJob(anyString());
      doAnswer(inv -> {
         JobDeployment deployment = null;
         if (crons.containsKey(inv.getArgument(0))) {
            deployment = new JobDeployment();
            deployment.setMode("release");
            deployment.setCronJobName(inv.getArgument(0));
            deployment.setImageName("image-name");
            deployment.setGitCommitSha("foo");
         }
         return Optional.ofNullable(deployment);
      }).when(mock).readCronJob(anyString());


      doAnswer(inv -> jobs.put(inv.getArgument(0), inv)).when(mock)
              .createJob(anyString(), anyString(), anyBoolean(), any(), any(), any(), any(), anyString(), any(), any());
      doAnswer(inv -> jobs.keySet()).when(mock).listCronJobs();
      doAnswer(inv -> jobs.remove(inv.getArgument(0))).when(mock).deleteJob(anyString());
      doAnswer(inv -> {
         KubernetesService.JobDetails jobDetails = null;
         String jobName = inv.getArgument(0);
         if (jobs.containsKey(jobName)) {
            jobDetails = new KubernetesService.JobDetails(
                    inv.getArgument(0),
                    new KubernetesService.JobStatus(0, jobName.startsWith("failure-") ? 1 : 0,
                            jobName.startsWith("failure-") ? 0: 1,0,1),
                    Collections.emptyMap(), Collections.emptyMap()
            );
         }
         return jobDetails;
      }).when(mock).readJob(anyString());

      doAnswer(inv ->{
         String jobName = inv.getArgument(0);
         if (jobs.containsKey(jobName)) {
            if (jobName.startsWith("failure-")) {
               return new KubernetesService.JobStatusCondition(false, "Status", "Job name starts with 'failure-'", "", 0);
            } else {
               return new KubernetesService.JobStatusCondition(true, "Status", "", "", 0);
            }
         }
         return new KubernetesService.JobStatusCondition(false, null, "No such job", "", 0);
      }).when(mock).watchJob(anyString(), anyInt(), any());

   }
}