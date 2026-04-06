package com.tarnvik.publicbackend.commuter.service;

import com.tarnvik.publicbackend.commuter.model.domain.entity.StatName;
import com.tarnvik.publicbackend.commuter.model.domain.entity.Statistic;
import com.tarnvik.publicbackend.commuter.model.domain.repository.StatisticRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StatisticsService {
  private final StatisticRepository statisticRepository;

  @Transactional
  public void increment(StatName name) {
    statisticRepository.increment(name.name());
  }

  @Transactional(readOnly = true)
  public long getCount(StatName name) {
    return statisticRepository.findByName(name.name())
      .map(Statistic::getCounter)
      .orElse(0L);
  }
}
