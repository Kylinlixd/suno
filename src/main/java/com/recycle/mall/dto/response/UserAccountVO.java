
package com.recycle.mall.dto.response;

import java.time.LocalDateTime;

/**
 * 用户信息响应
 */
public class UserAccountVO {

    private Long id;
    private String username;
    private String roleCode;
    private String accountStatus;
    private String level;
    private Integer points;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getRoleCode() { return roleCode; }
    public void setRoleCode(String roleCode) { this.roleCode = roleCode; }
    public String getAccountStatus() { return accountStatus; }
    public void setAccountStatus(String accountStatus) { this.accountStatus = accountStatus; }
    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }
    public Integer getPoints() { return points; }
    public void setPoints(Integer points) { this.points = points; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
