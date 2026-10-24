package com.suno.mall.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.TableGenerator;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "suno_user_account")
public class UserAccountEntity {

    /**
     * 基线表为手工主键（种子固定 id）。注册走 TableGenerator 发号
     * （suno_user_id_generator，H2/MySQL 通用），显式赋值 id 的插入不受影响。
     */
    @Id
    @TableGenerator(
            name = "suno_user_account_id_gen",
            table = "suno_user_id_generator",
            pkColumnName = "generator_key",
            valueColumnName = "next_val",
            pkColumnValue = "suno_user_account",
            initialValue = 10001,
            allocationSize = 50)
    @GeneratedValue(strategy = GenerationType.TABLE, generator = "suno_user_account_id_gen")
    @Nullable
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 128)
    private String passwordHash;

    @Column(name = "role_code", nullable = false, length = 32)
    private String roleCode;

    @Column(name = "account_status", nullable = false, length = 32)
    private String accountStatus;

    @Column(nullable = false, length = 32)
    private String level;

    @Column(nullable = false)
    private Integer points;

    @Nullable
    public Long getId() {
        return id;
    }

    public void setId(@Nullable Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }

    public String getAccountStatus() {
        return accountStatus;
    }

    public void setAccountStatus(String accountStatus) {
        this.accountStatus = accountStatus;
    }

    public Integer getPoints() {
        return points;
    }

    public void setPoints(Integer points) {
        this.points = points;
    }
}
