package com.recycle.mall.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "auth_export_task")
public class AuthExportTaskEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false, unique = true, length = 64)
    private String taskId;

    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @Column(name = "export_type", nullable = false, length = 32)
    private String exportType;

    @Column(name = "export_format", nullable = false, length = 16)
    private String exportFormat;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    @Column(name = "max_retry", nullable = false)
    private Integer maxRetry;

    @Column(name = "file_name", length = 128)
    private String fileName;

    @Column(name = "content_text")
    private String contentText;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "error_message", length = 512)
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    public Long getId() { return id; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getExportType() { return exportType; }
    public void setExportType(String exportType) { this.exportType = exportType; }
    public String getExportFormat() { return exportFormat; }
    public void setExportFormat(String exportFormat) { this.exportFormat = exportFormat; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    public Integer getMaxRetry() { return maxRetry; }
    public void setMaxRetry(Integer maxRetry) { this.maxRetry = maxRetry; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getContentText() { return contentText; }
    public void setContentText(String contentText) { this.contentText = contentText; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
}
