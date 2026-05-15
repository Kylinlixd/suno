
package com.suno.mall.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.TransactionManagementConfigurer;
import org.springframework.transaction.interceptor.TransactionAttribute;
import org.springframework.transaction.interceptor.TransactionAttributeSource;
import org.springframework.transaction.interceptor.DefaultTransactionAttribute;

import jakarta.annotation.Resource;

/**
 * 事务配置类
 * 优化事务管理，设置默认超时时间，明确事务传播行为
 */
@Configuration
@EnableTransactionManagement
public class TransactionConfig implements TransactionManagementConfigurer {

    @Resource
    private TransactionAttributeSource transactionAttributeSource;

    @Override
    public TransactionAttributeSource transactionAttributeSource() {
        return transactionAttributeSource;
    }

    /**
     * 创建默认事务属性，设置30秒超时
     * @return 默认事务属性
     */
    public static TransactionAttribute createDefaultTransactionAttribute() {
        DefaultTransactionAttribute attribute = new DefaultTransactionAttribute();
        attribute.setTimeout(30); // 设置事务超时时间为30秒
        attribute.setPropagationBehaviorName("PROPAGATION_REQUIRED"); // 默认传播行为为REQUIRED
        return attribute;
    }

    /**
     * 创建新事务属性，设置30秒超时
     * @return 新事务属性
     */
    public static TransactionAttribute createNewTransactionAttribute() {
        DefaultTransactionAttribute attribute = new DefaultTransactionAttribute();
        attribute.setTimeout(30); // 设置事务超时时间为30秒
        attribute.setPropagationBehaviorName("PROPAGATION_REQUIRES_NEW"); // 传播行为为REQUIRES_NEW
        return attribute;
    }
}
