/*
 * Copyright (c) 2021 VMware, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.vmware.taurus.service.execution;

import com.vmware.taurus.service.JobExecutionRepository;
import com.vmware.taurus.service.JobsRepository;
import com.vmware.taurus.service.model.DataJob;
import com.vmware.taurus.service.model.ExecutionStatus;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class JobExecutionCleanupService {

    @Value("${datajobs.executions.cleanupJob.maximumExecutionsToStore:100}") //default value is 100
    private int maxExecutionsToKeep;
    @Value("${datajobs.executions.cleanupJob.executionsTtlSeconds:1209600}") //default value is 14 days
    private long secondsCutoffAmount;
    private JobExecutionRepository jobExecutionRepository;
    private JobsRepository jobsRepository;

    @Autowired
    public void setJobExecutionRepository(JobExecutionRepository jobExecutionRepository) {
        this.jobExecutionRepository = jobExecutionRepository;
    }

    @Autowired
    public void setJobsRepository(JobsRepository jobsRepository) {
        this.jobsRepository = jobsRepository;
    }

    @SchedulerLock(name = "cleanupExecutionsTask")
    @Scheduled(cron = "${datajobs.executions.cleanupJob.scheduleCron:0 0 */3 * * *}") //default value is every 3 hours
    public void cleanupExecutions() {
        log.info("Starting DataJobExecutionCleanup");
        var jobs = jobsRepository.findAll();
        for (var job : jobs) {
            try {
                deleteDataJobExecutions(job);
            } catch (Exception e) {
                log.warn("Failed to delete executions for job {}", job.getName());
                log.warn("Error:", e);
            }
        }
    }

    private void deleteDataJobExecutions(DataJob job) {

        var statuses = List.of(ExecutionStatus.RUNNING, ExecutionStatus.SUBMITTED);
        var jobExecutions = jobExecutionRepository.findByDataJobNameAndStatusNotInOrderByEndTime(job.getName(), statuses);
        var jobsToDelete = new ArrayList<String>();
        var cutOff = OffsetDateTime.now().minusSeconds(secondsCutoffAmount);

        for (int i = 0; i < jobExecutions.size(); i++) { //first execution is the oldest
            var execution = jobExecutions.get(i);
            //ensure we add jobs for deletion only once
            if (shouldDeleteJobExecution(i, jobExecutions.size(), cutOff, execution.getEndTime())) {
                jobsToDelete.add(execution.getId());
            }
        }

        log.info("Found {} job executions to delete for DataJob:'{}'. Deleting if necessary.", jobsToDelete.size(), job.getName());
        jobExecutionRepository.deleteAllByIdInBatch(jobsToDelete);
    }

    /**
     * Helper method which determines if a data job execution should be deleted.
     * Assumes that executionPos is the position of a dataJobExecution in a sorted
     * execution list by start time ASC.
     *
     * @param executionPos       the position of a data job execution in a sorted list.
     * @param executionsSize     the size of the list.
     * @param cutOff             cut off date for deletion.
     * @param executionEndTime data job execution end time.
     * @return boolean describing if the data job should be deleted.
     */
    private boolean shouldDeleteJobExecution(int executionPos, int executionsSize,
                                             OffsetDateTime cutOff, OffsetDateTime executionEndTime) {
        boolean shouldDelete = false;

        if (executionPos < executionsSize - maxExecutionsToKeep) { //Delete by numbers
            shouldDelete = true;
        } else if (executionEndTime.isBefore(cutOff)) { // Delete by date
            shouldDelete = true;
        }
        return shouldDelete;
    }

}