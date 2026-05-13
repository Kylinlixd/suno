
package com.suno.mall.dto.converter;

import com.suno.mall.dto.response.*;
import com.suno.mall.entity.*;

import java.util.List;

/**
 * Entity → VO 转换器
 */
public final class VOConverter {

    private VOConverter() {}

    public static UserAccountVO toVO(UserAccountEntity entity) {
        if (entity == null) return null;
        UserAccountVO vo = new UserAccountVO();
        vo.setId(entity.getId());
        vo.setUsername(entity.getUsername());
        vo.setRoleCode(entity.getRoleCode());
        vo.setAccountStatus(entity.getAccountStatus());
        vo.setLevel(entity.getLevel());
        vo.setPoints(entity.getPoints());
        return vo;
    }

    public static ProductVO toVO(ProductEntity entity) {
        if (entity == null) return null;
        ProductVO vo = new ProductVO();
        vo.setId(entity.getId());
        vo.setSnCode(entity.getSnCode());
        vo.setBrand(entity.getBrand());
        vo.setModel(entity.getModel());
        vo.setProductionDate(entity.getProductionDate());
        vo.setImageUrl(entity.getImageUrl());
        vo.setWearScore(entity.getWearScore());
        vo.setRecycleGrade(entity.getRecycleGrade());
        vo.setEstimatedRecyclePrice(entity.getEstimatedRecyclePrice());
        return vo;
    }

    public static RecycleOrderVO toVO(RecycleOrderEntity entity) {
        if (entity == null) return null;
        RecycleOrderVO vo = new RecycleOrderVO();
        vo.setId(entity.getId());
        vo.setOrderNo(entity.getOrderNo());
        vo.setUserId(entity.getUser() != null ? entity.getUser().getId() : null);
        vo.setUsername(entity.getUser() != null ? entity.getUser().getUsername() : null);
        vo.setProductId(entity.getProduct() != null ? entity.getProduct().getId() : null);
        vo.setSnCode(entity.getProduct() != null ? entity.getProduct().getSnCode() : null);
        vo.setEstimatedPrice(entity.getEstimatedPrice());
        vo.setGrade(entity.getGrade());
        vo.setStatus(entity.getStatus());
        vo.setCreatedAt(entity.getCreatedAt());
        return vo;
    }

    public static ResaleListingVO toVO(ResaleListingEntity entity) {
        if (entity == null) return null;
        ResaleListingVO vo = new ResaleListingVO();
        vo.setListingId(entity.getId());
        vo.setRecycleOrderId(entity.getRecycleOrder() != null ? entity.getRecycleOrder().getId() : null);
        vo.setBrand(entity.getProduct() != null ? entity.getProduct().getBrand() : null);
        vo.setModel(entity.getProduct() != null ? entity.getProduct().getModel() : null);
        vo.setGrade(entity.getProduct() != null ? entity.getProduct().getRecycleGrade() : null);
        vo.setSalePrice(entity.getSalePrice());
        vo.setStock(entity.getStock());
        vo.setStatus(entity.getStatus());
        vo.setCreatedAt(entity.getCreatedAt());
        return vo;
    }

    public static ResaleListingVO toVO(ResaleListingEntity entity, long favoriteCount) {
        ResaleListingVO vo = toVO(entity);
        if (vo != null) {
            vo.setFavoriteCount(favoriteCount);
        }
        return vo;
    }

    public static ResaleOrderVO toVO(ResaleOrderEntity entity) {
        if (entity == null) return null;
        ResaleOrderVO vo = new ResaleOrderVO();
        vo.setId(entity.getId());
        vo.setOrderNo(entity.getOrderNo());
        vo.setBuyerUserId(entity.getBuyerUser() != null ? entity.getBuyerUser().getId() : null);
        vo.setBuyerUsername(entity.getBuyerUser() != null ? entity.getBuyerUser().getUsername() : null);
        vo.setListingId(entity.getListing() != null ? entity.getListing().getId() : null);
        vo.setAmount(entity.getAmount());
        vo.setPayStatus(entity.getPayStatus());
        vo.setFulfillStatus(entity.getFulfillStatus());
        vo.setCreatedAt(entity.getCreatedAt());
        return vo;
    }

    public static ResaleReviewVO toVO(ResaleReviewEntity entity) {
        if (entity == null) return null;
        ResaleReviewVO vo = new ResaleReviewVO();
        vo.setId(entity.getId());
        vo.setOrderId(entity.getOrder() != null ? entity.getOrder().getId() : null);
        vo.setListingId(entity.getListing() != null ? entity.getListing().getId() : null);
        vo.setUserId(entity.getUser() != null ? entity.getUser().getId() : null);
        vo.setUsername(entity.getUser() != null ? entity.getUser().getUsername() : null);
        vo.setRating(entity.getRating());
        vo.setContent(entity.getContent());
        vo.setImageUrls(entity.getImageUrls());
        vo.setAppendContent(entity.getAppendContent());
        vo.setMerchantReply(entity.getMerchantReply());
        vo.setSensitiveHit(entity.getSensitiveHit());
        vo.setModerationStatus(entity.getModerationStatus());
        vo.setModeratedAt(entity.getModeratedAt());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setAppendedAt(entity.getAppendedAt());
        vo.setRepliedAt(entity.getRepliedAt());
        return vo;
    }

    public static LogisticsTrackVO toVO(LogisticsTrackEntity entity) {
        if (entity == null) return null;
        LogisticsTrackVO vo = new LogisticsTrackVO();
        vo.setId(entity.getId());
        vo.setTrackingNo(entity.getTrackingNo());
        vo.setOrderId(entity.getOrder() != null ? entity.getOrder().getId() : null);
        vo.setStatus(entity.getStatus());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }

    public static PointsLedgerVO toVO(PointsLedgerEntity entity) {
        if (entity == null) return null;
        PointsLedgerVO vo = new PointsLedgerVO();
        vo.setId(entity.getId());
        vo.setUserId(entity.getUser() != null ? entity.getUser().getId() : null);
        vo.setPointsDelta(entity.getPointsDelta());
        vo.setReason(entity.getReason());
        vo.setCreatedAt(entity.getCreatedAt());
        return vo;
    }

    public static AuditLogVO toVO(OperationAuditLogEntity entity) {
        if (entity == null) return null;
        AuditLogVO vo = new AuditLogVO();
        vo.setId(entity.getId());
        vo.setActionType(entity.getActionType());
        vo.setTargetType(entity.getTargetType());
        vo.setTargetId(entity.getTargetId());
        vo.setDetail(entity.getDetail());
        vo.setCreatedAt(entity.getCreatedAt());
        return vo;
    }

    public static <T> List<T> convertList(List<?> entities, java.util.function.Function<Object, T> converter) {
        return entities.stream().map(converter).toList();
    }
}
