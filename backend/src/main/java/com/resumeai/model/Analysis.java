package com.resumeai.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "analyses", indexes = {
        @Index(name = "idx_analysis_user", columnList = "userId"),
        @Index(name = "idx_analysis_hash", columnList = "userId,inputHash"),
        @Index(name = "idx_analysis_share", columnList = "shareToken")
})
public class Analysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 255)
    private String fileName;

    @Column(length = 120)
    private String jobLabel;

    @Column(nullable = false)
    private int score;

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String resultJson;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    // ---- added later: all nullable so an existing database upgrades without errors ----
    @Column(length = 30)
    private String level;

    @Column(length = 120)
    private String role;

    @Column(columnDefinition = "LONGTEXT")
    private String jobDescription;

    @Column(columnDefinition = "LONGTEXT")
    private String resumeText;

    @Column(length = 64)
    private String inputHash;

    @Column(length = 64)
    private String shareToken;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getJobLabel() { return jobLabel; }
    public void setJobLabel(String jobLabel) { this.jobLabel = jobLabel; }
    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }
    public String getResultJson() { return resultJson; }
    public void setResultJson(String resultJson) { this.resultJson = resultJson; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getJobDescription() { return jobDescription; }
    public void setJobDescription(String jobDescription) { this.jobDescription = jobDescription; }
    public String getResumeText() { return resumeText; }
    public void setResumeText(String resumeText) { this.resumeText = resumeText; }
    public String getInputHash() { return inputHash; }
    public void setInputHash(String inputHash) { this.inputHash = inputHash; }
    public String getShareToken() { return shareToken; }
    public void setShareToken(String shareToken) { this.shareToken = shareToken; }
}
