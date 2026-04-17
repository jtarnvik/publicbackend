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
  public void markDownloadDone(GtfsDownloadLog entry) {
    entry.setStatus(GtfsDownloadStatus.DOWNLOAD_DONE);
    entry.setDownloadEndTime(LocalDateTime.now());
    downloadLogRepository.save(entry);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markUnzipStart(GtfsDownloadLog entry) {
    entry.setStatus(GtfsDownloadStatus.UNZIP_START);
    entry.setUnzipStartTime(LocalDateTime.now());
    downloadLogRepository.save(entry);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markUnzipDone(GtfsDownloadLog entry) {
    entry.setStatus(GtfsDownloadStatus.UNZIP_DONE);
    entry.setUnzipEndTime(LocalDateTime.now());
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

  public Optional<GtfsDownloadLog> findMostRecent() {
    return downloadLogRepository.findTopByOrderByDateDesc();
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void resetToDownloadDone(GtfsDownloadLog entry) {
    entry.setStatus(GtfsDownloadStatus.DOWNLOAD_DONE);
    entry.setUnzipStartTime(null);
    entry.setUnzipEndTime(null);
    entry.setParseStartTime(null);
    entry.setParseEndTime(null);
    entry.setErrorMessage(null);
    downloadLogRepository.save(entry);
  }

  public long countByDateAfter(LocalDate date) {
    return downloadLogRepository.countByDateAfter(date);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void deleteByDateBefore(LocalDate date) {
    downloadLogRepository.deleteByDateBefore(date);
  }
}
