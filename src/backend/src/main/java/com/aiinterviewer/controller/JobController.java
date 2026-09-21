package com.aiinterviewer.controller;

import com.aiinterviewer.common.ApiResponse;
import com.aiinterviewer.entity.Job;
import com.aiinterviewer.entity.JobSkill;
import com.aiinterviewer.mapper.JobMapper;
import com.aiinterviewer.mapper.JobSkillMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobMapper jobMapper;
    private final JobSkillMapper jobSkillMapper;

    @GetMapping
    public ApiResponse<List<Job>> list(@RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "20") int pageSize) {
        var wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Job>()
                .eq(Job::getStatus, 1)
                .orderByAsc(Job::getName);
        var pageResult = jobMapper.selectPage(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, pageSize), wrapper);
        return ApiResponse.ok(pageResult.getRecords());
    }

    @GetMapping("/{id}")
    public ApiResponse<JobDetailVO> detail(@PathVariable Long id) {
        Job job = jobMapper.selectById(id);
        if (job == null) {
            return ApiResponse.NotFound();
        }
        List<JobSkill> skills = jobSkillMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<JobSkill>()
                        .eq(JobSkill::getJobId, id));
        return ApiResponse.ok(new JobDetailVO(job, skills));
    }

    public static class JobDetailVO {
        private final Job job;
        private final List<JobSkill> skills;
        public JobDetailVO(Job job, List<JobSkill> skills) { this.job = job; this.skills = skills; }
        public Job getJob() { return job; }
        public List<JobSkill> getSkills() { return skills; }
    }
}
