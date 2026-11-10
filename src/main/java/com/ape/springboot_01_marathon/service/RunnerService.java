package com.ape.springboot_01_marathon.service;

import com.ape.springboot_01_marathon.common.PageResult;
import com.ape.springboot_01_marathon.pojo.Runner;
import com.ape.springboot_01_marathon.pojo.dto.RunnerQuery;

public interface RunnerService {

    Long add(Runner runner);

    boolean update(Runner runner);

    boolean delete(Long id);

    PageResult<Runner> page(RunnerQuery query);

    Runner detail(Long id);
}

