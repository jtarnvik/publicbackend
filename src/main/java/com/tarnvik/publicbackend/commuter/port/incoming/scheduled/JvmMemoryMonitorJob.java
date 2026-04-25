package com.tarnvik.publicbackend.commuter.port.incoming.scheduled;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;

@Component
@Profile("!test")
@Slf4j
public class JvmMemoryMonitorJob {
  @Scheduled(fixedRate = 600_000)
  public void logMemoryUsage() {
    MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
    long usedMb = heap.getUsed() / 1_048_576;
    long maxMb = heap.getMax() / 1_048_576;
    int pct = maxMb > 0 ? (int) (100 * heap.getUsed() / heap.getMax()) : -1;

    StringBuilder msg = new StringBuilder();
    msg.append(String.format("JVM memory — heap: %dMB / %dMB (%d%%)", usedMb, maxMb, pct));

    for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
      if (pool.getName().contains("Old") || pool.getName().contains("Tenured")) {
        MemoryUsage usage = pool.getUsage();
        if (usage != null && usage.getMax() > 0) {
          msg.append(String.format(" | old-gen: %dMB / %dMB", usage.getUsed() / 1_048_576, usage.getMax() / 1_048_576));
        }
      }
    }

    log.info(msg.toString());
  }
}
// Empty with error state
// JVM memory — heap: 57MB / 371MB (15%) | old-gen: 42MB / 256MB
