package com.tarnvik.publicbackend.commuter.model.domain.dao;

import com.tarnvik.publicbackend.commuter.model.domain.entity.GtfsDownloadLog;
import com.tarnvik.publicbackend.commuter.model.domain.entity.GtfsDownloadStatus;
import com.tarnvik.publicbackend.commuter.model.domain.repository.GtfsDownloadLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class GtfsDownloadDao {
  private final GtfsDownloadLogRepository downloadLogRepository;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public GtfsDownloadLog insertDownloadStart(LocalDate date) {
    GtfsDownloadLog entry = new GtfsDownloadLog();
    entry.setDate(date);
    entry.setStatus(GtfsDownloadStatus.DOWNLOAD_START);
    entry.setDownloadStartTime(LocalDateTime.now());
    return downloadLogRepository.save(entry);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void updateStatus(GtfsDownloadLog entry, GtfsDownloadStatus status) {
    entry.setStatus(status);
    downloadLogRepository.save(entry);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markUnzipStart(GtfsDownloadLog entry) {
    entry.setStatus(GtfsDownloadStatus.UNZIP_START);
    entry.setUnzipStartTime(LocalDateTime.now());
    downloadLogRepository.save(entry);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void updateFailed(GtfsDownloadLog entry, String errorMessage) {
    entry.setStatus(GtfsDownloadStatus.FAILED);
    entry.setErrorMessage(errorMessage);
    downloadLogRepository.save(entry);
  }

  public Optional<GtfsDownloadLog> findByDate(LocalDate date) {
    return downloadLogRepository.findByDate(date);
  }

  public long countByDateAfter(LocalDate date) {
    return downloadLogRepository.countByDateAfter(date);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void deleteByDateBefore(LocalDate date) {
    downloadLogRepository.deleteByDateBefore(date);
  }
}
