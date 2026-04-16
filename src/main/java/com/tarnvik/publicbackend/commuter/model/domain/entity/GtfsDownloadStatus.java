package com.tarnvik.publicbackend.commuter.model.domain.entity;

public enum GtfsDownloadStatus {
  DOWNLOAD_START,
  DOWNLOAD_DONE,
  UNZIP_START,
  UNZIP_DONE,
  PARSE_START,
  PARSE_DONE,
  FAILED
}
